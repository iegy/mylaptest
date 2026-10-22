package net.iegy.hisn.data

import kotlinx.coroutines.flow.Flow

class DownloadsRepository(private val dao: DownloadDao) {
    fun observe(): Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun add(entity: DownloadEntity): Long = dao.insert(entity)

    suspend fun delete(id: Long) = dao.delete(id)
}
