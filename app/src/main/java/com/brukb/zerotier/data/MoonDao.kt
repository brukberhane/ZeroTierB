package com.brukb.zerotier.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brukb.zerotier.data.model.Moon
import kotlinx.coroutines.flow.Flow

@Dao
interface MoonDao {
    @Query("SELECT * FROM moons ORDER BY createdAt ASC, worldId ASC")
    fun observeAll(): Flow<List<Moon>>

    @Query("SELECT * FROM moons ORDER BY createdAt ASC, worldId ASC")
    suspend fun getAll(): List<Moon>

    @Query("SELECT * FROM moons WHERE worldId = :worldId LIMIT 1")
    suspend fun getById(worldId: String): Moon?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(moon: Moon)

    @Query("DELETE FROM moons WHERE worldId = :worldId")
    suspend fun delete(worldId: String)

    @Query("SELECT COUNT(*) FROM moons")
    suspend fun count(): Int
}
