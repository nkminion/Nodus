package com.nkminion.nodus.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nkminion.nodus.dao.MessageDao
import com.nkminion.nodus.dao.NodeDao
import com.nkminion.nodus.entity.MessageEntity
import com.nkminion.nodus.entity.NodeEntity

@Database(entities = [NodeEntity::class, MessageEntity::class], version=1)
abstract class NodusDatabase : RoomDatabase()
{
	abstract fun nodeDao() : NodeDao
	abstract fun messageDao() : MessageDao

	companion object
	{
		@Volatile
		private var DB_INSTANCE: NodusDatabase? = null

		fun getDBInstance(context: Context): NodusDatabase
		{
			return DB_INSTANCE ?: synchronized(this)
			{
				val instance = Room.databaseBuilder(
					context.applicationContext,
					NodusDatabase::class.java,
					"NodusDatabase"
				).build()
				DB_INSTANCE = instance
				instance
			}
		}
	}
}