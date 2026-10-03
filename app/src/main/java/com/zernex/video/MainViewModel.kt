package com.zernex.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zernex.video.core.database.AppDatabase
import com.zernex.video.data.datasource.MediaStoreDataSource
import com.zernex.video.data.repository.VideoRepository
import com.zernex.video.domain.model.VideoItem
import com.zernex.video.domain.usecase.SeriesGrouper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = VideoRepository(MediaStoreDataSource(app.contentResolver), db)

    val videos = repo.videos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val series = videos.map(SeriesGrouper::groupBySeries)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            runCatching { repo.refresh() }
                .onFailure { _message.value = it.message ?: "Erreur d'indexation" }
            _loading.value = false
        }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch { repo.toggleFavorite(video.id) }
    }

    fun savePosition(video: VideoItem, positionMs: Long) {
        viewModelScope.launch { repo.setPosition(video.id, positionMs.coerceAtLeast(0L)) }
    }

    fun markPlayed(video: VideoItem, positionMs: Long) {
        viewModelScope.launch { repo.markPlayed(video.id, positionMs.coerceAtLeast(0L)) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
