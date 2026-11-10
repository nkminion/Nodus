package com.nkminion.nodus.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
	@PrimaryKey val nodeId: String,
	val publicKey: String? = null,
	val trustStatus: String = "Untrusted",
	val lastSeen: Long = System.currentTimeMillis()
)