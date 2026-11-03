package com.example.gbsw.shared.view

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.example.gbsw.shared.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * [View] 차량 화면. 곡 목록을 보여주고, 선택은 ViewModel에 전달만 한다.
 *
 * Car App의 Screen은 일반 ViewModelStoreOwner가 아니라서 ViewModel을 직접 만들어 쓴다.
 * 상태가 바뀌면 invalidate()로 화면을 다시 그리도록 요청한다.
 */
class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {

    private val viewModel = PlayerViewModel()

    init {
        // 화면이 살아있는 동안 상태 변화를 구독한다.
        lifecycleScope.launch {
            viewModel.uiState.collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val state = viewModel.uiState.value

        val list = ItemList.Builder()
        state.songs.forEach { song ->
            list.addItem(
                Row.Builder()
                    .setTitle(song.title)
                    .addText(song.artist)
                    .setOnClickListener { viewModel.onSongSelected(song) }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle(state.currentSong?.title ?: "GBSW")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
