package com.natkibe.videoplayerpro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.natkibe.videoplayerpro.model.VideoFolderSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_items ORDER BY folderName COLLATE NOCASE, displayName COLLATE NOCASE")
    fun observeVideos(): Flow<List<VideoItemEntity>>

    @Query("SELECT folderName, storageRoot, COUNT(*) AS videoCount, MAX(dateModified) AS latestModified FROM video_items GROUP BY folderName, storageRoot ORDER BY folderName COLLATE NOCASE")
    fun observeFolders(): Flow<List<VideoFolderSummary>>

    @Query("SELECT * FROM video_items WHERE folderName = :folderName ORDER BY displayName COLLATE NOCASE")
    fun observeVideosInFolder(folderName: String): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM video_items WHERE folderName = :folderName ORDER BY displayName COLLATE NOCASE")
    suspend fun videosInFolder(folderName: String): List<VideoItemEntity>

    @Query("SELECT * FROM video_items WHERE uri = :uri LIMIT 1")
    suspend fun videoByUri(uri: String): VideoItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<VideoItemEntity>)

    @Query("DELETE FROM video_items")
    suspend fun clearVideos()

    @Transaction
    suspend fun replaceVideos(videos: List<VideoItemEntity>) {
        clearVideos()
        upsertVideos(videos)
    }

    @Query("SELECT * FROM video_progress WHERE videoUri = :uri")
    suspend fun getProgress(uri: String): VideoProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: VideoProgressEntity)

    @Query("SELECT v.* FROM video_items v INNER JOIN video_progress p ON v.uri = p.videoUri ORDER BY p.updatedAt DESC LIMIT :limit")
    fun observeRecentlyWatched(limit: Int = 20): Flow<List<VideoItemEntity>>

    // ── Favorites ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorite_videos WHERE videoUri = :uri")
    suspend fun removeFavorite(uri: String)

    @Query("SELECT COUNT(*) FROM favorite_videos WHERE videoUri = :uri")
    suspend fun isFavorite(uri: String): Int

    @Query("SELECT v.* FROM video_items v INNER JOIN favorite_videos f ON v.uri = f.videoUri ORDER BY f.addedAt DESC")
    fun observeFavorites(): Flow<List<VideoItemEntity>>

    // ── Pinned folders ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPinnedFolder(folder: PinnedFolderEntity)

    @Query("DELETE FROM pinned_folders WHERE folderName = :folderName")
    suspend fun removePinnedFolder(folderName: String)

    @Query("SELECT * FROM pinned_folders ORDER BY pinnedAt ASC")
    fun observePinnedFolders(): Flow<List<PinnedFolderEntity>>
}
