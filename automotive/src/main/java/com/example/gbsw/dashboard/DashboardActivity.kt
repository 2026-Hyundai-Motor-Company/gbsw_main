package com.example.gbsw.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.gbsw.BuildConfig
import com.example.gbsw.shared.model.DemoScenario
import com.example.gbsw.shared.service.WarningNotifications

class DashboardActivity : ComponentActivity() {
    private lateinit var model: DashboardModel
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.permissionsChanged()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[DashboardModel::class.java]
        handleIntent(intent)
        setContent {
            DashboardApp(model, requestPermissions = ::requestConnection)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    override fun onStart() { super.onStart(); model.setForeground(true) }
    override fun onStop() { model.setForeground(false); super.onStop() }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.permissionsChanged() }
    private fun requestConnection(requested: List<String>) {
        val missing = requested.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        val history = getSharedPreferences("permission_requests", MODE_PRIVATE)
        val blocked = missing.any { history.getBoolean(it, false) && !shouldShowRequestPermissionRationale(it) }
        if (missing.isEmpty() || blocked) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            history.edit().apply { missing.forEach { putBoolean(it, true) } }.apply()
            permissions.launch(missing.toTypedArray())
        }
    }
    private fun handleIntent(intent: Intent) {
        if (intent.action == WarningNotifications.OPEN_WARNING) model.select(DashboardTab.DRIVE)
        // Explicit debug launch arguments make design QA reproducible without production shortcuts.
        if (!BuildConfig.DEBUG) return
        if (intent.getBooleanExtra("demo", false)) model.startDemo()
        intent.getStringExtra("tab")?.let { name -> DashboardTab.entries.firstOrNull { it.name == name }?.let(model::select) }
        intent.getStringExtra("scenario")?.let { name -> DemoScenario.entries.firstOrNull { it.name == name }?.let(model::setScenario) }
        if (intent.hasExtra("minutes")) model.store.setDemoDuration(intent.getIntExtra("minutes", 72))
        if (intent.hasExtra("speed")) model.store.setSpeed(intent.getIntExtra("speed", 85))
        if (intent.hasExtra("drowsy")) model.store.setDrowsy(intent.getBooleanExtra("drowsy", false))
    }
}
