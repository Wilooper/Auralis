package app.auralis.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun SkinEditor(current: Skin, dismiss: () -> Unit, save: (JSONObject) -> Unit) {
    val colors = remember { mutableStateMapOf("primary" to current.primary,"background" to current.background,"surface" to current.surface,"text" to current.text,"muted" to current.muted) }
    var scale by remember { mutableFloatStateOf(current.textScale) }
    var row by remember { mutableFloatStateOf(current.rowHeight.toFloat()) }
    var art by remember { mutableFloatStateOf(current.artworkSize.toFloat()) }
    var radius by remember { mutableFloatStateOf(current.cornerRadius.toFloat()) }
    var layout by remember { mutableStateOf(current.layout) }
    var font by remember { mutableStateOf(current.font) }
    var atmosphere by remember { mutableStateOf(current.atmosphere) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your custom style") }, text = {
        LazyColumn(Modifier.heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Colors use #RRGGBB. Readability checks and touch-size limits apply.", style = MaterialTheme.typography.bodySmall); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            colors.keys.toList().forEach { key -> item(key = key) { OutlinedTextField(colors[key].orEmpty(), { colors[key] = it.take(7) }, label = { Text(key.replaceFirstChar { it.titlecase() }) }, singleLine = true) } }
            item { Text("Text size · ${"%.2f".format(scale)}×"); Slider(scale,{ scale = it },valueRange=.85f..1.3f) }
            item { Text("Row height · ${row.toInt()} dp"); Slider(row,{ row = it },valueRange=64f..112f,steps=47) }
            item { Text("Cover size · ${art.toInt()} dp"); Slider(art,{ art = it },valueRange=40f..80f,steps=39) }
            item { Text("Corners · ${radius.toInt()} dp"); Slider(radius,{ radius = it },valueRange=0f..32f,steps=31) }
            item { Text("Library layout"); listOf("standard","compact","covers").forEach { value -> FilterChip(layout == value,{ layout = value },label = { Text(value.replaceFirstChar { it.titlecase() }) }) } }
            item { Text("Typeface"); listOf("sans","serif","mono").forEach { value -> FilterChip(font == value,{ font = value },label = { Text(value.replaceFirstChar { it.titlecase() }) }) } }
            item { Row { Text("Artwork atmosphere",Modifier.weight(1f)); Switch(atmosphere,{ atmosphere = it }) } }
        }
    }, confirmButton = { TextButton({
        val json = JSONObject().apply { colors.forEach { (k,v) -> put(k,v) }; put("textScale",scale); put("rowHeight",row.toInt()); put("artworkSize",art.toInt()); put("cornerRadius",radius.toInt()); put("layout",layout); put("font",font); put("atmosphere",atmosphere) }
        try { Skin.parse(json); save(json) } catch(e: Exception) { error = e.message }
    }) { Text("Apply style") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
