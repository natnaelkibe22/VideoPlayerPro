package com.natkibe.videoplayerpro.favorites

import com.natkibe.videoplayerpro.data.PinnedFolderEntity
import com.natkibe.videoplayerpro.data.VideoDao
import kotlinx.coroutines.flow.Flow

class PinnedFolderRepository(private val dao: VideoDao) {

    fun observePinnedFolders(): Flow<List<PinnedFolderEntity>> = dao.observePinnedFolders()

    suspend fun addPinnedFolder(folderName: String) {
        dao.addPinnedFolder(PinnedFolderEntity(folderName = folderName))
    }

    suspend fun removePinnedFolder(folderName: String) {
        dao.removePinnedFolder(folderName)
    }

    suspend fun setPinned(folderName: String, pinned: Boolean) {
        if (pinned) {
            addPinnedFolder(folderName)
        } else {
            removePinnedFolder(folderName)
        }
    }
}
