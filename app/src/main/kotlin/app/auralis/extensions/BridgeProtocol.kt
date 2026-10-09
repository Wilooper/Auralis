package app.auralis.extensions

import org.json.JSONArray
import org.json.JSONObject

/** Versioned host API for declarative contributions. Not an exported Android or public HTTP endpoint. */
data class BridgeRequest(val requestId: String, val apiVersion: Int, val channel: String, val mode: String, val operation: String, val payload: JSONObject = JSONObject())
data class BridgeEvent(val channel: String, val extensionId: String, val operation: String)

suspend fun ExtensionBridge.call(extensionId: String, request: BridgeRequest): JSONObject {
    require(request.apiVersion == 1 && request.requestId.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid bridge envelope" }
    require(request.payload.toString().toByteArray().size <= 8192) { "Bridge payload limit exceeded" }
    checkJsonDepth(request.payload.toString())
    val entry = registry.requireEnabled(extensionId,request.channel)
    val expectedMode = when(request.channel) { "catalog.read" -> "foreground"; "stream.play" -> "playback"; "party.session" -> "party"; "web.embed" -> "web"; "skin.apply" -> "appearance"; else -> error("Unknown channel") }
    require(request.mode == expectedMode) { "Invalid channel mode" }
    val result = when(request.channel to request.operation) {
        "catalog.read" to "search" -> JSONObject().put("tracks", JSONArray().apply { catalog(extensionId,request.payload.optString("q", "")).forEach { put(JSONObject().put("id",it.id).put("title",it.title).put("artist",it.artist).put("streamUrl",it.streamUrl).put("format",it.format)) } })
        "stream.play" to "prepare" -> JSONObject().put("uri",mediaUri(extensionId,request.payload.getString("streamUrl"),request.payload.optString("format","mp3")).toString())
        "party.session" to "request" -> party(extensionId,request.payload.getString("action"),request.payload.optString("session"),request.payload.optString("trackId"))
        "web.embed" to "describe" -> JSONObject().put("url",entry.manifest.web?.getString("url") ?: error("Missing embed"))
        "skin.apply" to "describe" -> JSONObject(entry.manifest.raw).getJSONObject("skin")
        else -> error("Unsupported bridge operation")
    }
    check(registry.valid(extensionId,entry.revision)) { "Bridge grant revoked" }
    return JSONObject().put("apiVersion",1).put("requestId",request.requestId).put("channel",request.channel).put("result",result)
}
