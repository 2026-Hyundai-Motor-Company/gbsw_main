package com.example.gbsw.shared.model

/**
 * [Model] 곡 하나를 나타내는 데이터 클래스.
 * data class는 값을 담는 용도의 클래스로, equals/toString 등이 자동 생성된다.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
)
