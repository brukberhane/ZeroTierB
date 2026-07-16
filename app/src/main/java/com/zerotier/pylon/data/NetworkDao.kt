package com.zerotier.pylon.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zerotier.pylon.data.model.PylonNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Query("SELECT * FROM networks ORDER BY networkId ASC")
    fun observeAll(): Flow<List<PylonNetwork>>

    @Query("SELECT * FROM networks WHERE isEnabled = 1 ORDER BY networkId ASC")
    fun observeEnabled(): Flow<List<PylonNetwork>>

    @Query("SELECT * FROM networks WHERE networkId = :networkId LIMIT 1")
    suspend fun getById(networkId: String): PylonNetwork?

    @Query("SELECT * FROM networks")
    suspend fun getAll(): List<PylonNetwork>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: PylonNetwork)

    @Update
    suspend fun update(network: PylonNetwork)

    @Query("DELETE FROM networks WHERE networkId = :networkId")
    suspend fun delete(networkId: String)
}
