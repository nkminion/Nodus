package com.nkminion.nodus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Strategy
import java.util.UUID
import androidx.core.content.edit
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.nkminion.nodus.dao.MessageDao
import com.nkminion.nodus.dao.NodeDao
import com.nkminion.nodus.database.NodusDatabase

class MeshService : Service()
{
	private lateinit var connectionsClient : ConnectionsClient
	private lateinit var sharedPreferences : SharedPreferences
	private lateinit var myUUID : String
	private lateinit var db : NodusDatabase
	private lateinit var nodeDao : NodeDao
	private lateinit var messageDao: MessageDao
	private val serviceID = "com.nkminion.nodus.MeshService"

	private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback()
	{
		override fun onEndpointFound(endpointId:String, info: DiscoveredEndpointInfo)
		{
			Log.d("MeshService", "Endpoint found: $endpointId (${info.endpointName})")
			connectionsClient.requestConnection(
				myUUID,
				endpointId,
				connectionLifecycleCallback
			)
		}

		override fun onEndpointLost(endpointId: String)
		{
			Log.d("MeshService", "Endpoint lost: $endpointId")
			// Mark endpoint as inactive and update ui
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
				val bytesPayload = Payload.fromBytes("PING".toByteArray())
				connectionsClient.sendPayload(endpointId, bytesPayload)
			}
			else
			{
				Log.e("MeshService", "Connection failed with status: ${result.status.statusCode}")
			}
		}

		override fun onDisconnected(endpointId: String)
		{
			Log.d("MeshService", "Disconnected from: $endpointId")
		}
	}

	private val payloadCallback = object : PayloadCallback()
	{
		override fun onPayloadReceived(endpointId: String, payload: Payload)
		{
			Log.d("MeshService", "Received bytes from $endpointId: ${payload.asBytes()?.size}")
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
		sharedPreferences = getSharedPreferences("NodusPreferences",Context.MODE_PRIVATE)

		// Fetch UUID
		myUUID = getOrCreateUUID()

		db = NodusDatabase.getDBInstance(this)

		nodeDao = db.nodeDao()
		messageDao = db.messageDao()
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
}