package com.termius.clone.data.local

import androidx.room.*
import com.termius.clone.data.model.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY name ASC")
    fun getAllIdentities(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getIdentityById(id: Long): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: IdentityEntity): Long

    @Update
    suspend fun updateIdentity(identity: IdentityEntity)

    @Delete
    suspend fun deleteIdentity(identity: IdentityEntity)
}
