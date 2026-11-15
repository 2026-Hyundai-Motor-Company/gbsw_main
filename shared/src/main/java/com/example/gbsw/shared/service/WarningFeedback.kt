package com.example.gbsw.shared.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.example.gbsw.shared.model.DrivingWarning

/** 작은 아이콘과 낮은 음량의 짧은 경고만 사용한다. 볼륨·무음 설정을 바꾸지 않는다. */
class WarningFeedback(context: Context) {
    private val preferences = context.getSharedPreferences("warning_preferences", Context.MODE_PRIVATE)
    var soundEnabled: Boolean
        get() = preferences.getBoolean("sound", true)
        set(value) { preferences.edit().putBoolean("sound", value).apply() }
    var ghostEnabled: Boolean
        get() = preferences.getBoolean("ghost", true)
        set(value) { preferences.edit().putBoolean("ghost", value).apply() }
    private var presented: DrivingWarning? = null
    private var tone: ToneGenerator? = null

    fun present(warning: DrivingWarning?) {
        if (warning == null) { presented = null; return }
        if (warning == presented) return
        presented = warning
        if (!soundEnabled) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 25)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        } catch (_: RuntimeException) { /* 오디오 장치가 없어도 화면 경고는 유지한다. */ }
    }

    fun close() { tone?.release(); tone = null }
}
