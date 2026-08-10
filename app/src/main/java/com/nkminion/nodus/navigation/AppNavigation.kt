package com.nkminion.nodus.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nkminion.nodus.data.Peer
import com.nkminion.nodus.data.samplePeers
import com.nkminion.nodus.ui.NavigationScreenContent
import com.nkminion.nodus.ui.TextScreenContent

@Composable
fun AppNavigation(displayName: String)
{
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
			val peer = samplePeers.find{it.uuid == peerUUID}
			TextScreenContent(
				peer = peer?: Peer(
					displayName = "Display Name",
					uuid = "Placeholder UUID",
					isVerified = true,
					hops = 1
				),
				onBackClick = { navController.popBackStack() }
			)
		}
	}
}