package com.example.gbsw.shared.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.ScreenManager
import androidx.lifecycle.Lifecycle
import com.example.gbsw.shared.R
import com.example.gbsw.shared.model.DrivingWarning
import com.example.gbsw.shared.viewmodel.DrivingSessionStore

/** 이 마커가 있는 장소 선택 화면이 전경이면 새 경고를 잠시 미룬다. */
interface WarningSelectionScreen { val isChoosingPlace: Boolean get() = true }

class WarningNotifications(
    private val context: Context,
    private val store: DrivingSessionStore,
    private val feedback: WarningFeedback,
    private val activityClass: String = "androidx.car.app.activity.CarAppActivity",
    private val selecting: (() -> Boolean)? = null,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var shown: DrivingWarning? = null
    private val action = context.packageName + ".DISMISS_DRIVING_WARNING"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getStringExtra("warning") ?: return
            val warning = DrivingWarning.entries.firstOrNull { it.name == type } ?: return
            store.acknowledge(warning)
            update(null)
        }
    }

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "운전과 휴식 알림",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "장시간 운전, 졸음, 과속을 알리고 휴게소 안내로 연결해요"
            setSound(null, null)
            enableVibration(false)
        })
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
    }

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun update(warning: DrivingWarning?) {
        if (warning == null) {
            if (shown != null) manager.cancel(ID)
            shown = null
            feedback.present(null)
            return
        }
        val selectingPlace = try {
            selecting?.invoke() ?: ((context as? CarContext)?.getCarService(ScreenManager::class.java)?.top?.let { top ->
                top is WarningSelectionScreen && top.isChoosingPlace && top.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            } ?: false)
        } catch (_: RuntimeException) { false }
        if (selectingPlace) return
        feedback.present(warning)
        if (warning == shown) return
        if (!hasPermission()) return
        val open = Intent().setComponent(ComponentName(context.packageName,
            activityClass)).setAction(OPEN_WARNING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, 100, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(context, warning.ordinal,
            Intent(action).setPackage(context.packageName).putExtra("warning", warning.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(if (feedback.ghostEnabled) R.drawable.ic_ghost else R.drawable.ic_car)
            .setContentTitle(warning.title).setContentText(warning.message)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setContentIntent(content).setDeleteIntent(dismiss)
            .addAction(Notification.Action.Builder(null, "앱에서 확인", content).build())
            .addAction(Notification.Action.Builder(null, "확인", dismiss).build())
            .setOnlyAlertOnce(true).setAutoCancel(false).build()
        try { manager.notify(ID, notification); shown = warning } catch (_: SecurityException) { }
    }

    fun close() {
        manager.cancel(ID)
        context.unregisterReceiver(receiver)
    }

    companion object {
        const val OPEN_WARNING = "com.example.gbsw.OPEN_WARNING"
        private const val CHANNEL = "driving_warnings"
        private const val ID = 41
    }
}
