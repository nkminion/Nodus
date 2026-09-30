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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Strategy
import java.util.UUID
import androidx.core.content.edit

class MeshService : Service()
{
	private lateinit var connectionsClient : ConnectionsClient
	private lateinit var sharedPreferences : SharedPreferences

	private val serviceID = "com.nkminion.nodus.MeshService"

	private val endpointDiscoveryCallback

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

		return START_STICKY
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

	fun startMesh()
	{
		val advertisingOptions = AdvertisingOptions.Builder()
			.setStrategy(Strategy.P2P_CLUSTER)
			.build()

		val discoveryOptions = DiscoveryOptions.Builder()
			.setStrategy(Strategy.P2P_CLUSTER)
			.build()

		val myUUID = getOrCreateUUID()

		connectionsClient.startAdvertising(
			myUUID,
			this.serviceID,
			// connectionLifecycleCallback,
			advertisingOptions
		)

		connectionsClient.startDiscovery(
			this.serviceID,
			//endpointDiscoveryCallback,
			discoveryOptions
		)
	}
}