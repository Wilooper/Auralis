package app.auralis.extensions

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.auralis.ReVaApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection

data class ExtensionState(val busy: Boolean = false, val message: String? = null, val tracks: List<ExtensionBridge.RemoteTrack> = emptyList(), val provider: String? = null, val party: JSONObject? = null)
class ExtensionViewModel(application: Application) : AndroidViewModel(application) {
    val bridge = (application as ReVaApplication).extensionBridge
    val registry = bridge.registry
    val entries = registry.entries
    private val mutable = MutableStateFlow(ExtensionState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { viewModelScope.launch { entries.collect { rows ->
        val id = mutable.value.provider
        if(id != null && rows.none { it.manifest.id == id && it.enabled }) { job?.cancel(); mutable.value = ExtensionState(message = "Extension disabled") }
    } } }
    private fun run(block: suspend () -> Unit) {
        job?.cancel(); job = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = null)
            try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.value = mutable.value.copy(message = e.message ?: "Bridge request failed") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun createSkin(skin: JSONObject) = run {
        withContext(Dispatchers.IO) {
            val json = JSONObject().put("apiVersion",1).put("id","org.auralis.personal").put("name","Your custom style").put("version","1.0.0").put("capabilities",org.json.JSONArray(listOf("skin.apply"))).put("origins",org.json.JSONArray()).put("skin",skin)
            registry.install(json.toString()); registry.enable("org.auralis.personal",true,setOf("skin.apply"))
        }
        mutable.value = mutable.value.copy(message = "Your custom style is applied. Disable it here to restore your previous skin.")
    }
    fun importFile(uri: Uri) = run { val entry = withContext(Dispatchers.IO) { registry.importFile(uri) }; mutable.value = mutable.value.copy(message = "${entry.manifest.name} imported. Review permissions to enable it.") }
    fun importUrl(source: String) = run {
        val entry = withContext(Dispatchers.IO) {
            val uri = githubManifestUri(source)
            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, text/plain;q=0.9")
                setRequestProperty("User-Agent", "ReVa-Android")
            }
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) { "GitHub returned HTTP ${connection.responseCode}; redirects are blocked" }
                val bytes = connection.inputStream.use { it.readBytesBounded(ExtensionManifest.MAX_BYTES) }
                registry.install(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
        mutable.value = mutable.value.copy(message = "${entry.manifest.name} imported from GitHub. Review permissions to enable it.")
    }
    fun toggle(id: String, enabled: Boolean, grants: Set<String>? = null) = run { withContext(Dispatchers.IO) { if(grants == null) registry.enable(id, enabled) else registry.enable(id, enabled, grants) } }
    fun remove(id: String) = run { withContext(Dispatchers.IO) { registry.remove(id) } }
    fun chooseSeekBarStyle(id: String, style: String) = run { withContext(Dispatchers.IO) { registry.chooseSeekBarStyle(id, style) } }
    fun chooseArtwork(id: String, motion: String, sizePercent: Int) = run { withContext(Dispatchers.IO) { registry.chooseArtwork(id, motion, sizePercent) } }
    fun token(id: String, text: String) = run { withContext(Dispatchers.IO) { registry.saveToken(id,text) }; mutable.value = mutable.value.copy(message = "Token saved. Review permissions to enable the extension.") }
    fun browse(id: String, query: String) {
        mutable.value = ExtensionState(provider = id)
        run { val reply = bridge.call(id, BridgeRequest(java.util.UUID.randomUUID().toString(),1,"catalog.read","foreground","search",JSONObject().put("q",query)))
            val rows = reply.getJSONObject("result").getJSONArray("tracks")
            val tracks = (0 until rows.length()).map { val r = rows.getJSONObject(it); ExtensionBridge.RemoteTrack(r.getString("id"),r.getString("title"),r.getString("artist"),r.getString("streamUrl"),r.optString("format","mp3")) }; mutable.value = mutable.value.copy(tracks = tracks, message = if(tracks.isEmpty()) "No songs found" else "${tracks.size} songs · user-requested catalog") }
    }
    fun party(id: String, action: String, session: String, track: String) {
        mutable.value = mutable.value.copy(provider = id)
        run { val response = bridge.call(id,BridgeRequest(java.util.UUID.randomUUID().toString(),1,"party.session","party","request",JSONObject().put("action",action).put("session",session).put("trackId",track))).getJSONObject("result"); mutable.value = mutable.value.copy(party = response, message = "Party $action completed") }
    }
    fun cancel() { job?.cancel(); mutable.value.provider?.let(bridge::cancelRequests); mutable.value = mutable.value.copy(busy = false, message = "Request canceled") }
}

internal fun githubManifestUri(source: String): URI {
    require(source.length <= 2048 && !source.contains('\\')) { "Invalid GitHub URL" }
    val input = URI(source)
    require(input.scheme == "https" && input.rawUserInfo == null && input.port in setOf(-1, 443) && input.rawFragment == null) { "Use a public HTTPS GitHub link" }
    val normalized = when (input.host?.lowercase()) {
        "raw.githubusercontent.com" -> input.also { require(it.rawQuery == null) { "Query strings are not accepted" } }
        "github.com" -> {
            require(input.rawQuery == null) { "Query strings are not accepted" }
            val parts = input.path.split('/').filter(String::isNotEmpty)
            require(parts.size >= 5 && parts[2] == "blob") { "Use a GitHub file link or raw manifest link" }
            URI("https", "raw.githubusercontent.com", "/${parts[0]}/${parts[1]}/${parts.drop(3).joinToString("/")}", null)
        }
        else -> error("Only github.com and raw.githubusercontent.com links are supported")
    }
    require(normalized.host.equals("raw.githubusercontent.com", true) && normalized.scheme == "https" && normalized.rawUserInfo == null && normalized.port == -1) { "Invalid public GitHub URL" }
    return normalized
}
