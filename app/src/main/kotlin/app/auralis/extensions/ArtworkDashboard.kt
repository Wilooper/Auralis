package app.auralis.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.auralis.LocalArtwork
import app.auralis.TrackCover
import kotlin.math.roundToInt

@Composable
internal fun ArtworkDashboard(entry: ExtensionRegistry.Entry, busy: Boolean, artworkUri: android.net.Uri?, artworkTitle: String, dismiss: () -> Unit, save: (String, Int) -> Unit) {
    val spec = entry.manifest.artwork ?: return
    val settings = entry.artworkSettings ?: return
    var motion by remember(entry.manifest.id, entry.revision) { mutableStateOf(settings.motion) }
    var size by remember(entry.manifest.id, entry.revision) { mutableFloatStateOf(settings.sizePercent.toFloat()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(entry.manifest.name) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.fillMaxWidth().height(184.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalArtwork provides spec.copy(motion = motion)) {
                    TrackCover(artworkUri, artworkTitle, Modifier.size(160.dp).graphicsLayer { scaleX = size / 100; scaleY = scaleX }, RoundedCornerShape(20.dp), animate = true)
                }
            }
            Text("Motion", style = MaterialTheme.typography.titleSmall)
            // Separate rows remain usable at large font sizes and on small screens.
            spec.motions.forEach { value ->
                FilterChip(motion == value, { motion = value }, label = { Text(when (value) { "drift" -> "Gentle drift"; "tilt" -> "Touch / pointer tilt"; else -> "Still" }) })
            }
            Text("Cover size · ${size.roundToInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(size, { size = it }, valueRange = 60f..100f, steps = 7)
            Text("Relative to the space available in Now playing. Touch the preview to tilt. Motion pauses with playback and respects battery saver and reduced motion.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { motion = spec.motion; size = spec.sizePercent.toFloat() }) { Text("Reset to extension defaults") }
        }
    }, confirmButton = { TextButton(onClick = { save(motion, size.roundToInt()) }, enabled = !busy) { Text("Apply") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
