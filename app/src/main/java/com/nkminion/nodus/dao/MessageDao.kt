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

	@Query("SELECT * FROM messages WHERE senderId = :peerId OR recipientId = :peerId ORDER BY timestamp ASC")
	fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

	@Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
	suspend fun updateMessageStatus(messageId: String, status: String)

	@Query("""
		SELECT CASE WHEN senderId = :myUuid THEN recipientId ELSE senderId END AS peerId
		FROM messages
		WHERE senderId = :myUuid OR recipientId = :myUuid
		GROUP BY peerId
		ORDER BY MAX(timestamp) DESC
	""")
	fun getMessagedPeerIds(myUuid: String): Flow<List<String>>
}