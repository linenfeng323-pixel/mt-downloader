package com.linenfeng.mtdownloader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_task ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_task WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM download_task WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM download_task WHERE status IN (:statuses)")
    suspend fun getByStatus(statuses: List<Int>): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Delete
    suspend fun delete(entity: DownloadEntity)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_task WHERE status = :status")
    suspend fun deleteByStatus(status: Int)

    @Query("UPDATE download_task SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE download_task SET downloaded_size = :downloaded, total_size = :total, blocks_text = :blocks, updated_at = :now WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        downloaded: Long,
        total: Long,
        blocks: String,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE download_task SET error_msg = :error, status = :status, retries = :retries, updated_at = :now WHERE id = :id")
    suspend fun updateError(id: Long, error: String?, status: Int, retries: Int, now: Long = System.currentTimeMillis())
}
