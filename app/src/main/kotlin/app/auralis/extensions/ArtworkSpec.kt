package app.auralis.extensions

import app.reva.sdk.ExtensionProtocol
import org.json.JSONObject

/** Declarative layer transforms only. No script, frame callback or external media URL. */
data class ArtworkSpec(val motions: List<String>, val motion: String, val sizePercent: Int) {
    companion object {
        val supported = setOf("none", "drift", "tilt")
        fun parse(json: JSONObject): ArtworkSpec {
            ExtensionProtocol.objectValue(json.toString())
            require(json.keys().asSequence().toSet() == setOf("motions", "motion", "sizePercent")) { "Invalid artwork fields" }
            val values = json.getJSONArray("motions")
            val motions = (0 until values.length()).map { values.get(it) as? String ?: error("Motion must be text") }
            require(motions.size in 1..3 && motions.distinct() == motions && motions.all { it in supported }) { "Unsupported artwork motions" }
            val motion = json.get("motion") as? String ?: error("Motion must be text")
            require(motion in motions) { "Default motion must be offered" }
            val size = json.get("sizePercent") as? Number ?: error("Size must be numeric")
            require(size.toDouble() in 60.0..100.0 && size.toDouble() == size.toInt().toDouble()) { "Cover size must be 60–100 percent" }
            return ArtworkSpec(motions, motion, size.toInt())
        }
    }
}
