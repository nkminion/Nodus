package com.nkminion.nodus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nkminion.nodus.R
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.sampleMessages
import com.nkminion.nodus.data.samplePeers
import com.nkminion.nodus.ui.theme.NodusTheme

@Composable
fun TextBubble(
	message: String,
	isMe: Boolean,
	timestamp: String,
	modifier: Modifier = Modifier
) {
	val bubbleColor = if (isMe)
		MaterialTheme.colorScheme.primary
	else
		Color(0xFF1A1A1A)

	val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

	Box(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 4.dp),
		contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
	) {
		Surface(
			shape = RoundedCornerShape(
				topStart = 16.dp,
				topEnd = 16.dp,
				bottomStart = if (isMe) 16.dp else 4.dp,
				bottomEnd = if (isMe) 4.dp else 16.dp
			),
			color = bubbleColor,
			modifier = Modifier.padding(
				start = if (isMe) 70.dp else 0.dp,
				end = if (isMe) 0.dp else 70.dp
			)
		) {
			Column(
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
			) {
				Text(
					text = message,
					style = MaterialTheme.typography.bodyLarge,
					color = contentColor
				)
				Text(
					text = timestamp,
					style = MaterialTheme.typography.labelSmall,
					color = contentColor.copy(alpha = 0.7f),
					modifier = Modifier
						.align(Alignment.End)
						.padding(top = 2.dp)
				)
			}
		}
	}
}

@Composable
fun MessageInputBar(
	text: String,
	peerName: String,
	onValueChange: (String) -> Unit,
	onSendClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	Surface(
		tonalElevation = 2.dp,
		modifier = modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier
				.padding(8.dp)
				.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			OutlinedTextField(
				value = text,
				onValueChange = onValueChange,
				placeholder = { Text("Message $peerName") },
				modifier = Modifier.weight(1f),
				shape = CircleShape,
				maxLines = 4
			)
			IconButton(
				onClick = onSendClick,
			) {
				Icon(
					painter = painterResource(id = R.drawable.send_48px),
					contentDescription = "Send Message",
					tint = if (text.isNotBlank())
						MaterialTheme.colorScheme.secondary
					else
						MaterialTheme.colorScheme.outline
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreenContent(
	peer: Peer,
	onBackClick: () -> Unit
) {
	var inputText by remember { mutableStateOf("") }
	val circleColor = when {
		!peer.isVerified || peer.hops > 4 -> Color(0xFFFF0000)
		peer.hops > 1 -> Color(0xFFFFAE00)
		else -> Color(0xFF00FF60)
	}
	Scaffold(
		modifier = Modifier.imePadding(),
		topBar = {
			TopAppBar(
				navigationIcon = {
					IconButton(
						onClick = onBackClick,
						modifier = Modifier.background(color = MaterialTheme.colorScheme.surface)
					) {
						Icon(
							painter = painterResource(id = R.drawable.arrow_back_48px),
							contentDescription = "Go Back",
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				},
				title = {
					Row(
						modifier = Modifier
							.padding(horizontal = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp)
					) {
						Box(
							modifier = Modifier
								.size(12.dp)
								.background(color= circleColor, shape = CircleShape)
						)
						Column(
							modifier = Modifier.padding(6.dp)
						) {
							Text(
								text = peer.displayName,
								style = MaterialTheme.typography.bodyLarge,
								color = MaterialTheme.colorScheme.onSurface
							)
							Text(
								text = peer.uuid,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			)
		},
		bottomBar = {
			MessageInputBar(
				text = inputText,
				peerName = peer.displayName,
				onValueChange = { inputText = it },
				onSendClick = { inputText = "" }
			)
		}
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize(),
			reverseLayout = true,
			contentPadding = PaddingValues(vertical = 8.dp)
		) {
			items(sampleMessages) {msg ->
				TextBubble(
					message = msg.text,
					isMe = msg.isMe,
					timestamp = msg.timestamp
				)
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun TextScreenContentPreview() {
	NodusTheme(dynamicColor = false)
	{
		TextScreenContent(
			peer = samplePeers[0],
			onBackClick = {}
		)
	}
}