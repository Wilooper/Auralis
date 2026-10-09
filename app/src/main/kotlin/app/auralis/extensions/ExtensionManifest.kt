package app.auralis.extensions

import org.json.JSONObject
import java.net.URI

/** V1 is data only. Never add reflection, dynamic libraries, eval, or package execution here. */
data class ExtensionManifest(val id: String, val name: String, val version: String,
    val capabilities: Set<String>, val origins: Set<String>, val provider: JSONObject?,
    val web: JSONObject?, val skin: Skin?, val party: JSONObject?, val raw: String) {
    companion object {
        const val MAX_BYTES = 65_536
        val supported = setOf("catalog.read", "stream.play", "web.embed", "skin.apply", "party.session")
        fun parse(text: String): ExtensionManifest {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Extension exceeds 64 KiB" }
            checkJsonDepth(text)
            val tokener = org.json.JSONTokener(text)
            val json = tokener.nextValue() as? JSONObject ?: error("Manifest must be a JSON object")
            require(tokener.nextClean() == '\u0000') { "Trailing manifest content" }
            require(json.keys().asSequence().all { it in setOf("apiVersion", "id", "name", "version", "capabilities", "origins", "provider", "web", "skin", "party") }) { "Unsupported manifest fields. Executable extensions are not supported." }
            require(json.getInt("apiVersion") == 1) { "Unsupported SDK version" }
            val id = json.getString("id")
            require(id.matches(Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+){1,7}")) && id.length <= 80) { "Use a reverse-domain extension ID" }
            val name = json.getString("name").trim(); val version = json.getString("version")
            require(name.length in 1..80 && version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Invalid name or version" }
            fun strings(key: String): Set<String> {
                val a = json.optJSONArray(key) ?: return emptySet()
                require(a.length() <= 12) { "Too many $key" }
                return (0 until a.length()).map { a.getString(it) }.toSet()
            }
            val caps = strings("capabilities"); require(caps.isNotEmpty() && caps.all { it in supported }) { "Unknown capability" }
            val origins = strings("origins").map { value ->
                val uri = URI(value); require(value == origin(uri) && uri.scheme == "https") { "Origins must be exact HTTPS origins, without paths" }; value
            }.toSet()
            fun section(key: String, cap: String): JSONObject? = json.optJSONObject(key)?.also {
                require(cap in caps) { "$key needs $cap" }
            }
            val provider = section("provider", "catalog.read")
            provider?.let { require(it.keys().asSequence().all { key -> key in setOf("catalogUrl") }); allowed(it.getString("catalogUrl"), origins) }
            require("stream.play" !in caps || provider != null) { "Streaming needs a provider" }
            val web = section("web", "web.embed")
            web?.let { require(it.keys().asSequence().all { key -> key == "url" }); allowed(it.getString("url"), origins) }
            val party = section("party", "party.session")
            party?.let { require(it.keys().asSequence().all { key -> key == "url" }); allowed(it.getString("url"), origins) }
            val skin = section("skin", "skin.apply")?.let(Skin::parse)
            require(provider != null || web != null || party != null || skin != null) { "Extension has no supported contribution" }
            return ExtensionManifest(id, name, version, caps, origins, provider, web, skin, party, json.toString())
        }
        fun origin(uri: URI): String {
            require(uri.isAbsolute && !uri.isOpaque && uri.host != null && uri.rawUserInfo == null && uri.port in -1..65535 && uri.port != 0) { "Invalid URL" }
            val host = uri.host.lowercase(); return "${uri.scheme.lowercase()}://$host${if(uri.port == -1 || uri.port == 443) "" else ":${uri.port}"}"
        }
        fun allowed(value: String, origins: Set<String>): URI {
            require(value.length <= 2048 && !value.contains('\\')) { "Invalid URL" }
            val uri = URI(value)
            require(uri.scheme == "https" && uri.rawFragment == null && origin(uri) in origins) { "URL is outside approved HTTPS origins" }
            return uri
        }
    }
}

data class Skin(val primary: String = "#69E0BE", val background: String = "#09090C", val surface: String = "#19191F",
    val text: String = "#F8F6F7", val muted: String = "#ADABB6", val textScale: Float = 1f,
    val rowHeight: Int = 72, val cornerRadius: Int = 18, val artworkSize: Int = 56,
    val layout: String = "standard", val font: String = "sans", val atmosphere: Boolean = true) {
    companion object {
        fun parse(json: JSONObject): Skin {
            require(json.keys().asSequence().all { it in setOf("primary", "background", "surface", "text", "muted", "textScale", "rowHeight", "cornerRadius", "artworkSize", "layout", "font", "atmosphere") }) { "Unknown skin token" }
            fun color(key: String, fallback: String) = json.optString(key, fallback).also { require(it.matches(Regex("#[0-9a-fA-F]{6}"))) { "Invalid $key color" } }
            val skin = Skin(color("primary", "#69E0BE"), color("background", "#09090C"), color("surface", "#19191F"), color("text", "#F8F6F7"), color("muted", "#ADABB6"),
                json.optDouble("textScale", 1.0).toFloat(), json.optInt("rowHeight", 72), json.optInt("cornerRadius", 18), json.optInt("artworkSize", 56),
                json.optString("layout", "standard"), json.optString("font", "sans"), json.optBoolean("atmosphere", true))
            require(skin.textScale.isFinite() && skin.textScale in .85f..1.3f && skin.rowHeight in 64..112 && skin.cornerRadius in 0..32 && skin.artworkSize in 40..80) { "Skin exceeds accessibility/layout limits" }
            require(skin.layout in setOf("standard", "compact", "covers") && skin.font in setOf("sans", "serif", "mono")) { "Unsupported skin layout or font" }
            fun luminance(c: String): Double {
                val n = c.drop(1).toInt(16)
                fun channel(v: Int): Double { val x = v / 255.0; return if (x <= .04045) x / 12.92 else Math.pow((x + .055) / 1.055, 2.4) }
                return .2126 * channel(n shr 16 and 255) + .7152 * channel(n shr 8 and 255) + .0722 * channel(n and 255)
            }
            fun contrast(a: String, b: String): Double { val x = luminance(a); val y = luminance(b); return (maxOf(x,y)+.05)/(minOf(x,y)+.05) }
            require(contrast(skin.text, skin.background) >= 4.5 && contrast(skin.text, skin.surface) >= 4.5 && contrast(skin.muted, skin.background) >= 3 && contrast(skin.muted, skin.surface) >= 3 && contrast(skin.primary, skin.background) >= 3) { "Skin colors need readable contrast" }
            return skin
        }
    }
}

/** Strict quote/comment boundary plus a structural depth budget for platform JSON parsing. */
internal fun checkJsonDepth(text: String) {
    var depth = 0; var quoted = false; var escaped = false
    text.forEach { c ->
        if(quoted) { if(escaped) escaped = false else if(c == '\\') escaped = true else if(c == '"') quoted = false }
        else if(c == '"') quoted = true
        else if(c == '{' || c == '[') { depth++; require(depth <= 16) { "JSON nesting limit exceeded" } }
        else if(c == '}' || c == ']') { depth--; require(depth >= 0) }
        else require(c != '\'' && c != '/' && c != '\\') { "Use standard JSON without comments or single quotes" }
    }
    require(depth == 0 && !quoted) { "Incomplete JSON" }
}
