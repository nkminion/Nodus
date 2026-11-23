package com.nkminion.nodus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.PeerRepository
import com.nkminion.nodus.database.NodusDatabase
import com.nkminion.nodus.service.MeshService
import com.nkminion.nodus.ui.NavigationScreenContent
import com.nkminion.nodus.ui.TextScreenContent

@Composable
fun AppNavigation(displayName: String)
{
	LaunchedEffect(displayName) {
		MeshService.instance?.currentSessionDisplayName = displayName
	}

	val context = LocalContext.current
	val db = remember { NodusDatabase.getDBInstance(context) }
	val navController = rememberNavController()

	NavHost(
		navController = navController,
		startDestination = "NavigationScreen"
	) {
		composable("NavigationScreen") {
			NavigationScreenContent(
				displayName = displayName,
				onPeerClick = { peer ->
					navController.navigate("TextScreen/${peer.uuid}"
					)
				}
			)
		}

		composable(
			route = "TextScreen/{peerUUID}",
			arguments = listOf(
				navArgument(name = "peerUUID") {type = NavType.StringType}
			)
		) { backStackEntry ->
			val peerUUID = backStackEntry.arguments?.getString("peerUUID") ?: ""
			val livePeers by PeerRepository.peers.collectAsState()
			val peer = livePeers.firstOrNull { it.uuid == peerUUID }
			val savedNode by produceState<com.nkminion.nodus.entity.NodeEntity?>(initialValue = null, peerUUID)
			{
				value = db.nodeDao().getNodeById(peerUUID)
			}
			TextScreenContent(
				peer = peer?: Peer(
					displayName = savedNode?.displayName ?: "Node-${peerUUID.take(5)}",
					uuid = peerUUID,
					isVerified = true,
					hops = 5
				),
				onBackClick = { navController.popBackStack() }
			)
		}
	}
}