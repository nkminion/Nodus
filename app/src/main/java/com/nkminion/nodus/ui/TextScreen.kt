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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nkminion.nodus.R
import com.nkminion.nodus.crypto.CryptoManager
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.PeerRepository
import com.nkminion.nodus.data.samplePeers
import com.nkminion.nodus.database.NodusDatabase
import com.nkminion.nodus.service.MeshService
import com.nkminion.nodus.ui.theme.NodusTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TextBubble(
	message: String,
	isMe: Boolean,
	timestamp: String,
	modifier: Modifier = Modifier,
	isDelivered: Boolean = false
) {
	val bubbleColor = if (isMe)
		MaterialTheme.colorScheme.primary
	else
		MaterialTheme.colorScheme.primaryContainer

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
			modifier = Modifier
				.alpha(
					if (isMe && !isDelivered) 0.5f else 1f
				)
				.padding(
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
		modifier = modifier
			.fillMaxWidth()
			.navigationBarsPadding()
			.imePadding()
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
				onValueChange = { if (it.length <= CryptoManager.CHAR_LIMIT) onValueChange(it) },
				placeholder = { Text("Message $peerName (${text.length}/${CryptoManager.CHAR_LIMIT})") },
				modifier = Modifier.weight(1f),
				shape = CircleShape,
				maxLines = 4
			)
			IconButton(
				onClick = onSendClick,
				enabled = text.isNotBlank()
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
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner, peer.uuid)
	{
		val observer = LifecycleEventObserver { _, event ->
			when (event)
			{
				Lifecycle.Event.ON_RESUME -> PeerRepository.activeChatPeerUuid = peer.uuid
				Lifecycle.Event.ON_PAUSE -> if (PeerRepository.activeChatPeerUuid == peer.uuid)
				{
					PeerRepository.activeChatPeerUuid = null
				}
				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			if (PeerRepository.activeChatPeerUuid == peer.uuid)
			{
				PeerRepository.activeChatPeerUuid = null
			}
		}
	}
	val db = remember { NodusDatabase.getDBInstance(context) }
	val messages by db.messageDao().getMessagesForPeer(peer.uuid).collectAsState(initial = emptyList())
	val livePeers by PeerRepository.peers.collectAsState()
	val activePeer = livePeers.firstOrNull { it.uuid == peer.uuid } ?: peer.copy(hops = 5)
	val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
	val reversedMessages = remember(messages) { messages.asReversed() }

	var inputText by remember { mutableStateOf("") }
	val circleColor = when {
		!activePeer.isVerified || activePeer.hops > 4 -> Color(0xFFFF0000)
		activePeer.hops > 1 -> Color(0xFFFFAE00)
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
								text = activePeer.displayName,
								style = MaterialTheme.typography.bodyLarge,
								color = MaterialTheme.colorScheme.onSurface
							)
							Text(
								text = activePeer.uuid,
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
				peerName = activePeer.displayName,
				onValueChange = { inputText = it },
				onSendClick = {
					val msgId = MeshService.instance?.sendEncryptedMessage(activePeer.uuid, inputText)
					if (msgId != null)
					{
						inputText = ""
					}
				}
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
			items(reversedMessages, key = { it.messageId }) { msg ->
				TextBubble(
					message = msg.content,
					isMe = msg.recipientId == activePeer.uuid,
					timestamp = timeFormatter.format(Date(msg.timestamp)),
					isDelivered = msg.status == "DELIVERED"
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