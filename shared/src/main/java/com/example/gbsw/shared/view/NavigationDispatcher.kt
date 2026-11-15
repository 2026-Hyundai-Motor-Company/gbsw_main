package com.example.gbsw.shared.view

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.HostException
import com.example.gbsw.shared.model.DemoScenario
import com.example.gbsw.shared.model.Place
import com.example.gbsw.shared.service.AppRuntime
import java.net.URLEncoder

class NavigationDispatcher(private val runtime: AppRuntime) {
    fun place(place: Place): String? = launch(Uri.parse("geo:${place.latitude},${place.longitude}"))
    fun calendar(location: String): String? = launch(Uri.parse("geo:0,0?q=" + URLEncoder.encode(location.trim(), "UTF-8")))

    private fun launch(uri: Uri): String? {
        if (runtime.store.uiState.value.isDemo && runtime.scenario == DemoScenario.NAVIGATION_MISSING)
            return "길안내 앱을 찾을 수 없습니다"
        return try {
            runtime.context.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
            null
        } catch (_: ActivityNotFoundException) { nativeAutomotiveFallback(uri)
        } catch (_: HostException) { nativeAutomotiveFallback(uri)
        } catch (_: SecurityException) { "차량에서 길안내 앱을 열 수 없습니다" }
    }

    private fun nativeAutomotiveFallback(uri: Uri): String? {
        if (!runtime.context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
            return "길안내 앱을 찾을 수 없습니다"
        // 일부 AAOS 기본 내비게이션은 CAL 서비스 없이 geo NAVIGATE Activity만 제공한다.
        for (action in listOf("android.intent.action.NAVIGATE", Intent.ACTION_VIEW)) {
            val intent = Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(runtime.context.packageManager) == null) continue
            try {
                runtime.context.startActivity(intent)
                return null
            } catch (_: ActivityNotFoundException) { continue
            } catch (_: SecurityException) { return "차량에서 길안내 앱을 열 수 없습니다" }
        }
        return "길안내 앱을 찾을 수 없습니다"
    }
}
