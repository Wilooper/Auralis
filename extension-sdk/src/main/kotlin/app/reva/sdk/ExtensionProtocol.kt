package app.reva.sdk

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader

/** Bounded strict JSON shared by the SDK and host; no tags, duplicate keys or trailing values. */
object ExtensionProtocol {
    const val MAX_INPUT_BYTES = 16_384
    const val MAX_REQUEST_BYTES = 4096
    const val MAX_REPLY_BYTES = 262_144
    fun objectValue(text: String, maxBytes: Int = MAX_INPUT_BYTES): JSONObject {
        require(maxBytes in 1..MAX_REPLY_BYTES && text.length <= maxBytes && text.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Protocol size limit exceeded" }
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val result = value(reader, 0) as? JSONObject ?: error("Expected JSON object")
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON content" }
            return result
        }
    }
    fun panel(text: String): JSONObject = objectValue(text).also { result ->
        require(result.keys().asSequence().toSet() == setOf("title", "text")) { "Unsupported result panel" }
        val title = result.get("title") as? String ?: error("Panel title must be text")
        val body = result.get("text") as? String ?: error("Panel body must be text")
        require(title.isNotBlank() && title.length <= 80 && title.none(Char::isISOControl) && body.length <= 8000)
    }
    private fun value(reader: JsonReader, depth: Int): Any {
        require(depth <= 16) { "Protocol nesting limit exceeded" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().also { result ->
                reader.beginObject()
                val seen = mutableSetOf<String>()
                while (reader.hasNext()) {
                    val name = reader.nextName(); require(seen.add(name)) { "Duplicate JSON field" }
                    result.put(name, value(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().also { result ->
                reader.beginArray()
                while (reader.hasNext()) result.put(value(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            JsonToken.NUMBER -> reader.nextString().let { number -> number.toLongOrNull() ?: number.toDouble().also { require(it.isFinite()) } }
            else -> error("Unsupported JSON value")
        }
    }
}
