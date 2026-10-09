package app.auralis.extensions

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Give embedded pages a bounded, redirect-denying network path rather than WebView's default fetch. */
internal class EmbedNetwork(private val entry: ExtensionRegistry.Entry, private val connectionFactory: (java.net.URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) {
    private val slots = Semaphore(6)
    private val active = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    private val closed = AtomicBoolean(false)
    fun close() { closed.set(true); active.forEach { it.disconnect() }; active.clear() }
    private fun denied() = WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    fun fetch(request: WebResourceRequest): WebResourceResponse {
        if(closed.get() || request.method != "GET") return denied()
        val uri = runCatching { ExtensionManifest.allowed(request.url.toString(),entry.manifest.origins) }.getOrNull() ?: return denied()
        if(!slots.tryAcquire()) return denied()
        var connection: HttpURLConnection? = null
        try {
            connection = connectionFactory(uri)
            connection.instanceFollowRedirects = false; connection.connectTimeout = 8000; connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            request.requestHeaders.entries.filter { it.key.equals("Range",true) || it.key.equals("Accept",true) }.forEach { (key,value) -> if(value.length < 1024 && value.none { it == '\r' || it == '\n' }) connection.setRequestProperty(key,value) }
            active.add(connection); if(closed.get()) error("Closed embed")
            val code = connection.responseCode
            require(code in 200..299 && connection.contentLengthLong <= 32L*1024*1024) { "Web resource budget or redirect denied" }
            val headers = mutableMapOf<String,String>()
            connection.headerFields.forEach { (key,values) -> if(key != null && values != null && !key.equals("Set-Cookie",true) && !key.equals("Content-Security-Policy",true)) headers[key] = values.joinToString(", ") }
            val origins = entry.manifest.origins.joinToString(" ")
            // Constrain frames, navigation-adjacent resources, workers, forms and JS connection APIs too.
            val policy = "default-src $origins 'unsafe-inline'; connect-src $origins; frame-src $origins; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
            // Preserve the site's own CSP as a second enforced policy, rather than loosening it.
            headers["Content-Security-Policy"] = connection.getHeaderField("Content-Security-Policy")?.let { "$it, $policy" } ?: policy
            val current = connection
            val stream = object : FilterInputStream(current.inputStream) {
                private val done = AtomicBoolean(false); private val start = android.os.SystemClock.elapsedRealtime(); private var bytes = 0L
                fun check(n: Int): Int {
                    if(n > 0) bytes += n
                    if(closed.get() || bytes > 32L*1024*1024 || android.os.SystemClock.elapsedRealtime()-start > 120_000) { close(); throw java.io.IOException("Web resource budget exceeded") }
                    if(n == -1) close()
                    return n
                }
                override fun read(): Int = try { val n = `in`.read(); check(if(n < 0) -1 else 1); n } catch(e: java.io.IOException) { close(); throw e }
                override fun read(b: ByteArray, off: Int, len: Int): Int = try { check(`in`.read(b,off,len)) } catch(e: java.io.IOException) { close(); throw e }
                override fun close() { if(done.compareAndSet(false,true)) { runCatching { super.close() }; current.disconnect(); active.remove(current); slots.release() } }
            }
            val content = connection.contentType.orEmpty().split(';')
            return WebResourceResponse(content[0].ifBlank { "application/octet-stream" },"UTF-8",code,"OK",headers,stream)
        } catch (_: Exception) { connection?.let { active.remove(it); it.disconnect() }; slots.release(); return denied() }
    }
}
