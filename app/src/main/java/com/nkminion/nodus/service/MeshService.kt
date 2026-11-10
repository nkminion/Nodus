package com.nkminion.nodus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.nkminion.nodus.crypto.CryptoManager
import com.nkminion.nodus.crypto.SecureKeyStore
import com.nkminion.nodus.dao.MessageDao
import com.nkminion.nodus.dao.NodeDao
import com.nkminion.nodus.data.PayloadType
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.PeerRepository
import com.nkminion.nodus.database.NodusDatabase
import com.nkminion.nodus.entity.NodeEntity
import java.security.KeyPair
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MeshService : Service()
{
	companion object
	{
		var instance: MeshService? = null
			private set
	}

	private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val sentIdentities = mutableSetOf<String>()
	private fun hasSentIdentityTo(endpointId: String): Boolean =
		sentIdentities.contains(endpointId)
	private fun markIdentitySent(endpointId: String)
	{
		sentIdentities.add(endpointId)
	}
	private lateinit var connectionsClient : ConnectionsClient
	private lateinit var sharedPreferences : SharedPreferences
	private lateinit var myUUID : String
	private lateinit var db : NodusDatabase
	private lateinit var nodeDao : NodeDao
	private lateinit var messageDao: MessageDao
	private lateinit var secureKeyStore: SecureKeyStore
	private val serviceID = "com.nkminion.nodus.MeshService"
	private val endpointToUUIDMap = mutableMapOf<String,String>()
	private val localKeyPairs = mutableMapOf<String, KeyPair>()
	private val activeSessionKeys = mutableMapOf<String, ByteArray>()
	private val peerPublicKeys = mutableMapOf<String, String>()
	var currentSessionDisplayName: String = "Node"

	private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback()
	{
		override fun onEndpointFound(endpointId:String, info: DiscoveredEndpointInfo)
		{
			val peerUUID = info.endpointName
			endpointToUUIDMap[endpointId] = peerUUID

			serviceScope.launch {
				val savedNode = nodeDao.getNodeById(peerUUID)
				val isVerified = savedNode?.let {
					it.trustStatus == "VerifiedSAS" || it.trustStatus == "VerifiedMutual"
				} ?: false

				val discoveredPeer = Peer(
					displayName = "Node-${peerUUID.take(5)}",
					uuid = peerUUID,
					isVerified = isVerified,
					hops = 1
				)
				PeerRepository.addOrUpdatePeer(discoveredPeer)

				if (savedNode != null)
				{
					nodeDao.updateLastSeen(peerUUID)
				}
			}

			if (myUUID < peerUUID)
			{
				connectionsClient.requestConnection(
					myUUID,
					endpointId,
					connectionLifecycleCallback
				)
			}
			Log.d("MeshService", "Endpoint found: $endpointId (${peerUUID})")
		}

		override fun onEndpointLost(endpointId: String)
		{
			Log.d("MeshService", "Endpoint lost: $endpointId")
			endpointToUUIDMap.remove(endpointId)?.let { uuid ->
				PeerRepository.removePeerByUUID(uuid)
			}
		}
	}

	private val connectionLifecycleCallback = object : ConnectionLifecycleCallback()
	{
		override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
			Log.d("MeshService", "Connection initiated from: ${info.endpointName}")
			// Temp accept for peer visibility
			 connectionsClient.acceptConnection(endpointId,payloadCallback)
		}

		override fun onConnectionResult(endpointId: String, result: ConnectionResolution)
		{
			if (result.status.isSuccess)
			{
				Log.d("MeshService", "Connected successfully to: $endpointId")
			}
			else
			{
				Log.e("MeshService", "Connection failed with status: ${result.status.statusCode}")
			}
		}

		override fun onDisconnected(endpointId: String)
		{
			Log.d("MeshService", "Disconnected from: $endpointId")
			localKeyPairs.remove(endpointId)
			sentIdentities.remove(endpointId)
			endpointToUUIDMap.remove(endpointId)?.let { uuid ->
				activeSessionKeys.remove(uuid)
				peerPublicKeys.remove(uuid)
				PeerRepository.removePeerByUUID(uuid)
				PeerRepository.clearSasVerification()
			}
		}
	}

	private val payloadCallback = object : PayloadCallback()
	{
		override fun onPayloadReceived(endpointId: String, payload: Payload)
		{
			val bytes = payload.asBytes() ?: return
			if (bytes.isEmpty())
			{
				return
			}

			val type = PayloadType.fromByte(bytes[0]) ?: return
			val body = bytes.copyOfRange(1, bytes.size)

			when (type) {
				PayloadType.KeyExchange -> {
					val peerUUID = endpointToUUIDMap[endpointId] ?: return

					var myKeyPair = localKeyPairs[endpointId]
					if (myKeyPair == null) {
						myKeyPair = CryptoManager.generateEcKeyPair()
						localKeyPairs[endpointId] = myKeyPair

						val responsePacket = byteArrayOf(PayloadType.KeyExchange.code) + myKeyPair.public.encoded
						connectionsClient.sendPayload(endpointId, Payload.fromBytes(responsePacket))
					}

					val sessionKey = CryptoManager.deriveSharedSecret(myKeyPair.private, body)
					activeSessionKeys[peerUUID] = sessionKey

					val sasCode = CryptoManager.generateSasCode(
						myPubKey = myKeyPair.public.encoded,
						peerPubKey = body,
						sharedSecret = sessionKey
					)

					peerPublicKeys[peerUUID] = android.util.Base64.encodeToString(body, android.util.Base64.NO_WRAP)

					PeerRepository.showSASVerification(peerUUID, endpointId, sasCode)

					Log.d("MeshService", "SAS generated for $peerUUID: $sasCode")
				}
				PayloadType.ChatAck -> {
					Log.d("MeshService", "Received ACK from $endpointId")
				}
				PayloadType.IdentityAnnounce -> {
					val peerUUID = endpointToUUIDMap[endpointId] ?: return
					val peerName = String(body, Charsets.UTF_8)

					PeerRepository.updatePeerDisplayName(peerUUID, peerName)

					if (!hasSentIdentityTo(endpointId)) {
						val echoPacket = byteArrayOf(PayloadType.IdentityAnnounce.code) + currentSessionDisplayName.toByteArray(Charsets.UTF_8)
						connectionsClient.sendPayload(endpointId, Payload.fromBytes(echoPacket))
						markIdentitySent(endpointId)
					}

					serviceScope.launch{
						nodeDao.insertNode(
							NodeEntity(
								peerUUID,
								peerPublicKeys[peerUUID],
								"VerifiedSAS",
								System.currentTimeMillis()
							)
						)
					}

					Log.d("MeshService", "Updated node $peerUUID display name to $peerName")
				}
				else -> Unit
			}
		}

		override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate)
		{
			// Track transfer progress
		}
	}

	fun getOrCreateUUID() : String
	{
		val existingUUID = sharedPreferences.getString("NodusUUID",null)

		if (existingUUID == null)
		{
			val newUUID = UUID.randomUUID().toString()
			sharedPreferences.edit { putString("NodusUUID", newUUID) }
			return newUUID
		}
		return existingUUID
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onCreate() {
		super.onCreate()
		instance = this

		// Notification Channel Setup
		val notifChannel = NotificationChannel(
			"NodusMeshChannelID",
			"NodusMeshChannel",
			NotificationManager.IMPORTANCE_LOW
		)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(notifChannel)

		// Nearby Connections Setup
		connectionsClient = Nearby.getConnectionsClient(this)

		// Shared Preferences
		sharedPreferences = getSharedPreferences("NodusPreferences", MODE_PRIVATE)

		// Fetch UUID
		myUUID = getOrCreateUUID()

		db = NodusDatabase.getDBInstance(this)

		nodeDao = db.nodeDao()
		messageDao = db.messageDao()

		// Keys
		secureKeyStore = SecureKeyStore(this)
	}

	override fun onDestroy() {
		super.onDestroy()
		serviceScope.cancel()
		instance = null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
	{
		val stopIntent = Intent(this, MeshService::class.java).apply {
			action = "ACTION_STOP_SERVICE"
		}
		if (intent?.action == "ACTION_STOP_SERVICE")
		{
			stopForeground(STOP_FOREGROUND_REMOVE)
			stopSelf()
			return START_NOT_STICKY
		}
		val pendingIntent = PendingIntent.getService(
			this,
			0,
			stopIntent,
			PendingIntent.FLAG_IMMUTABLE
		)
		val notifBuilder = NotificationCompat.Builder(this,"NodusMeshChannelID")
			.setSmallIcon(android.R.drawable.ic_notification_overlay)
			.setContentTitle("Nodus is running")
			.setContentText("Connected to nodes")
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setAutoCancel(false)
			.addAction(
				0,
				"Quit App",
				pendingIntent
			)

		ServiceCompat.startForeground(
			this,
			100,
			notifBuilder.build(),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
		)

		startMesh()

		return START_STICKY
	}

	fun startMesh()
	{
		Log.d("MeshService", "Starting mesh advertising and discovery...")

		val advertisingOptions = AdvertisingOptions.Builder()
			.setStrategy(Strategy.P2P_CLUSTER)
			.build()

		val discoveryOptions = DiscoveryOptions.Builder()
			.setStrategy(Strategy.P2P_CLUSTER)
			.build()

		connectionsClient.startAdvertising(
			myUUID,
			this.serviceID,
			connectionLifecycleCallback,
			advertisingOptions
		)
		.addOnSuccessListener { Log.d("MeshService", "Advertising started successfully") }
		.addOnFailureListener { e -> Log.e("MeshService", "Advertising failed: ${e.message}") }

		connectionsClient.startDiscovery(
			this.serviceID,
			endpointDiscoveryCallback,
			discoveryOptions
		)
		.addOnSuccessListener { Log.d("MeshService", "Discovery started successfully") }
		.addOnFailureListener { e -> Log.e("MeshService", "Discovery failed: ${e.message}") }
	}

	fun startKeyExchange(endpointId: String)
	{
		val keyPair = CryptoManager.generateEcKeyPair()
		localKeyPairs[endpointId] = keyPair

		val pubKeyBytes = keyPair.public.encoded
		val packet = byteArrayOf(PayloadType.KeyExchange.code) + pubKeyBytes
		connectionsClient.sendPayload(endpointId, Payload.fromBytes(packet))
	}

	fun confirmSasVerification(peerUuid: String)
	{
		val sessionKey = activeSessionKeys[peerUuid]
		if (sessionKey != null)
		{
			secureKeyStore.storeSessionKey(peerUuid, sessionKey)

			val endpointId = endpointToUUIDMap.entries.firstOrNull { it.value == peerUuid }?.key
			if (endpointId != null)
			{
				val nameBytes = currentSessionDisplayName.toByteArray(Charsets.UTF_8)
				val packet = byteArrayOf(PayloadType.IdentityAnnounce.code) + nameBytes
				connectionsClient.sendPayload(endpointId, Payload.fromBytes(packet))
				markIdentitySent(endpointId)
			}

			PeerRepository.setPeerVerified(peerUuid, true)
		}
		PeerRepository.clearSasVerification()
	}

	fun rejectSasVerification(peerUuid: String)
	{
		activeSessionKeys.remove(peerUuid)
		PeerRepository.clearSasVerification()
	}

	fun startKeyExchangeForPeer(peerUuid: String)
	{
		val endpointId = endpointToUUIDMap.entries.firstOrNull { it.value == peerUuid }?.key
		if (endpointId != null)
		{
			startKeyExchange(endpointId)
		} else
		{
			Log.e("MeshService", "No active endpoint found for peer UUID: $peerUuid")
		}
	}
}