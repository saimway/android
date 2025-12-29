package com.example.sonic.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.sonic.data.model.Song
import com.example.sonic.data.repository.AudioRepository
import com.example.sonic.service.MusicService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val songs: List<Song>) : UiState()
    object PermissionDenied : UiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val repository: AudioRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    fun initializeController() {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, MusicService::class.java)
        )
        mediaControllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                setupPlayerListener()
                // Sync UI with current player state if reconnected
                updateCurrentSongFromPlayer()
                _isPlaying.value = mediaController?.isPlaying == true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromPlayer()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    _progress.value = 0f
                }
            }
        })
    }

    private fun updateCurrentSongFromPlayer() {
        val currentMediaItem = mediaController?.currentMediaItem
        val currentMediaId = currentMediaItem?.mediaId

        if (currentMediaId != null && uiState.value is UiState.Success) {
           val songs = (uiState.value as UiState.Success).songs
           val song = songs.find { it.id.toString() == currentMediaId }
           _currentSong.value = song
        }
    }

    fun loadLocalMusic() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val songs = repository.getAudioFiles()
            _uiState.value = UiState.Success(songs)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            loadLocalMusic()
            initializeController()
        } else {
            _uiState.value = UiState.PermissionDenied
        }
    }

    fun playSong(song: Song) {
        val controller = mediaController ?: return

        // If the same song is clicked, toggle play/pause
        if (controller.currentMediaItem?.mediaId == song.id.toString()) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
            return
        }

        // If it's a new song, we need to prepare the playlist or just play this one.
        // For simplicity, we can set the clicked song and maybe the rest of the list.
        // Or just the single song for now.
        // A better approach for a music player is to set the whole list and seek to the index.

        if (uiState.value is UiState.Success) {
            val songs = (uiState.value as UiState.Success).songs
            val index = songs.indexOfFirst { it.id == song.id }

            if (index != -1) {
                // Check if the playlist is already set and same
                // For simplicity, let's reset the playlist on every click for now unless we implement proper queue management

                val mediaItems = songs.map {
                    MediaItem.Builder()
                        .setMediaId(it.id.toString())
                        .setUri(it.contentUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(it.title)
                                .setArtist(it.artist)
                                .setArtworkUri(it.albumArtUri)
                                .build()
                        )
                        .build()
                }

                controller.setMediaItems(mediaItems, index, 0)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    init {
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    val currentPosition = mediaController?.currentPosition ?: 0L
                    val duration = mediaController?.duration ?: 1L
                    if (duration > 0) {
                        _progress.value = currentPosition.toFloat() / duration.toFloat()
                    }
                }
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }
}
