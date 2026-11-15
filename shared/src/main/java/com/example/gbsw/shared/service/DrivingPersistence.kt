package com.example.gbsw.shared.service

import android.content.Context
import com.example.gbsw.shared.model.DrivingWarning
import com.example.gbsw.shared.viewmodel.DrivingSessionStore

class DrivingPersistence(context: Context) {
    private val prefs = context.getSharedPreferences("driving_session", Context.MODE_PRIVATE)
    fun createStore(demoAvailable: Boolean): DrivingSessionStore {
        val elapsedWall = (System.currentTimeMillis() - prefs.getLong("saved_at", 0)).coerceAtLeast(0)
        val cooldowns = DrivingWarning.entries.associateWith {
            (prefs.getLong("cooldown_${it.name}", 0) - elapsedWall).coerceAtLeast(0)
        }
        return DrivingSessionStore(demoAvailable = demoAvailable,
            initialDrivingMillis = prefs.getLong("driving_millis", 0), initialCooldowns = cooldowns)
    }

    fun save(store: DrivingSessionStore) {
        prefs.edit().putLong("saved_at", System.currentTimeMillis())
            .putLong("driving_millis", store.persistedDrivingMillis()).apply {
                store.remainingRealCooldowns().forEach { (warning, remaining) ->
                    putLong("cooldown_${warning.name}", remaining)
                }
            }.apply()
    }
}
