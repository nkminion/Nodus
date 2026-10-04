package com.nkminion.nodus.dao

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nkminion.nodus.entity.MessageEntity

@Dao
interface MessageDao
{
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertMessage(message: MessageEntity)

	@Query("SELECT * FROM messages WHERE senderId = :peerId OR recipientId = :peerId")
	fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>>
}