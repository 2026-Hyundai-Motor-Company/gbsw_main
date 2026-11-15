package com.example.gbsw.dashboard

import androidx.compose.runtime.staticCompositionLocalOf

/** Shared viewport measurements keep content, actions and navigation in one layout. */
data class DashboardLayout(val height: Float = 800f, val fontScale: Float = 1f) {
    val headerTop = 24f
    val headerHeight = maxOf(64f, 48f * fontScale)
    val contentTop = headerTop + headerHeight + 24f
    val navigationHeight = maxOf(84f, 42f * fontScale + 12f)
    val navigationTop = height - 24f - navigationHeight
    val contentHeight = navigationTop - 24f - contentTop
    val overlayHeight = height - contentTop - 24f
    val largeText = fontScale >= 1.5f
}

val LocalDashboardLayout = staticCompositionLocalOf { DashboardLayout() }
