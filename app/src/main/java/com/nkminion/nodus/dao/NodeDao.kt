package com.nkminion.nodus.dao

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nkminion.nodus.entity.NodeEntity

@Dao
interface NodeDao
{
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertNode(node: NodeEntity)

	@Query("SELECT * FROM nodes WHERE nodeId = :nodeId LIMIT 1")
	suspend fun getNodeById(nodeId: String) : NodeEntity?

	@Query("SELECT * FROM nodes WHERE trustStatus = 'VerifiedMutual' OR trustStatus = 'VerifiedSAS'")
	fun getVerifiedNodes() : Flow<List<NodeEntity>>
}