package com.example.gbsw.shared.model

/**
 * [Model] 곡 목록을 제공하는 곳.
 * 지금은 임시 더미 데이터만 반환한다. 나중에 파일/서버에서 가져오도록 이 클래스만 바꾸면
 * ViewModel과 View는 수정하지 않아도 된다.
 */
class SongRepository {

    fun getSongs(): List<Song> = listOf(
        Song(id = "1", title = "Sample Song 1", artist = "Artist A"),
        Song(id = "2", title = "Sample Song 2", artist = "Artist B"),
        Song(id = "3", title = "Sample Song 3", artist = "Artist C"),
    )
}
