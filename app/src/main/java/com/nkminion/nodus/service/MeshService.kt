package com.nkminion.nodus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class MeshService : Service()
{
	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onCreate() {
		super.onCreate()
		val notifChannel = NotificationChannel(
			"NodusMeshChannelID",
			"NodusMeshChannel",
			NotificationManager.IMPORTANCE_LOW
		)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(notifChannel)
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
}