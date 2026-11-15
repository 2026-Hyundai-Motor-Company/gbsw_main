package com.example.gbsw.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import kotlin.math.ceil
import com.caverock.androidsvg.SVG
import com.example.gbsw.R

/** Tokens from Figma page 150:2954, home frames 150:3211 and 150:3219. */
data class DesignColors(val night: Boolean) {
    val background = if (night) Color(0xFF131417) else Color(0xFFF3F5F7)
    val card = if (night) Color(0xFF1D2026) else Color.White
    val border = if (night) Color(0xFF30343D) else Color(0xFFE3E7ED)
    val text = if (night) Color.White else Color(0xFF202630)
    val secondary = if (night) Color(0xFFC7C9CE) else Color(0xFF626C79)
    val surface = if (night) Color(0xFF313236) else Color(0xFFF0F3F7)
    val accentSurface = if (night) Color(0xFF243248) else Color(0xFFEDF3FF)
    val accent = Color(0xFF0064FF)
    val link = if (night) Color(0xFF93BFFF) else accent
    val glass = if (night) Color(0x592A303C) else Color(0x73FFFFFF)
    val selected = if (night) Color(0x453F74BE) else Color(0x2E3F87F5)
}
val LocalDesign = staticCompositionLocalOf { DesignColors(false) }
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val KoreanFont = FontFamily(
    Font(R.font.noto_sans_kr, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_kr, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

@Composable
fun Copy(value: String, size: Int = 24, medium: Boolean = false, color: Color = LocalDesign.current.text,
         modifier: Modifier = Modifier, lines: Int = 1) {
    Text(value, modifier, color, fontSize = size.sp, fontFamily = KoreanFont,
        fontWeight = if (medium) FontWeight.Medium else FontWeight.Normal,
        lineHeight = (ceil(size * (if (size >= 32) 1.25f else 1.33f) / 4f) * 4f).sp,
        maxLines = lines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(letterSpacing = (if (size >= 28) (-.018f) else (-.008f)).em,
            platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
fun DesignCard(modifier: Modifier = Modifier, padding: Int = 0, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalDesign.current
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(colors.card)
        .padding(padding.dp), content = content)
}

@Composable
fun DesignButton(label: String, modifier: Modifier = Modifier, primary: Boolean = true, height: Int = 64,
                 size: Int = 24, onClick: () -> Unit) {
    val c = LocalDesign.current
    Box(modifier.heightIn(min = height.dp).clip(RoundedCornerShape(14.dp))
        .background(if (primary) c.accent else c.surface).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Copy(label, size, true, if (primary) Color.White else c.text, lines = 2)
    }
}

@Composable
fun QuietButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Copy(label, 24, color = LocalDesign.current.link)
    }
}

/** Render the downloaded Figma SVG intact, retaining its native aspect ratio and colors. */
@Composable
fun FigmaIcon(asset: String, size: Int, modifier: Modifier = Modifier, description: String? = null, tint: Color? = null) {
    val context = LocalContext.current
    val bitmap = remember(asset) {
        val svg = SVG.getFromAsset(context.assets, "figma/$asset")
        val width = svg.documentWidth.takeIf { it > 0 } ?: 32f
        val height = svg.documentHeight.takeIf { it > 0 } ?: 32f
        Bitmap.createBitmap((width * 3).toInt(), (height * 3).toInt(), Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it); canvas.scale(3f, 3f); svg.renderToCanvas(canvas)
        }.asImageBitmap()
    }
    Image(bitmap, description, modifier.size(size.dp), colorFilter = tint?.let(ColorFilter::tint))
}

fun iconAsset(tab: DashboardTab, selected: Boolean, night: Boolean): String = if (!night) {
    when (tab) {
        DashboardTab.DRIVE -> if (selected) "150-3219/imgIconCar.svg" else "150-3283/imgIconCar.svg"
        DashboardTab.REST -> if (selected) "150-3283/imgIconCoffee.svg" else "150-3219/imgIconCoffee1.svg"
        DashboardTab.SIGHTS -> if (selected) "150-3230/imgIconPin.svg" else "150-3219/imgIconPin.svg"
        DashboardTab.CALENDAR -> if (selected) "150-3233/imgIconCal.svg" else "150-3219/imgIconCal1.svg"
    }
} else {
    when (tab) {
        DashboardTab.DRIVE -> if (selected) "night/car_selected.svg" else "150-3227/imgIconCar.svg"
        DashboardTab.REST -> if (selected) "150-3227/imgIconCoffee.svg" else "night/coffee_inactive.svg"
        DashboardTab.SIGHTS -> if (selected) "150-3230/imgIconPin.svg" else "night/pin.svg"
        DashboardTab.CALENDAR -> if (selected) "150-3233/imgIconCal.svg" else "night/calendar_inactive.svg"
    }
}
