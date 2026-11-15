package com.example.gbsw.shared.view

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.gbsw.shared.service.AppRuntime
import com.example.gbsw.shared.service.WarningSelectionScreen
import kotlinx.coroutines.launch

/** 휴게소 선택 중에는 경고로 화면을 빼앗지 않는다. 그 외 흐름은 확인 후 복귀한다. */
interface WarningResume { fun recreate(): Screen }

abstract class RuntimeScreen(context: CarContext, protected val runtime: AppRuntime) : Screen(context) {
    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runtime.updates.collect {
                    val warning = runtime.store.nextWarning()
                    if (warning != null && this@RuntimeScreen is WarningResume && screenManager.top === this@RuntimeScreen) {
                        val origin = this@RuntimeScreen as WarningResume
                        runtime.resumeAfterWarning = { screenManager.push(origin.recreate()) }
                        screenManager.popToRoot()
                    } else {
                        if (this@RuntimeScreen is MyCarAppScreen && !isChoosingPlace) {
                            runtime.feedback.present(warning)
                        }
                        invalidate()
                    }
                }
            }
        }
    }
}
