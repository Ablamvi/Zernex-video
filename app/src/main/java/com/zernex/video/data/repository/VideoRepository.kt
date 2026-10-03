package com.zernex.video.data.repository

import com.zernex.video.core.database.*
import com.zernex.video.data.datasource.MediaStoreDataSource
import com.zernex.video.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VideoRepository(private val source:MediaStoreDataSource,private val db:AppDatabase){
    val videos:Flow<List<VideoItem>> = db.videoDao().observeAll().map{it.map(::toDomain)}
    suspend fun refresh(){ val fresh=source.scan();val old=db.videoDao().getAll().associateBy{it.id};val merged=fresh.map{f->f.copy(favorite=old[f.id]?.favorite?:false,lastPositionMs=old[f.id]?.lastPositionMs?:0L,playCount=old[f.id]?.playCount?:0,lastPlayed=old[f.id]?.lastPlayed?:0L,seriesId=old[f.id]?.seriesId)};db.videoDao().upsertAll(merged); if (merged.isNotEmpty()) db.videoDao().deleteMissing(merged.map{it.id}) else db.videoDao().getAll().takeIf{it.isNotEmpty()}?.let{ /* keep DB stable when the MediaStore query is temporarily empty */ } }
    suspend fun toggleFavorite(id:Long)=db.videoDao().toggleFavorite(id)
    suspend fun setPosition(id:Long,pos:Long)=db.videoDao().setPosition(id,pos)
    suspend fun markPlayed(id:Long,pos:Long)=db.videoDao().markPlayed(id,System.currentTimeMillis(),pos)
    suspend fun count():Int=db.videoDao().count()
    private fun toDomain(e:VideoEntity)=VideoItem(e.id,android.net.Uri.parse(e.uri),e.name,e.mimeType,e.relativePath,e.sizeBytes,e.durationMs,e.dateAdded,e.dateModified,e.width,e.height,e.fps,e.bitrate,e.favorite,e.lastPositionMs,e.playCount,e.lastPlayed,e.seriesId)
}
