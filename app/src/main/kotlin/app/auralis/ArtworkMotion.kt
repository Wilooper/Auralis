package app.auralis

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.auralis.extensions.ArtworkSpec

internal val LocalArtwork = staticCompositionLocalOf<ArtworkSpec?> { null }

@Composable
private fun artworkMotionAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var permitted by remember { mutableStateOf(!power.isPowerSaveMode && ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context, lifecycle) {
        fun refresh() { permitted = !power.isPowerSaveMode && ValueAnimator.areAnimatorsEnabled() }
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); refresh() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settings)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver); context.contentResolver.unregisterContentObserver(settings) }
    }
    return foreground && permitted
}

/** Layer-only motion: the image request and bitmap stay unchanged between frames. */
@Composable
internal fun artworkMotionModifier(playing: Boolean, motion: String): Modifier {
    if (!playing || motion == "none") return Modifier
    val allowed = artworkMotionAllowed()
    if (!allowed) return Modifier
    if (motion == "tilt") {
        var target by remember { mutableStateOf(Offset.Zero) }
        val x = animateFloatAsState(target.x, spring(stiffness = Spring.StiffnessLow), label = "artwork-tilt-x")
        val y = animateFloatAsState(target.y, spring(stiffness = Spring.StiffnessLow), label = "artwork-tilt-y")
        return Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val position = event.changes.firstOrNull()?.position
                    target = if (position == null || event.type == PointerEventType.Exit || event.type == PointerEventType.Release) Offset.Zero
                    else Offset((position.x / size.width.coerceAtLeast(1) * 2 - 1).coerceIn(-1f, 1f),
                        (position.y / size.height.coerceAtLeast(1) * 2 - 1).coerceIn(-1f, 1f))
                    // Observe only: scrolling and accessibility gestures keep their normal ownership.
                }
            }
        }.graphicsLayer { rotationX = -y.value * 6; rotationY = x.value * 6; cameraDistance = 16 * density }
    }
    if (motion == "turntable") {
        val rotation = rememberInfiniteTransition(label = "artwork-turntable").animateFloat(0f, 360f,
            infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "artwork-rotation")
        return Modifier.graphicsLayer { rotationZ = rotation.value }
    }
    val transition = rememberInfiniteTransition(label = "artwork-motion")
    val phase = transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(if (motion == "pulse") 2400 else 6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "artwork-drift")
    return Modifier.graphicsLayer {
        when (motion) {
            "drift" -> { rotationZ = phase.value * 1.2f; rotationY = phase.value * 3f; translationY = phase.value * 3 * density; cameraDistance = 16 * density }
            "pulse" -> { scaleX = 1f + (phase.value + 1f) * .0175f; scaleY = scaleX }
        }
    }
}
