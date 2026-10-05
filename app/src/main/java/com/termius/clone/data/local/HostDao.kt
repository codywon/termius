package com.termius.clone.data.local

import androidx.room.*
import com.termius.clone.data.model.HostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY lastConnected DESC, label ASC")
    fun getAllHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts ORDER BY lastConnected DESC, label ASC")
    suspend fun getAllHostsSync(): List<HostEntity>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getHostById(id: Long): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: HostEntity): Long

    @Update
    suspend fun updateHost(host: HostEntity)

    @Delete
    suspend fun deleteHost(host: HostEntity)

    @Query("UPDATE hosts SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)
}
