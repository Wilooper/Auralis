package app.auralis.extensions

import android.net.Uri
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** The only extension network path. Requests are user initiated and grants are checked again on completion. */
class ExtensionBridge(val registry: ExtensionRegistry, private val connectionFactory: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) {
    data class RemoteTrack(val id: String, val title: String, val artist: String, val streamUrl: String, val format: String = "mp3")
    private val mutableEvents = kotlinx.coroutines.flow.MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 16, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val events = mutableEvents.asSharedFlow()
    private val apiConnections = ConcurrentHashMap<HttpURLConnection, String>()
    private val apiSlots = Semaphore(2)
    private val connections = ConcurrentHashMap<HttpURLConnection, String>()
    private val sockets = ConcurrentHashMap<Socket, String>()
    private val rate = mutableMapOf<String, java.util.ArrayDeque<Long>>()
    private val grants = object : LinkedHashMap<String, StreamGrant>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamGrant>?) = size > 256
    }
    private data class StreamGrant(val id: String, val revision: Long, val url: String)
    private var server: ServerSocket? = null
    private val streamSlots = java.util.concurrent.Semaphore(2)
    private val workers = ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, ArrayBlockingQueue(4), { r -> Thread(r, "auralis-stream").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }
    init { registry.revoke = ::revoke }
    fun close() { server?.close(); server = null; connections.keys.forEach { it.disconnect() }; sockets.keys.forEach { runCatching { it.close() } }; workers.shutdownNow(); synchronized(grants) { grants.clear() } }
    fun cancelRequests(id: String) { apiConnections.entries.filter { it.value == id }.forEach { it.key.disconnect() } }
    fun revoke(id: String) {
        mutableEvents.tryEmit(BridgeEvent("host.lifecycle",id,"revoked"))
        connections.entries.filter { it.value == id }.forEach { it.key.disconnect() }
        sockets.entries.filter { it.value == id }.forEach { runCatching { it.key.close() } }
        synchronized(grants) { grants.entries.removeAll { it.value.id == id } }
    }
    @Synchronized private fun budget(id: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        val times = rate.getOrPut(id) { java.util.ArrayDeque() }
        while(times.isNotEmpty() && now-times.first() >= 60_000) times.removeFirst()
        require(times.size < 10) { "Request limit reached. Try again in a minute." }; times.addLast(now)
        rate.keys.retainAll(registry.entries.value.map { it.manifest.id }.toSet())
    }
    private fun open(entry: ExtensionRegistry.Entry, target: String, method: String = "GET", range: String? = null): HttpURLConnection {
        val uri = ExtensionManifest.allowed(target, entry.manifest.origins)
        check(registry.valid(entry.manifest.id, entry.revision)) { "Extension disabled" }
        val connection = connectionFactory(uri)
        connection.instanceFollowRedirects = false; connection.connectTimeout = 8000; connection.readTimeout = 15_000
        connection.requestMethod = method; connection.setRequestProperty("Accept", if(range == null) "application/json" else "audio/*,application/octet-stream")
        connection.setRequestProperty("Accept-Encoding", "identity")
        // Credentials are scoped to the declared catalog (or party) origin, never to embed origins.
        val authUrl = entry.manifest.provider?.getString("catalogUrl") ?: entry.manifest.party?.getString("url")
        if(authUrl != null && ExtensionManifest.origin(URI(authUrl)) == ExtensionManifest.origin(uri)) registry.token(entry.manifest.id)?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        range?.let { connection.setRequestProperty("Range", it) }
        connections[connection] = entry.manifest.id
        if(!registry.valid(entry.manifest.id, entry.revision)) { connections.remove(connection); connection.disconnect(); error("Extension disabled") }
        return connection
    }
    private suspend fun request(id: String, capability: String, url: String, body: JSONObject? = null): JSONObject = apiSlots.withPermit {
        withContext(Dispatchers.IO) {
            val entry = registry.requireEnabled(id, capability); budget(id)
            val connection = open(entry, url, if(body == null) "GET" else "POST")
            apiConnections[connection] = id
            try {
                if(body != null) {
                    val bytes = body.toString().toByteArray(); require(bytes.size <= 8192)
                    connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json"); connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                require(connection.responseCode in 200..299) { "Server returned HTTP ${connection.responseCode}; redirects are blocked" }
                require(connection.contentType?.substringBefore(';')?.trim()?.lowercase() == "application/json") { "Server must return JSON" }
                val started = android.os.SystemClock.elapsedRealtime()
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val b = ByteArray(8192)
                    while(true) { require(android.os.SystemClock.elapsedRealtime()-started < 15_000) { "Server response exceeded time budget" }; val n = input.read(b); if(n == -1) break; require(output.size()+n <= 262_144) { "Server response exceeds 256 KiB" }; output.write(b,0,n) }; output.toByteArray()
                }
                check(registry.valid(id, entry.revision)) { "Extension permission revoked" }
                val text = String(bytes, Charsets.UTF_8); checkJsonDepth(text)
                JSONObject(text).also { mutableEvents.tryEmit(BridgeEvent(capability,id,"completed")) }
            } finally { apiConnections.remove(connection); connections.remove(connection); connection.disconnect() }
        }
    }
    suspend fun catalog(id: String, query: String): List<RemoteTrack> {
        val entry = registry.requireEnabled(id, "catalog.read")
        val endpoint = entry.manifest.provider?.getString("catalogUrl") ?: error("No catalog")
        val url = endpoint + (if('?' in endpoint) "&" else "?") + "q=" + java.net.URLEncoder.encode(query.take(200), "UTF-8") + "&limit=100"
        val response = request(id, "catalog.read", url)
        val rows = response.getJSONArray("tracks"); require(rows.length() <= 100) { "Catalog page exceeds 100 songs" }
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            fun field(key: String, max: Int) = row.getString(key).also { require(it.isNotBlank() && it.length <= max) { "Invalid catalog $key" } }
            val stream = field("streamUrl", 2048); ExtensionManifest.allowed(stream, entry.manifest.origins)
            RemoteTrack(field("id",128), field("title",200), row.optString("artist", "Unknown artist").take(200), stream, row.optString("format","mp3").also { require(it in setOf("mp3","flac","wav","ogg")) { "Remote format must be mp3, flac, wav or ogg" } })
        }.also { require(it.map { track -> track.id }.toSet().size == it.size) { "Catalog contains duplicate IDs" }; check(registry.valid(id, entry.revision)) }
    }
    fun mediaUri(id: String, url: String, format: String = "mp3"): Uri {
        require(format in setOf("mp3","flac","wav","ogg"))
        val entry = registry.requireEnabled(id, "stream.play"); ExtensionManifest.allowed(url, entry.manifest.origins)
        return Uri.Builder().scheme("auralis").authority(id).appendPath("stream").appendQueryParameter("url", url).appendQueryParameter("format",format).build()
    }
    /** Called by the native engine on every deck open, including restored queues. Never persist local proxy tokens. */
    @Synchronized fun resolve(uri: Uri): Uri {
        require(uri.scheme == "auralis" && uri.path == "/stream")
        val id = uri.authority ?: error("Missing extension"); val entry = registry.requireEnabled(id, "stream.play")
        val url = uri.getQueryParameter("url") ?: error("Missing stream"); ExtensionManifest.allowed(url, entry.manifest.origins)
        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        synchronized(grants) { grants[token] = StreamGrant(id, entry.revision, url) }
        val listener = server ?: ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")).also { created ->
            server = created
            Thread({ while(!created.isClosed) { val socket = runCatching { created.accept() }.getOrNull() ?: break
                try { workers.execute { serve(socket) } } catch (_: java.util.concurrent.RejectedExecutionException) { socket.close() }
            } }, "auralis-loopback").apply { isDaemon = true; start() }
        }
        mutableEvents.tryEmit(BridgeEvent("stream.play",id,"prepared"))
        return Uri.parse("http://127.0.0.1:${listener.localPort}/audio/$token")
    }
    private fun serve(socket: Socket) {
        var acquired = false; var connection: HttpURLConnection? = null
        try {
            socket.soTimeout = 5000
            val input = socket.getInputStream(); val header = java.io.ByteArrayOutputStream()
            var end = 0
            while(header.size() < 8192 && end != 4) { val c = input.read(); if(c < 0) return; header.write(c); end = when { end == 0 && c == 13 -> 1; end == 1 && c == 10 -> 2; end == 2 && c == 13 -> 3; end == 3 && c == 10 -> 4; c == 13 -> 1; else -> 0 } }
            require(end == 4)
            val lines = header.toString("US-ASCII").split("\r\n"); val parts = lines.first().split(' ')
            require(parts.size == 3 && parts[0] in setOf("GET", "HEAD") && parts[1].matches(Regex("/audio/[a-f0-9]{48}")))
            val grant = synchronized(grants) { grants[parts[1].substringAfterLast('/')] } ?: error("Expired stream grant")
            sockets[socket] = grant.id
            val entry = registry.requireEnabled(grant.id, "stream.play"); check(entry.revision == grant.revision)
            acquired = streamSlots.tryAcquire(); require(acquired) { "Two stream limit" }
            val range = lines.drop(1).firstOrNull { it.startsWith("Range:", true) }?.substringAfter(':')?.trim()
            require(range == null || range.matches(Regex("bytes=[0-9]{0,16}-[0-9]{0,16}")) && range != "bytes=-")
            connection = open(entry, grant.url, parts[0], range)
            val code = connection.responseCode
            require(code == 200 || code == 206) { "Stream redirects and error pages are blocked" }
            val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            require(type in setOf("audio/mpeg","audio/mp3","audio/flac","audio/x-flac","audio/wav","audio/x-wav","audio/wave","audio/ogg","application/ogg","application/octet-stream")) { "Expected a direct audio stream" }
            val length = connection.contentLengthLong; require(length <= 512L*1024*1024)
            val response = StringBuilder("HTTP/1.1 $code OK\r\nConnection: close\r\nContent-Type: $type\r\n")
            if(length >= 0) response.append("Content-Length: $length\r\n")
            for(key in listOf("Content-Range", "Accept-Ranges")) connection.getHeaderField(key)?.takeIf { value -> value.length < 128 && value.none { it == '\r' || it == '\n' } }?.let { response.append("$key: $it\r\n") }
            val output = socket.getOutputStream(); output.write(response.append("\r\n").toString().toByteArray(Charsets.US_ASCII)); output.flush()
            if(parts[0] == "HEAD") return
            val started = android.os.SystemClock.elapsedRealtime(); var total = 0L
            connection.inputStream.use { stream ->
                val buffer = ByteArray(32*1024)
                while(registry.valid(grant.id, grant.revision)) {
                    require(android.os.SystemClock.elapsedRealtime()-started < 30*60*1000 && total <= 512L*1024*1024)
                    val n = stream.read(buffer); if(n < 0) break
                    check(registry.valid(grant.id, grant.revision)); output.write(buffer,0,n); total += n
                    val wait = total*1000/1_048_576 - (android.os.SystemClock.elapsedRealtime()-started)
                    if(wait > 0) Thread.sleep(wait.coerceAtMost(32))
                }
            }
        } catch (_: Exception) {
            runCatching { socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()) }
        } finally {
            connection?.let { connections.remove(it); it.disconnect() }; sockets.remove(socket); runCatching { socket.close() }; if(acquired) streamSlots.release()
        }
    }
    suspend fun party(id: String, action: String, session: String = "", track: String = ""): JSONObject {
        require(action in setOf("create", "join", "state", "enqueue", "leave")); require(session.length <= 128 && track.length <= 128)
        val entry = registry.requireEnabled(id, "party.session")
        return request(id, "party.session", entry.manifest.party?.getString("url") ?: error("No party endpoint"), JSONObject().put("action",action).put("session",session).put("trackId",track)).also { response ->
            require(response.getString("session").length in 1..128)
            val queue = response.getJSONArray("queue"); require(queue.length() <= 100)
            repeat(queue.length()) { require(queue.getString(it).length in 1..128) }
        }
    }
}
