package com.nkminion.nodus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import com.nkminion.nodus.ui.LoginScreenContent
import com.nkminion.nodus.ui.theme.NodusTheme

class MainActivity : ComponentActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
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
	var userName by remember { mutableStateOf<String?>(null) }
	if (userName == null)
	{
		LoginScreenContent(
			onSubmit = { enteredName ->
				userName = enteredName
			}
		)
	}
	else
	{
		//Home Screen
	}
}