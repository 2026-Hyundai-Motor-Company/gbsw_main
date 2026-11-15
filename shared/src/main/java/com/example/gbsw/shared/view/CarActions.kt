package com.example.gbsw.shared.view

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat

internal fun carIcon(context: CarContext, drawable: Int): CarIcon {
    // 픽셀을 전달하면 호스트가 APK 갱신 전의 drawable ID 캐시를 재사용하지 않는다.
    val image = requireNotNull(ContextCompat.getDrawable(context, drawable))
    return CarIcon.Builder(IconCompat.createWithBitmap(image.toBitmap(128, 128))).build()
}

internal fun primaryAction(context: CarContext, title: String, icon: Int, onClick: () -> Unit): Action =
    Action.Builder().setTitle(title).setIcon(carIcon(context, icon))
        .setBackgroundColor(CarColor.BLUE).setOnClickListener { onClick() }
        .apply { if (context.carAppApiLevel >= 4) setFlags(Action.FLAG_PRIMARY) }.build()
