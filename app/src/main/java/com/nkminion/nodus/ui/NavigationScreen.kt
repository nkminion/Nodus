package com.nkminion.nodus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.samplePeers
import com.nkminion.nodus.ui.theme.NodusTheme

@Composable
fun PeerListItem(peer: Peer, modifier: Modifier) {
	val statusText = if (peer.isVerified) "Verified" else "Unverified"
	val hopLabel = if (peer.hops == 1) "1 hop" else "${peer.hops} hops"
	val circleColor = when {
		!peer.isVerified || peer.hops > 4 -> Color(0xFFFF0000)
		peer.hops > 1 -> Color(0xFFFFAE00)
		else -> Color(0xFF00FF60)
	}
	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp),
		colors = CardColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer,
			contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
			disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant,
			disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
		),
		shape = RectangleShape
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Box(
				modifier = Modifier
					.size(10.dp)
					.background(color = circleColor, shape = CircleShape)
			)
			Column {
				Text(
					text = peer.displayName,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface
				)
				Text(
					text = "$statusText ($hopLabel)",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreenContent(displayName: String,onPeerClick: (Peer) -> Unit)
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
						text = displayName,
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
					horizontalArrangement = Arrangement.Start
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

			//Dummies
			items(samplePeers) { peer ->
				PeerListItem(
					peer = peer,
					modifier = Modifier.clickable {
						onPeerClick(peer)
					}
				)
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun NavigationScreenPreview() {
	NodusTheme(dynamicColor = false)
	{
		NavigationScreenContent(displayName = "nkminion", onPeerClick = {})
	}
}