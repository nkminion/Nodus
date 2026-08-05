package com.nkminion.nodus.ui

import android.widget.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nkminion.nodus.ui.theme.NodusTheme



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationPageContent(userName: String)
{
	val customRipple = RippleConfiguration(color = MaterialTheme.colorScheme.secondary)
	Scaffold(
		topBar = {
			TopAppBar(
				colors = topAppBarColors(
					containerColor = MaterialTheme.colorScheme.primaryContainer,
					titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
				),
				title = {
					Text(
						text = userName,
						textAlign = TextAlign.Center,
						modifier = Modifier
							.fillMaxWidth()
					)
				}
			)
		},
	) { innerPadding ->
		LazyColumn(modifier = Modifier
			.padding(innerPadding)
			.fillMaxHeight()
			.fillMaxWidth(),
		) {
			item {
				Row(
					modifier = Modifier
						.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceEvenly
				) {
					CompositionLocalProvider(LocalRippleConfiguration provides customRipple)
					{
						TextButton(
							onClick = { print("Clicked Nearby") },
							modifier = Modifier.weight(1f),
							colors = ButtonDefaults.textButtonColors(
								containerColor = MaterialTheme.colorScheme.primaryContainer,
								contentColor = MaterialTheme.colorScheme.onPrimaryContainer
							),
							shape = RectangleShape
						) {
							Text("Nearby")
						}
						TextButton(
							onClick = { print("Clicked History") },
							modifier = Modifier.weight(1f),
							colors = ButtonDefaults.textButtonColors(
								containerColor = MaterialTheme.colorScheme.primaryContainer,
								contentColor = MaterialTheme.colorScheme.onPrimaryContainer
							),
							shape = RectangleShape
						) {
							Text("History")
						}
					}
				}
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun NavigationPagePreview() {
	NodusTheme(dynamicColor = false)
	{
		NavigationPageContent(userName = "Minion")
	}
}