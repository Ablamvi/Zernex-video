package com.zernex.video.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    suspend fun getAll(): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Long>)

    @Query("UPDATE videos SET favorite = CASE WHEN favorite = 1 THEN 0 ELSE 1 END WHERE id=:id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE videos SET lastPositionMs=:positionMs WHERE id=:id")
    suspend fun setPosition(id: Long, positionMs: Long)

    @Query("UPDATE videos SET playCount=playCount+1,lastPlayed=:played,lastPositionMs=:positionMs WHERE id=:id")
    suspend fun markPlayed(id: Long, played: Long, positionMs: Long)

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun count(): Int
}
