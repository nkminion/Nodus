package com.nkminion.nodus.data

data class Peer(
	val displayName: String,
	val uuid: String,
	val isVerified: Boolean,
	val hops: Int
)

val samplePeers = listOf(
	Peer(displayName = "Mambo", uuid = "placeholder 1", isVerified = true, hops = 1),
	Peer(displayName = "Mr. Bombastic", uuid = "placeholder 2", isVerified = true, hops = 2),
	Peer(displayName = "Pengu", uuid = "placeholder 3", isVerified = true, hops = 3),
	Peer(displayName = "Player 1", uuid = "placeholder 4", isVerified = true, hops = 4),
	Peer(displayName = "Duck", uuid = "placeholder 5", isVerified = false, hops = 5)
)