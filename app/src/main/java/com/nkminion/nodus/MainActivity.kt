package com.nkminion.nodus

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nkminion.nodus.navigation.AppNavigation
import com.nkminion.nodus.service.MeshService
import com.nkminion.nodus.ui.LoginScreenContent
import com.nkminion.nodus.ui.theme.NodusTheme

class MainActivity : ComponentActivity()
{
	private fun startMeshService()
	{
		val serviceIntent = Intent(this, MeshService::class.java)
		ContextCompat.startForegroundService(this, serviceIntent)
	}
	private val requestPermissionsLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		//permissions = Map<String,Boolean> btw
		val allGranted = permissions.values.all { it }
		if (allGranted)
		{
			startMeshService()
		}
		else
		{
			Toast.makeText(this, "Please enable permissions in order to use the app", Toast.LENGTH_LONG).show()
		}
	}
	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		val missingPermissions = mutableListOf<String>()
		if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			missingPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
		}
		if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			missingPermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
		}
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)
		{
			if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			{
				missingPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
		{
			if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
			{
				missingPermissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
			}
			if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
			{
				missingPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
			}
			if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
			{
				missingPermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
			}
		}
		if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU))
		{
			if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
			{
				missingPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
			}
			if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
			{
				missingPermissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
			}
		}
		if (missingPermissions.isNotEmpty())
		{
			requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
		}
		else
		{
			startMeshService()
		}
		enableEdgeToEdge()
		setContent()
		{
			NodusTheme(dynamicColor = false)
			{
				AppRouter()
			}
		}
	}
}

@Composable
fun AppRouter()
{
	var displayName by remember { mutableStateOf<String?>(null) }
	if (displayName == null)
	{
		LoginScreenContent(
			onSubmit = { enteredName ->
				displayName = enteredName
			}
		)
	}
	else
	{
		val userName: String = displayName ?: "placeholder"
		AppNavigation(displayName = userName)
	}
}