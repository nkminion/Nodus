package com.nkminion.nodus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nkminion.nodus.ui.theme.NodusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreenContent(onSubmit: (String) -> Unit)
{
	var textVal by remember { mutableStateOf("") }
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
						text = "Login Screen",
						textAlign = TextAlign.Center,
						modifier = Modifier
							.fillMaxWidth(),
						style = MaterialTheme.typography.titleLarge
					)
				}
			)
		},
	) { innerPadding ->
		Column(modifier = Modifier
			.padding(innerPadding)
			.fillMaxHeight()
			.fillMaxWidth(),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			OutlinedTextField(
				shape = RoundedCornerShape(28.dp),
				value = textVal,
				onValueChange = { newText: String ->
					textVal = newText
				},
				placeholder = {Text("Display Name")},
				label = {Text("Display Name")},
				singleLine = true
			)
			Spacer(modifier = Modifier.height(10.dp))
			CompositionLocalProvider(LocalRippleConfiguration provides customRipple)
			{
				OutlinedButton(
					onClick = {
						if (textVal.isNotBlank()) {
							onSubmit(textVal)
						}
					},
					border = BorderStroke(
						width = 2.dp,
						color = MaterialTheme.colorScheme.primary

					),
					colors = ButtonColors(
						containerColor = MaterialTheme.colorScheme.surface,
						contentColor = MaterialTheme.colorScheme.onSurface,
						disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
						disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				) {
					Text("Next")
				}
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
	NodusTheme(dynamicColor = false)
	{
		LoginScreenContent(onSubmit = {})
	}
}