package com.nkminion.nodus.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SasVerificationState(
	val peerUuid: String,
	val endpointId: String,
	val sasCode: String
)

object PeerRepository
{
	private val _peers = MutableStateFlow<List<Peer>>(emptyList())
	val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

	private val _activeSasVerification = MutableStateFlow<SasVerificationState?>(null)
	val activeSasVerification: StateFlow<SasVerificationState?> = _activeSasVerification.asStateFlow()

	fun showSASVerification(peerUuid: String, endpointId: String, sasCode: String)
	{
		_activeSasVerification.value = SasVerificationState(peerUuid, endpointId, sasCode)
	}

	fun clearSasVerification()
	{
		_activeSasVerification.value = null
	}

	fun addOrUpdatePeer(peer: Peer)
	{
		_peers.update { currentList ->
			val index = currentList.indexOfFirst { it.uuid == peer.uuid }
			if (index != -1)
			{
				currentList.toMutableList().apply { set(index,peer) }
			}
			else
			{
				currentList + peer
			}
		}
	}

	fun removePeerByUUID(uuid: String)
	{
		_peers.update { currentList ->
			currentList.filterNot { it.uuid == uuid }
		}
	}

	fun setPeerVerified(uuid: String, isVerified: Boolean)
	{
		_peers.update { currentList ->
			currentList.map { peer->
				if (peer.uuid == uuid)
				{
					peer.copy(isVerified = isVerified)
				}
				else
				{
					peer
				}
			}
		}
	}

	fun updatePeerDisplayName(uuid: String, newName: String)
	{
		_peers.update { currentList ->
			currentList.map { peer ->
				if (peer.uuid == uuid)
				{
					peer.copy(displayName = newName, isVerified = true)
				}
				else
				{
					peer
				}
			}
		}
	}
}