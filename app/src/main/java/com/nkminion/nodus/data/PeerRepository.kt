package com.nkminion.nodus.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PeerRepository
{
	private val _peers = MutableStateFlow<List<Peer>>(emptyList())
	val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

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
}