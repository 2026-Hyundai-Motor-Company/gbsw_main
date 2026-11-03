package com.example.gbsw.shared.service

import com.example.gbsw.shared.view.MyCarAppScreen

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MyCarAppSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return MyCarAppScreen(carContext)
    }
}