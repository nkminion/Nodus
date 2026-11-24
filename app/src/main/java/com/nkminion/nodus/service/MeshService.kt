package com.nkminion.nodus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Base64
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
import com.nkminion.nodus.MainActivity
import com.nkminion.nodus.crypto.CryptoManager
import com.nkminion.nodus.crypto.SecureKeyStore
import com.nkminion.nodus.dao.MessageDao
import com.nkminion.nodus.dao.NodeDao
import com.nkminion.nodus.data.PayloadType
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.PeerRepository
import com.nkminion.nodus.database.NodusDatabase
import com.nkminion.nodus.entity.MessageEntity
import com.nkminion.nodus.entity.NodeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class MeshService : Service()
{
	companion object
	{
		var instance: MeshService? = null
			private set

		private const val MAX_TTL: Byte = 4
		private const val MAX_SEEN_IDS = 1000
		private const val RATE_LIMIT_MS = 500L
		private const val BEACON_INTERVAL_MS = 15000L
		private const val STALE_PEER_TIMEOUT_MS = 40000L
	}
	private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var beaconJob: Job? = null
	private val secureRandom = SecureRandom()
	private val sentIdentities = ConcurrentHashMap.newKeySet<String>()
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
	private val endpointToUUIDMap = ConcurrentHashMap<String,String>()
	private val pendingConnections = ConcurrentHashMap.newKeySet<String>()
	private val peerLastSeen = ConcurrentHashMap<String, Long>()
	private val localKeyPairs = ConcurrentHashMap<String, KeyPair>()
	private val multiHopLocalKeyPairs = ConcurrentHashMap<String, KeyPair>()
	private val activeSessionKeys = ConcurrentHashMap<String, ByteArray>()
	private val peerEcdhPublicKeys = ConcurrentHashMap<String, ByteArray>()
	private val pendingMutualVerifications = ConcurrentHashMap<String, Job>()
	private val confirmedSasPeers = ConcurrentHashMap.newKeySet<String>()
	private val pendingPeerIdentities = ConcurrentHashMap<String, Pair<String, String>>()

	@Volatile
	private var verifiedNodes: Set<String> = emptySet()

	@Volatile
	private var isForegroundStarted: Boolean = false

	@Volatile
	private var isMeshStarted: Boolean = false

	private val seenPacketIds = object : LinkedHashMap<Long, Boolean>(MAX_SEEN_IDS, 0.75f,true)
	{
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?): Boolean
		{
			return size > MAX_SEEN_IDS
		}
	}

	private val seenMessageIds = object : LinkedHashMap<String, Boolean>(MAX_SEEN_IDS, 0.75f, true)
	{
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean
		{
			return size > MAX_SEEN_IDS
		}
	}

	private val lastInboundFloodTime = ConcurrentHashMap<String, Long>()
	private val lastOutboundFloodTime = AtomicLong(0L)

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

				val existingName = savedNode?.displayName?: PeerRepository.peers.value.firstOrNull { it.uuid == peerUUID }?.displayName

				val discoveredPeer = Peer(
					displayName = existingName ?: "Node-${peerUUID.take(5)}",
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

			if (myUUID < peerUUID && pendingConnections.add(endpointId))
			{
				connectionsClient.requestConnection(
					myUUID,
					endpointId,
					connectionLifecycleCallback
				).addOnFailureListener { e ->
					pendingConnections.remove(endpointId)
					Log.e("MeshService", "requestConnection failed for $endpointId: ${e.message}")
				}
			}
			Log.d("MeshService", "Endpoint found: $endpointId (${peerUUID})")
		}

		override fun onEndpointLost(endpointId: String)
		{
			Log.d("MeshService", "Endpoint lost: $endpointId")
			pendingConnections.remove(endpointId)
			endpointToUUIDMap.remove(endpointId)?.let { uuid ->
				peerLastSeen.remove(uuid)
				PeerRepository.removePeerByUUID(uuid)
			}
		}
	}

	private val connectionLifecycleCallback = object : ConnectionLifecycleCallback()
	{
		override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
			val remoteUuid = info.endpointName
			val isValidUuid = runCatching { UUID.fromString(remoteUuid) }.isSuccess
			val hasDuplicateEndpoint = endpointToUUIDMap.any { (ep, uuid) -> ep != endpointId && uuid == remoteUuid }
			if (!isValidUuid || remoteUuid == myUUID || hasDuplicateEndpoint)
			{
				Log.w("MeshService", "Rejected invalid or duplicate connection from $remoteUuid ($endpointId)")
				connectionsClient.rejectConnection(endpointId)
				return
			}
			Log.d("MeshService", "Connection initiated from: $remoteUuid")
			connectionsClient.acceptConnection(endpointId, payloadCallback)
		}

		override fun onConnectionResult(endpointId: String, result: ConnectionResolution)
		{
			pendingConnections.remove(endpointId)
			if (result.status.isSuccess)
			{
				Log.d("MeshService", "Connected successfully to: $endpointId")
			}
			else
			{
				endpointToUUIDMap.remove(endpointId)
				Log.e("MeshService", "Connection failed with status: ${result.status.statusCode}")
			}
		}

		override fun onDisconnected(endpointId: String)
		{
			Log.d("MeshService", "Disconnected from: $endpointId")
			pendingConnections.remove(endpointId)
			localKeyPairs.remove(endpointId)
			sentIdentities.remove(endpointId)
			lastInboundFloodTime.remove(endpointId)
			endpointToUUIDMap.remove(endpointId)?.let { uuid ->
				peerLastSeen.remove(uuid)
				activeSessionKeys.remove(uuid)
				peerEcdhPublicKeys.remove(uuid)
				confirmedSasPeers.remove(uuid)
				pendingPeerIdentities.remove(uuid)
				PeerRepository.removePeerByUUID(uuid)
				if (PeerRepository.activeSasVerification.value?.peerUuid == uuid)
				{
					PeerRepository.clearSasVerification()
				}
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
					if (body.size < 2) return
					val isReply = body[0] == 1.toByte()
					val peerPubBytes = body.copyOfRange(1, body.size)

					val peerUUID = endpointToUUIDMap[endpointId] ?: return
					confirmedSasPeers.remove(peerUUID)
					sentIdentities.remove(endpointId)

					val myKeyPair = if (!isReply) {
						val freshPair = CryptoManager.generateEcKeyPair()
						localKeyPairs[endpointId] = freshPair
						val responsePacket = byteArrayOf(PayloadType.KeyExchange.code, 1) + freshPair.public.encoded
						connectionsClient.sendPayload(endpointId, Payload.fromBytes(responsePacket))
						freshPair
					}
					else
					{
						localKeyPairs[endpointId] ?: return
					}

					val sessionKey = CryptoManager.deriveSharedSecret(myKeyPair.private, peerPubBytes)
					activeSessionKeys[peerUUID] = sessionKey

					val sasCode = CryptoManager.generateSasCode(
						myPubKey = myKeyPair.public.encoded,
						peerPubKey = peerPubBytes,
						sharedSecret = sessionKey
					)

					peerEcdhPublicKeys[peerUUID] = peerPubBytes

					PeerRepository.showSASVerification(peerUUID, endpointId, sasCode)

					Log.d("MeshService", "SAS generated for $peerUUID: $sasCode")
				}
				PayloadType.ChatAck -> {
					Log.d("MeshService", "Received ACK from $endpointId")
				}
				PayloadType.IdentityAnnounce -> {
					val peerUUID = endpointToUUIDMap[endpointId] ?: return
					if (body.size < 2) return

					val buffer = ByteBuffer.wrap(body)
					val publicKeyLen = buffer.short.toInt() and 0xFFFF
					if (body.size < 2 + publicKeyLen) return

					val peerIdentityPublicKey = ByteArray(publicKeyLen)
					buffer.get(peerIdentityPublicKey)

					val peerNameBytes = ByteArray(buffer.remaining())
					buffer.get(peerNameBytes)
					val peerName = String(peerNameBytes, Charsets.UTF_8)

					PeerRepository.updatePeerDisplayName(peerUUID, peerName)
					serviceScope.launch {
						nodeDao.updateDisplayName(peerUUID, peerName)
					}

					val publicKeyBase64 = Base64.encodeToString(
						peerIdentityPublicKey,
						Base64.NO_WRAP
					)
					pendingPeerIdentities[peerUUID] = peerName to publicKeyBase64

					if (peerUUID in confirmedSasPeers)
					{
						if (!hasSentIdentityTo(endpointId))
						{
							connectionsClient.sendPayload(endpointId, Payload.fromBytes(buildIdentityPacket()))
							markIdentitySent(endpointId)
						}
						completeSasVerification(peerUUID, endpointId, peerName, publicKeyBase64)
					}

					Log.d("MeshService", "Received identity announce from $peerUUID ($peerName)")
				}

				PayloadType.FloodEnvelope -> {
					handleFloodEnvelope(endpointId, body)
				}
				else -> Unit
			}
		}

		override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate)
		{
			// Track transfer progress
		}
	}

	private fun buildIdentityPacket(): ByteArray
	{
		val publicKeyBytes = secureKeyStore.getMyIdentityPublicKeyBytes()
		val nameBytes = currentSessionDisplayName.toByteArray(Charsets.UTF_8)

		val buffer = ByteBuffer.allocate(1 + 2 + publicKeyBytes.size + nameBytes.size)
		buffer.put(PayloadType.IdentityAnnounce.code)
		buffer.putShort(publicKeyBytes.size.toShort())
		buffer.put(publicKeyBytes)
		buffer.put(nameBytes)

		return buffer.array()
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
		val msgChannel = NotificationChannel(
			"NodusMessagesChannelID",
			"Incoming Messages",
			NotificationManager.IMPORTANCE_HIGH
		)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(notifChannel)
		manager.createNotificationChannel(msgChannel)

		// Nearby Connections Setup
		connectionsClient = Nearby.getConnectionsClient(this)

		// Shared Preferences
		sharedPreferences = getSharedPreferences("NodusPreferences", MODE_PRIVATE)

		// Fetch UUID
		myUUID = getOrCreateUUID()
		currentSessionDisplayName = sharedPreferences.getString("NodusDisplayName", null)?.takeIf { it.isNotBlank() } ?: "Node"

		db = NodusDatabase.getDBInstance(this)

		nodeDao = db.nodeDao()
		messageDao = db.messageDao()

		// Keys
		secureKeyStore = SecureKeyStore(this)

		serviceScope.launch {
			nodeDao.getVerifiedNodes().collect { nodes ->
				verifiedNodes = nodes.map { it.nodeId }.toSet()
			}
		}

		serviceScope.launch {
			PeerRepository.peers.collect { currentPeers ->
				if (isForegroundStarted)
				{
					updateForegroundNodeCount(currentPeers.size)
				}
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		beaconJob?.cancel()
		isMeshStarted = false
		serviceScope.cancel()
		if (::connectionsClient.isInitialized)
		{
			connectionsClient.stopAdvertising()
			connectionsClient.stopDiscovery()
			connectionsClient.stopAllEndpoints()
		}
		instance = null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
	{
		if (intent?.action == "ACTION_STOP_SERVICE")
		{
			isForegroundStarted = false
			stopForeground(STOP_FOREGROUND_REMOVE)
			stopSelf()
			return START_NOT_STICKY
		}

		ServiceCompat.startForeground(
			this,
			100,
			buildForegroundNotification(PeerRepository.peers.value.size),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
		)
		isForegroundStarted = true

		startMesh()

		return START_STICKY
	}

	private fun buildForegroundNotification(connectedCount: Int) = run {
		val stopIntent = Intent(this, MeshService::class.java).apply {
			action = "ACTION_STOP_SERVICE"
		}
		val pendingIntent = PendingIntent.getService(
			this,
			0,
			stopIntent,
			PendingIntent.FLAG_IMMUTABLE
		)
		val openAppIntent = PendingIntent.getActivity(
			this,
			1,
			Intent(this, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			},
			PendingIntent.FLAG_IMMUTABLE
		)
		val nodeLabel = if (connectedCount == 1) "node" else "nodes"
		NotificationCompat.Builder(this, "NodusMeshChannelID")
			.setSmallIcon(android.R.drawable.ic_notification_overlay)
			.setContentTitle("Nodus is running")
			.setContentText("Connected to $connectedCount $nodeLabel")
			.setContentIntent(openAppIntent)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setOnlyAlertOnce(true)
			.setAutoCancel(false)
			.addAction(
				0,
				"Quit App",
				pendingIntent
			).build()
	}

	private fun updateForegroundNodeCount(connectedCount: Int)
	{
		try
		{
			val manager = getSystemService(NotificationManager::class.java)
			manager.notify(100, buildForegroundNotification(connectedCount))
		}
		catch (e: SecurityException)
		{
			Log.w("MeshService", "Missing notification permission: ${e.message}")
		}
	}

	fun startMesh()
	{
		if (isMeshStarted) return
		isMeshStarted = true
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

		startPresenceBeaconLoop()
	}

	fun startKeyExchange(endpointId: String)
	{
		endpointToUUIDMap[endpointId]?.let { confirmedSasPeers.remove(it) }
		sentIdentities.remove(endpointId)
		val keyPair = CryptoManager.generateEcKeyPair()
		localKeyPairs[endpointId] = keyPair

		val pubKeyBytes = keyPair.public.encoded
		val packet = byteArrayOf(PayloadType.KeyExchange.code, 0) + pubKeyBytes
		connectionsClient.sendPayload(endpointId, Payload.fromBytes(packet))
	}

	fun confirmSasVerification(peerUuid: String) {
		val sessionKey = activeSessionKeys[peerUuid]
		if (sessionKey != null) {
			confirmedSasPeers.add(peerUuid)
			secureKeyStore.storeSessionKey(peerUuid, sessionKey)

			val endpointId = endpointToUUIDMap.entries.firstOrNull { it.value == peerUuid }?.key
			if (endpointId != null) {
				if (!hasSentIdentityTo(endpointId)) {
					connectionsClient.sendPayload(
						endpointId,
						Payload.fromBytes(buildIdentityPacket())
					)
					markIdentitySent(endpointId)
				}
				pendingPeerIdentities[peerUuid]?.let { (peerName, pubKeyBase64) ->
					completeSasVerification(peerUuid, endpointId, peerName, pubKeyBase64)
				}
			}
			PeerRepository.clearSasVerification()
		}
	}

	private fun completeSasVerification(peerUuid: String, endpointId: String, peerName: String, publicKeyBase64: String)
	{
		pendingPeerIdentities.remove(peerUuid)
		initialiseRatchetIfNeeded(peerUuid, endpointId)
		serviceScope.launch {
			nodeDao.insertNode(
				NodeEntity(
					peerUuid,
					peerName,
					publicKeyBase64,
					"VerifiedSAS",
					System.currentTimeMillis()
				)
			)
		}
		PeerRepository.setPeerVerified(peerUuid, true)
	}

	fun rejectSasVerification(peerUuid: String)
	{
		activeSessionKeys.remove(peerUuid)
		peerEcdhPublicKeys.remove(peerUuid)
		confirmedSasPeers.remove(peerUuid)
		pendingPeerIdentities.remove(peerUuid)
		endpointToUUIDMap.entries.firstOrNull { it.value == peerUuid }?.key?.let { epId ->
			localKeyPairs.remove(epId)
			sentIdentities.remove(epId)
		}
		PeerRepository.clearSasVerification()
	}

	fun verifyPeer(targetUuid: String)
	{
		if (pendingMutualVerifications.containsKey(targetUuid)) return

		requestMutualVerification(targetUuid)

		val timeoutJob = serviceScope.launch {
			delay(4000L.milliseconds)
			pendingMutualVerifications.remove(targetUuid)
			val endpointId = endpointToUUIDMap.entries.firstOrNull { it.value == targetUuid }?.key
			if (endpointId != null)
			{
				val existingNode = nodeDao.getNodeById(targetUuid)
				if (existingNode?.trustStatus != "VerifiedMutual")
				{
					Log.d("MeshService", "Mutual verification timed out for $targetUuid, falling back to SAS")
					startKeyExchange(endpointId)
				}
			}
			else
			{
				Log.w("MeshService", "Mutual verification failed for 2+ hop peer $targetUuid")
			}
		}
		pendingMutualVerifications[targetUuid] = timeoutJob
	}

	fun requestMutualVerification(targetUuid: String)
	{
		val reqBytes = myUUID.toByteArray(Charsets.UTF_8)
		val targetBytes = targetUuid.toByteArray(Charsets.UTF_8)

		val buf = ByteBuffer.allocate(1 + 2 + reqBytes.size + 2 + targetBytes.size)
		buf.put(PayloadType.MutualVerifyRequest.code)
		buf.putShort(reqBytes.size.toShort())
		buf.put(reqBytes)
		buf.putShort(targetBytes.size.toShort())
		buf.put(targetBytes)

		broadcastFlood(buf.array(), bypassRateLimit = true)
	}

	fun sendEncryptedMessage(targetUuid: String, plaintext: String): String?
	{
		val trimmed = plaintext.trim()
		if (trimmed.isEmpty() || trimmed.length > CryptoManager.CHAR_LIMIT) return null
		val state = secureKeyStore.getRatchetState(targetUuid)
		if (state == null)
		{
			serviceScope.launch {
				val node = nodeDao.getNodeById(targetUuid)
				if (node?.trustStatus == "VerifiedMutual")
				{
					initiateMultiHopKeyExchange(targetUuid)
				}
			}
			return null
		}

		val msgUuid = UUID.randomUUID()
		val msgId = msgUuid.toString()
		val (updatedState, ratchetCiphertext) = CryptoManager.ratchetEncrypt(state, trimmed)
		val senderBytes = myUUID.toByteArray(Charsets.UTF_8)
		val targetBytes = targetUuid.toByteArray(Charsets.UTF_8)

		val buf = ByteBuffer.allocate(1 + 16 + 2 + senderBytes.size + 2 + targetBytes.size + ratchetCiphertext.size)
		buf.put(PayloadType.ChatMessage.code)
		buf.putLong(msgUuid.mostSignificantBits)
		buf.putLong(msgUuid.leastSignificantBits)
		buf.putShort(senderBytes.size.toShort())
		buf.put(senderBytes)
		buf.putShort(targetBytes.size.toShort())
		buf.put(targetBytes)
		buf.put(ratchetCiphertext)

		val sent = broadcastFlood(buf.array())
		if (sent)
		{
			secureKeyStore.storeRatchetState(targetUuid, updatedState)
			serviceScope.launch {
				messageDao.insertMessage(
					MessageEntity(
						msgId,
						myUUID,
						targetUuid,
						trimmed,
						System.currentTimeMillis(),
						"SENT"
					)
				)
			}
			return msgId
		}
		return null
	}

	private fun sendChatAck(targetUuid: String, msgUuid: UUID)
	{
		val senderBytes = myUUID.toByteArray(Charsets.UTF_8)
		val targetBytes = targetUuid.toByteArray(Charsets.UTF_8)

		val buf = ByteBuffer.allocate(1 + 16 + 2 + senderBytes.size + 2 + targetBytes.size)
		buf.put(PayloadType.ChatAck.code)
		buf.putLong(msgUuid.mostSignificantBits)
		buf.putLong(msgUuid.leastSignificantBits)
		buf.putShort(senderBytes.size.toShort())
		buf.put(senderBytes)
		buf.putShort(targetBytes.size.toShort())
		buf.put(targetBytes)
		broadcastFlood(buf.array(), bypassRateLimit = true)
	}

	private fun initialiseRatchetIfNeeded(peerUuid: String, endpointId: String)
	{
		if (secureKeyStore.getRatchetState(peerUuid) != null) return
		val sessionKey = activeSessionKeys[peerUuid] ?: return
		val myKeyPair = localKeyPairs[endpointId] ?: return
		val peerEcdhPub = peerEcdhPublicKeys[peerUuid] ?: return

		val state = CryptoManager.initRatchetSymmetric(
			sharedSecret = sessionKey,
			myInitialEcdhKeyPair = myKeyPair,
			peerInitialEcdhPub = peerEcdhPub,
			isLexicographicallySmaller = myUUID < peerUuid
		)
		secureKeyStore.storeRatchetState(peerUuid, state)
	}

	private fun initiateMultiHopKeyExchange(targetUuid: String, isReply: Boolean = false)
	{
		if (secureKeyStore.getRatchetState(targetUuid) != null) return
		val keyPair = multiHopLocalKeyPairs.computeIfAbsent(targetUuid)
		{
			CryptoManager.generateEcKeyPair()
		}
		val ephPubBytes = keyPair.public.encoded
		val header = "$myUUID:$targetUuid:".toByteArray(Charsets.UTF_8)
		val signature = secureKeyStore.signData(header + ephPubBytes)

		val senderBytes = myUUID.toByteArray(Charsets.UTF_8)
		val targetBytes = targetUuid.toByteArray(Charsets.UTF_8)
		val buf = ByteBuffer.allocate(
			1 + 1 + 2 + senderBytes.size + 2 + targetBytes.size + 2 + ephPubBytes.size + signature.size
		)
		buf.put(PayloadType.MultiHopKeyExchange.code)
		buf.put(if (isReply) 1.toByte() else 0.toByte())
		buf.putShort(senderBytes.size.toShort())
		buf.put(senderBytes)
		buf.putShort(targetBytes.size.toShort())
		buf.put(targetBytes)
		buf.putShort(ephPubBytes.size.toShort())
		buf.put(ephPubBytes)
		buf.put(signature)

		broadcastFlood(buf.array(), bypassRateLimit = true)
		Log.d("MeshService", "Sent MultiHopKeyExchange (isReply=$isReply) to $targetUuid")
	}

	private fun startPresenceBeaconLoop()
	{
		beaconJob?.cancel()
		beaconJob = serviceScope.launch {
			while (isActive)
			{
				delay(BEACON_INTERVAL_MS.milliseconds)
				val now = System.currentTimeMillis()
				val directUuids = endpointToUUIDMap.values.toSet()
				peerLastSeen.entries.removeIf { (uuid, lastSeen) ->
					val isStale = uuid !in directUuids && (now - lastSeen) > STALE_PEER_TIMEOUT_MS
					if (isStale)
					{
						PeerRepository.removePeerByUUID(uuid)
					}
					isStale
				}
				val hasVerifiedNeighbor = endpointToUUIDMap.values.any { it in verifiedNodes }
				if (hasVerifiedNeighbor)
				{
					val uuidBytes = myUUID.toByteArray(Charsets.UTF_8)
					val nameBytes = currentSessionDisplayName.toByteArray(Charsets.UTF_8)
					val beacon = ByteBuffer.allocate(1 + 2 + uuidBytes.size + nameBytes.size).apply {
						put(PayloadType.PresenceBeacon.code)
						putShort(uuidBytes.size.toShort())
						put(uuidBytes)
						put(nameBytes)
					}.array()
					broadcastFlood(beacon, bypassRateLimit = true)
				}
			}
		}
	}

	private fun packFloodEnvelope(packetId: Long, ttl: Byte, innerPayload: ByteArray): ByteArray
	{
		val buffer = ByteBuffer.allocate(1 + 8 + 1 + innerPayload.size)
		buffer.put(PayloadType.FloodEnvelope.code)
		buffer.putLong(packetId)
		buffer.put(ttl)
		buffer.put(innerPayload)
		return buffer.array()
	}

	fun broadcastFlood(innerPayload: ByteArray, ttl: Byte = MAX_TTL, bypassRateLimit: Boolean = false): Boolean
	{
		val now = System.currentTimeMillis()
		if (!bypassRateLimit)
		{
			val prev = lastOutboundFloodTime.get()
			if (now - prev < RATE_LIMIT_MS) return false
			if (!lastOutboundFloodTime.compareAndSet(prev, now)) return false
		}

		val packetId = secureRandom.nextLong()
		synchronized(seenPacketIds) { seenPacketIds[packetId] = true }

		val envelope = packFloodEnvelope(packetId, ttl, innerPayload)
		var sentCount = 0
		endpointToUUIDMap.forEach { (epId, uuid) ->
			if (uuid in verifiedNodes)
			{
				Log.d("MeshService", "Sending flood packet ${innerPayload[0]} to verified peer $uuid")
				connectionsClient.sendPayload(epId, Payload.fromBytes(envelope))
				sentCount++
			}
		}
		return sentCount > 0
	}

	private fun handleFloodEnvelope(fromEndpointId: String, body: ByteArray)
	{
		val neighborUuid = endpointToUUIDMap[fromEndpointId] ?: return
		if (neighborUuid !in verifiedNodes)
		{
			Log.w("MeshService", "Dropped flood from unverified neighbor $neighborUuid (verifiedNodes=$verifiedNodes)")
			return
		}

		if (body.size < 10) return
		val buffer = ByteBuffer.wrap(body)
		val packetId = buffer.long
		val ttl = buffer.get()

		synchronized(seenPacketIds) {
			if (seenPacketIds.containsKey(packetId)) return
			seenPacketIds[packetId] = true
		}

		val innerPayload = ByteArray(buffer.remaining()).also { buffer.get(it) }
		val innerType = innerPayload.firstOrNull()?.let { PayloadType.fromByte(it) }

		if (innerType != PayloadType.MutualVerifyRequest &&
			innerType != PayloadType.MutualVerifyResponse &&
			innerType != PayloadType.MultiHopKeyExchange &&
			innerType != PayloadType.ChatAck
		)
		{
			val now = System.currentTimeMillis()
			if (now - (lastInboundFloodTime[fromEndpointId] ?: 0L) < RATE_LIMIT_MS) return
			lastInboundFloodTime[fromEndpointId] = now
		}
		val hops = (MAX_TTL - ttl) + 1

		dispatchFloodedPayload(hops, innerPayload)

		if (ttl > 1) {
			val nextEnvelope = packFloodEnvelope(packetId, (ttl - 1).toByte(), innerPayload)
			endpointToUUIDMap.forEach { (epId, uuid) ->
				if (epId != fromEndpointId && uuid in verifiedNodes) {
					connectionsClient.sendPayload(epId, Payload.fromBytes(nextEnvelope))
				}
			}
		}
	}

	private fun dispatchFloodedPayload(hops: Int, innerPayload: ByteArray)
	{
		if (innerPayload.isEmpty()) return
		val innerType = PayloadType.fromByte(innerPayload[0]) ?: return
		val innerBody = innerPayload.copyOfRange(1, innerPayload.size)

		when (innerType) {
			PayloadType.PresenceBeacon -> {
				if (innerBody.size < 2) return
				val buf = ByteBuffer.wrap(innerBody)
				val uuidLen = buf.short.toInt() and 0xFFFF
				if (innerBody.size < 2 + uuidLen) return

				val senderUuid = ByteArray(uuidLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				val beaconName = ByteArray(buf.remaining()).also { buf.get(it) }.toString(Charsets.UTF_8).ifBlank { null }
				if (senderUuid == myUUID) return
				peerLastSeen[senderUuid] = System.currentTimeMillis()

				serviceScope.launch {
					val savedNode = nodeDao.getNodeById(senderUuid)
					val isVerified = savedNode?.let {
						it.trustStatus == "VerifiedSAS" || it.trustStatus == "VerifiedMutual"
					} ?: false

					val existingName = savedNode?.displayName?: PeerRepository.peers.value.firstOrNull { it.uuid == senderUuid }?.displayName
					val resolvedName = beaconName ?: existingName ?: "Node-${senderUuid.take(5)}"

					PeerRepository.addOrUpdatePeer(
						Peer(
							displayName = resolvedName,
							uuid = senderUuid,
							isVerified = isVerified,
							hops = hops
						)
					)
					if (savedNode != null)
					{
						nodeDao.updateLastSeen(senderUuid)
						if (beaconName != null)
						{
							nodeDao.updateDisplayName(senderUuid, beaconName)
						}					}
				}
			}

			PayloadType.MutualVerifyRequest -> {
				if (innerBody.size < 4) return
				val buf = ByteBuffer.wrap(innerBody)
				val reqLen = buf.short.toInt() and 0xFFFF
				val requesterUuid = ByteArray(reqLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				val targetLen = buf.short.toInt() and 0xFFFF
				val targetUuid = ByteArray(targetLen).also { buf.get(it) }.toString(Charsets.UTF_8)

				if (requesterUuid == myUUID || targetUuid == myUUID) return

				serviceScope.launch {
					val requesterNode = nodeDao.getNodeById(requesterUuid)
					val isRequesterTrusted = requesterNode?.trustStatus == "VerifiedSAS" || requesterNode?.trustStatus == "VerifiedMutual"
					if (!isRequesterTrusted) return@launch

					val targetNode = nodeDao.getNodeById(targetUuid)
					val isTrusted = targetNode?.trustStatus == "VerifiedSAS" ||
							targetNode?.trustStatus == "VerifiedMutual"

					Log.d("MeshService", "Bridge lookup for $targetUuid: trusted=$isTrusted, hasKey=${targetNode?.publicKey != null}")
					if (isTrusted && targetNode.publicKey != null) {
						val attestationPayload = "$targetUuid:${targetNode.publicKey}".toByteArray(Charsets.UTF_8)
						val signature = secureKeyStore.signData(attestationPayload)

						val reqBytes = requesterUuid.toByteArray(Charsets.UTF_8)
						val bridgeBytes = myUUID.toByteArray(Charsets.UTF_8)
						val targetBytes = targetUuid.toByteArray(Charsets.UTF_8)
						val pubKeyBytes = targetNode.publicKey.toByteArray(Charsets.UTF_8)

						val out = ByteBuffer.allocate(
							1 + 2 + reqBytes.size + 2 + bridgeBytes.size + 2 + targetBytes.size + 2 + pubKeyBytes.size + signature.size
						)
						out.put(PayloadType.MutualVerifyResponse.code)
						out.putShort(reqBytes.size.toShort())
						out.put(reqBytes)
						out.putShort(bridgeBytes.size.toShort())
						out.put(bridgeBytes)
						out.putShort(targetBytes.size.toShort())
						out.put(targetBytes)
						out.putShort(pubKeyBytes.size.toShort())
						out.put(pubKeyBytes)
						out.put(signature)

						delay(600L.milliseconds)
						broadcastFlood(out.array(), bypassRateLimit = true)
					}
					else
					{
						Log.w("MeshService", "Bridge cannot vouch for $targetUuid (trusted=$isTrusted, hasKey=${targetNode?.publicKey != null})")
					}
				}
			}

			PayloadType.MutualVerifyResponse -> {
				try
				{
					if (innerBody.size < 8) return
					val buf = ByteBuffer.wrap(innerBody)

					val reqLen = buf.short.toInt() and 0xFFFF
					val requesterUuid =
						ByteArray(reqLen).also { buf.get(it) }.toString(Charsets.UTF_8)

					val bridgeLen = buf.short.toInt() and 0xFFFF
					val bridgeUuid =
						ByteArray(bridgeLen).also { buf.get(it) }.toString(Charsets.UTF_8)

					val targetLen = buf.short.toInt() and 0xFFFF
					val targetUuid =
						ByteArray(targetLen).also { buf.get(it) }.toString(Charsets.UTF_8)

					if (targetUuid == myUUID)
					{
						serviceScope.launch {
							val existing = nodeDao.getNodeById(requesterUuid)
							val alreadyVerified =
								existing?.trustStatus == "VerifiedSAS" || existing?.trustStatus == "VerifiedMutual"
							if (!alreadyVerified && !pendingMutualVerifications.containsKey(
									requesterUuid
								)
							)
							{
								delay(600L.milliseconds)
								requestMutualVerification(requesterUuid)
							}
						}
						return
					}

					if (requesterUuid != myUUID) return

					val pubKeyLen = buf.short.toInt() and 0xFFFF
					val targetPubKeyBase64 =
						ByteArray(pubKeyLen).also { buf.get(it) }.toString(Charsets.UTF_8)

					val signatureBytes = ByteArray(buf.remaining()).also { buf.get(it) }

					serviceScope.launch {
						val bridgeNode = nodeDao.getNodeById(bridgeUuid)
						val isBridgeTrusted =
							bridgeNode?.trustStatus == "VerifiedSAS" || bridgeNode?.trustStatus == "VerifiedMutual"

						Log.d(
							"MeshService",
							"VerifyResponse from bridge $bridgeUuid: bridgeTrusted=$isBridgeTrusted"
						)
						if (isBridgeTrusted && bridgeNode.publicKey != null)
						{
							val bridgePubKeyBytes =
								Base64.decode(bridgeNode.publicKey, Base64.NO_WRAP)
							val attestationPayload =
								"$targetUuid:$targetPubKeyBase64".toByteArray(Charsets.UTF_8)

							val isValid = CryptoManager.verifySignature(
								publicKeyBytes = bridgePubKeyBytes,
								data = attestationPayload,
								signatureBytes = signatureBytes
							)
							if (isValid)
							{
								pendingMutualVerifications.remove(targetUuid)?.cancel()
								nodeDao.insertNode(
									NodeEntity(
										nodeId = targetUuid,
										displayName = getPeerLabel(targetUuid),
										publicKey = targetPubKeyBase64,
										trustStatus = "VerifiedMutual",
										lastSeen = System.currentTimeMillis()
									)
								)
							}
							PeerRepository.addOrUpdatePeer(
								Peer(
									getPeerLabel(targetUuid),
									targetUuid,
									true,
									2
								)
							)
							PeerRepository.setPeerVerified(targetUuid, true)
							showMutualVerificationNotification(targetUuid, bridgeUuid)
							Log.d(
								"MeshService",
								"Mutually verified $targetUuid via bridge $bridgeUuid"
							)
							initiateMultiHopKeyExchange(targetUuid)
						}
					}
				}
				catch (_: BufferUnderflowException)
				{
					Log.w("MeshService", "Malformed MutualVerifyResponse packet dropped")
				}
			}

			PayloadType.MultiHopKeyExchange -> {
				if (innerBody.size < 7) return

				val buf = ByteBuffer.wrap(innerBody)
				val isReply = buf.get() == 1.toByte()
				val senderLen = buf.short.toInt() and 0xFFFF
				if (buf.remaining() < senderLen + 2) return

				val senderUuid = ByteArray(senderLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				val targetLen = buf.short.toInt() and 0xFFFF
				if (buf.remaining() < targetLen + 2) return

				val targetUuid = ByteArray(targetLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				if (targetUuid != myUUID) return

				val ephLen = buf.short.toInt() and 0xFFFF
				if (buf.remaining() < ephLen) return

				val peerEphPub = ByteArray(ephLen).also { buf.get(it) }
				val signatureBytes = ByteArray(buf.remaining()).also { buf.get(it) }

				serviceScope.launch {
					val senderNode = nodeDao.getNodeById(senderUuid)
					val isTrusted = senderNode?.trustStatus == "VerifiedMutual" ||
					senderNode?.trustStatus == "VerifiedSAS"
					if (!isTrusted || senderNode.publicKey == null) return@launch
					val senderPubKeyBytes = Base64.decode(senderNode.publicKey, Base64.NO_WRAP)
					val signedData = "$senderUuid:$myUUID:".toByteArray(Charsets.UTF_8) + peerEphPub
					val isValid = CryptoManager.verifySignature(
						publicKeyBytes = senderPubKeyBytes,
						data = signedData,
						signatureBytes = signatureBytes
					)
					if (!isValid)
					{
						Log.w("MeshService", "Invalid MultiHopKeyExchange signature from $senderUuid")
						return@launch
					}

					val myKeyPair = if (isReply)
					{
						multiHopLocalKeyPairs[senderUuid] ?: return@launch
					}
					else
					{
						multiHopLocalKeyPairs.computeIfAbsent(senderUuid) { CryptoManager.generateEcKeyPair() }
					}
					if (!isReply)
					{
						initiateMultiHopKeyExchange(senderUuid, isReply = true)
					}

					if (secureKeyStore.getRatchetState(senderUuid) == null)
					{
						val sharedSecret = CryptoManager.deriveSharedSecret(myKeyPair.private, peerEphPub)
						val state = CryptoManager.initRatchetSymmetric(
							sharedSecret = sharedSecret,
							myInitialEcdhKeyPair = myKeyPair,
							peerInitialEcdhPub = peerEphPub,
							isLexicographicallySmaller = myUUID < senderUuid
						)
						secureKeyStore.storeRatchetState(senderUuid, state)
						multiHopLocalKeyPairs.remove(senderUuid)
						Log.d("MeshService", "Initialized multi-hop ratchet state with $senderUuid")
					}
				}
			}

			PayloadType.ChatMessage -> {
				if (innerBody.size < 20) return
				val buf = ByteBuffer.wrap(innerBody)

				val msgUuid = UUID(buf.long, buf.long)
				val msgIdStr = msgUuid.toString()
				synchronized(seenMessageIds)
				{
					if (seenMessageIds.containsKey(msgIdStr)) return
					seenMessageIds[msgIdStr] = true
				}

				val senderLen = buf.short.toInt() and 0xFFFF
				val senderUuid = ByteArray(senderLen).also { buf.get(it) }.toString(Charsets.UTF_8)

				val targetLen = buf.short.toInt() and 0xFFFF
				val targetUuid = ByteArray(targetLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				if (targetUuid != myUUID) return

				val ratchetPacket = ByteArray(buf.remaining()).also { buf.get(it) }
				val currentState = secureKeyStore.getRatchetState(senderUuid) ?: return

				try {
					val (updatedState, decryptedText) = CryptoManager.ratchetDecrypt(currentState, ratchetPacket)
					secureKeyStore.storeRatchetState(senderUuid, updatedState)
					serviceScope.launch {
						messageDao.insertMessage(
							MessageEntity(
								msgUuid.toString(),
								senderUuid,
								myUUID,
								decryptedText,
								System.currentTimeMillis(),
								"DELIVERED"
							)
						)
						if (PeerRepository.activeChatPeerUuid != senderUuid)
						{
							val senderName = PeerRepository.peers.value.firstOrNull { it.uuid == senderUuid }
								?.displayName?.takeUnless { it.startsWith("Node-") }
								?: nodeDao.getNodeById(senderUuid)?.displayName
								?: "Node-${senderUuid.take(5)}"
							showIncomingMessageNotification(senderUuid, senderName, decryptedText)
						}
					}
					sendChatAck(senderUuid, msgUuid)
				} catch (e: Exception) {
					Log.e("MeshService", "Ratchet decryption failed from $senderUuid: ${e.message}")
				}
			}

			PayloadType.ChatAck -> {
				if (innerBody.size < 20) return
				val buf = ByteBuffer.wrap(innerBody)
				val msgUuid = UUID(buf.long, buf.long)
				val senderLen = buf.short.toInt() and 0xFFFF
				val senderUuid = ByteArray(senderLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				val targetLen = buf.short.toInt() and 0xFFFF
				val targetUuid = ByteArray(targetLen).also { buf.get(it) }.toString(Charsets.UTF_8)
				if (targetUuid == myUUID)
				{
					serviceScope.launch {
						messageDao.updateMessageStatus(msgUuid.toString(), "DELIVERED")
					}
					Log.d("MeshService", "Received delivery ACK from $senderUuid for $msgUuid")
				}
			}

			else -> Unit
		}
	}

	private fun getPeerLabel(uuid: String): String
	{
		return PeerRepository.peers.value.firstOrNull { it.uuid == uuid }?.displayName ?: "Node-${uuid.take(5)}"
	}

	private fun showMutualVerificationNotification(verifiedUuid: String, bridgeUuid: String)
	{
		val peerLabel = getPeerLabel(verifiedUuid)
		val bridgeLabel = getPeerLabel(bridgeUuid)
		val message = "$peerLabel has been verified by mutual nodes $bridgeLabel"
		PeerRepository.emitMutualVerification(message)

		val notif = NotificationCompat.Builder(this, "NodusMeshChannelID")
			.setSmallIcon(android.R.drawable.ic_dialog_info)
			.setContentTitle("Peer Mutually Verified")
			.setContentText("Verified $peerLabel via mutual node $bridgeLabel")
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setAutoCancel(true)
			.build()

		try
		{
			val manager = getSystemService(NotificationManager::class.java)
			manager.notify(verifiedUuid.hashCode(), notif)
		}
		catch (e: SecurityException)
		{
			Log.w("MeshService", "Missing notification permission: ${e.message}")
		}
	}

	private fun showIncomingMessageNotification(senderUuid: String, senderName: String, messageText: String)
	{
		val openAppIntent = PendingIntent.getActivity(
			this,
			senderUuid.hashCode(),
			Intent(this, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
				putExtra("NAVIGATE_TO_PEER", senderUuid)
			},
			PendingIntent.FLAG_IMMUTABLE
		)

		val notif = NotificationCompat.Builder(this, "NodusMessagesChannelID")
			.setSmallIcon(android.R.drawable.ic_dialog_email)
			.setContentTitle(senderName)
			.setContentText(messageText)
			.setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
			.setContentIntent(openAppIntent)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setAutoCancel(true)
			.build()

		try
		{
			val manager = getSystemService(NotificationManager::class.java)
			manager.notify(senderUuid.hashCode() + 10000, notif)
		}
		catch (e: SecurityException)
		{
			Log.w("MeshService", "Missing notification permission: ${e.message}")
		}
	}
}