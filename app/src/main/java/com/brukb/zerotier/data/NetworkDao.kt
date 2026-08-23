package com.brukb.zerotier.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.brukb.zerotier.data.model.ZerotierBNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Query("SELECT * FROM networks ORDER BY name")
    fun observeAll(): Flow<List<ZerotierBNetwork>>

    @Query("SELECT * FROM networks WHERE isEnabled = 1 ORDER BY name")
    fun observeEnabled(): Flow<List<ZerotierBNetwork>>

    @Query("SELECT * FROM networks")
    suspend fun getAll(): List<ZerotierBNetwork>

    @Query("SELECT * FROM networks WHERE networkId = :networkId LIMIT 1")
    suspend fun getById(networkId: String): ZerotierBNetwork?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: ZerotierBNetwork)

    @Update
    suspend fun update(network: ZerotierBNetwork)

    @Query("DELETE FROM networks WHERE networkId = :networkId")
    suspend fun delete(networkId: String)

    @Query("UPDATE networks SET isPinnedMain = 0")
    suspend fun clearPinnedMain()

    @Query("UPDATE networks SET isPinnedMain = 1 WHERE networkId = :networkId")
    suspend fun pinMain(networkId: String)

    @Transaction
    suspend fun setPinnedMain(networkId: String) {
        clearPinnedMain()
        pinMain(networkId)
    }
}
