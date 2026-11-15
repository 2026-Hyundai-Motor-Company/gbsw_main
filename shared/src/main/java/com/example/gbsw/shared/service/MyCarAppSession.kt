package com.example.gbsw.shared.service

import com.example.gbsw.shared.view.MyCarAppScreen

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MyCarAppSession : Session() {
    private var runtime: AppRuntime? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val active = runtime ?: AppRuntime(carContext, lifecycle).also { runtime = it }
        return MyCarAppScreen(carContext, active)
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.action == WarningNotifications.OPEN_WARNING) {
            val manager = carContext.getCarService(androidx.car.app.ScreenManager::class.java)
            val origin = runCatching { manager.top }.getOrNull()
            if (origin is com.example.gbsw.shared.view.WarningResume) {
                runtime?.resumeAfterWarning = { manager.push(origin.recreate()) }
            }
            manager.popToRoot()
            runtime?.changed()
        }
    }
}
