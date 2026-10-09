package app.auralis

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.auralis.extensions.Skin
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

internal val accents = listOf(Color(0xFF69E0BE), Color(0xFFFF929D), Color(0xFF8FD8FF), Color(0xFFF5DB75), Color(0xFFFF647C))

internal val LocalSkin = staticCompositionLocalOf { Skin() }

@Composable
internal fun AuralisTheme(accent: Int, customSkin: Skin? = null, content: @Composable () -> Unit) {
    val skin = customSkin ?: Skin()
    fun color(value: String) = Color(android.graphics.Color.parseColor(value))
    val primary = customSkin?.let { color(it.primary) } ?: accents[accent.coerceIn(accents.indices)]
    val family = when(skin.font) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val base = Typography()
    fun style(value: TextStyle): TextStyle = value.copy(fontFamily = family, fontSize = value.fontSize * skin.textScale, lineHeight = value.lineHeight * skin.textScale)
    val typography = Typography(
        displayLarge = style(base.displayLarge), displayMedium = style(base.displayMedium), displaySmall = style(base.displaySmall),
        headlineLarge = style(base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp)),
        headlineMedium = style(base.headlineMedium), headlineSmall = style(base.headlineSmall),
        titleLarge = style(base.titleLarge), titleMedium = style(base.titleMedium), titleSmall = style(base.titleSmall),
        bodyLarge = style(base.bodyLarge), bodyMedium = style(base.bodyMedium), bodySmall = style(base.bodySmall),
        labelLarge = style(base.labelLarge), labelMedium = style(base.labelMedium), labelSmall = style(base.labelSmall))
    val onPrimary = if(androidx.core.graphics.ColorUtils.calculateLuminance(android.graphics.Color.parseColor(customSkin?.primary ?: "#FF647C")) > .179) Color.Black else Color.White
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = primary, onPrimary = onPrimary, secondary = primary,
            background = color(skin.background), surface = color(skin.background),
            surfaceContainer = color(skin.surface), surfaceContainerHigh = color(skin.surface),
            onSurface = color(skin.text), onBackground = color(skin.text), onSurfaceVariant = color(skin.muted),
            outline = color(skin.muted).copy(alpha = .5f), outlineVariant = color(skin.muted).copy(alpha = .2f),
        ), typography = typography, shapes = Shapes(
            extraSmall = RoundedCornerShape((skin.cornerRadius/3).dp), small = RoundedCornerShape((skin.cornerRadius/2).dp),
            medium = RoundedCornerShape(skin.cornerRadius.dp), large = RoundedCornerShape(skin.cornerRadius.dp), extraLarge = RoundedCornerShape(skin.cornerRadius.dp)), content = content)
    }
}

/** A static 64px software blur, cached by Coil per cover, works on API 26 too. */
internal class SoftArtwork : Transformation {
    override val cacheKey = "auralis-soft-artwork-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val small = Bitmap.createScaledBitmap(input, 64, 64, true)
        var pixels = IntArray(4096).also { small.getPixels(it, 0, 64, 0, 0, 64, 64) }
        if (small !== input) small.recycle()
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                val result = IntArray(4096)
                for (y in 0 until 64) for (x in 0 until 64) {
                    var red = 0; var green = 0; var blue = 0
                    for (offset in -4..4) {
                        val px = if (horizontal) (x + offset).coerceIn(0, 63) else x
                        val py = if (horizontal) y else (y + offset).coerceIn(0, 63)
                        val value = pixels[py * 64 + px]
                        red += value shr 16 and 255; green += value shr 8 and 255; blue += value and 255
                    }
                    result[y * 64 + x] = (255 shl 24) or ((red / 9) shl 16) or ((green / 9) shl 8) or (blue / 9)
                }
                pixels = result
            }
        }
        return Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888)
    }
}

@Composable
internal fun ArtworkAtmosphere(uri: Uri?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val visibleUri = if(LocalSkin.current.atmosphere) uri else null
    val request = remember(visibleUri, context) {
        ImageRequest.Builder(context).data(visibleUri).size(96).allowHardware(false).transformations(SoftArtwork()).build()
    }
    val backdrop = if(LocalSkin.current.atmosphere) listOf(Color(0xFF49313F), Color(0xFF181620), Color(0xFF0E0E14)) else listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
    Box(modifier.background(Brush.verticalGradient(backdrop))) {
        if (visibleUri != null) AsyncImage(request, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .5f)
        if(LocalSkin.current.atmosphere) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x33101017), Color(0xA6101017), Color(0xED101017)))))
        content()
    }
}
