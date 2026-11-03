package com.example.gbsw.shared.viewmodel

import androidx.lifecycle.ViewModel
import com.example.gbsw.shared.model.Song
import com.example.gbsw.shared.model.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ViewModel] 화면에 보여줄 상태를 들고 있고, 화면에서 오는 동작을 처리한다.
 *
 * - View(Screen)는 [uiState]를 읽기만 한다.
 * - 상태를 바꾸는 건 이 클래스 안에서만 한다. (_uiState는 private)
 */
class PlayerViewModel(
    private val repository: SongRepository = SongRepository(),
) : ViewModel() {

    /** 화면 상태. 값이 바뀌면 구독 중인 View가 알림을 받는다. */
    data class UiState(
        val songs: List<Song> = emptyList(),
        val currentSong: Song? = null,
    )

    // 바꿀 수 있는 버전은 안에서만, 밖에는 읽기 전용 버전만 공개한다.
    private val _uiState = MutableStateFlow(UiState(songs = repository.getSongs()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 곡을 선택했을 때 호출. 실제 재생은 나중에 연결한다. */
    fun onSongSelected(song: Song) {
        _uiState.value = _uiState.value.copy(currentSong = song)
    }
}
