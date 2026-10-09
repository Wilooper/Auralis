package app.auralis.extensions

import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EmbedNetworkTest {
    private val entry = ExtensionRegistry.Entry(ExtensionManifest.parse("""{"apiVersion":1,"id":"org.test.embed","name":"Embed","version":"1.0.0","capabilities":["web.embed"],"origins":["https://web.example"],"web":{"url":"https://web.example/embed"}}"""),true,1,setOf("web.embed"))
    private class Request(val target: String, val verb: String = "GET") : WebResourceRequest {
        override fun getUrl() = Uri.parse(target)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = verb
        override fun getRequestHeaders() = emptyMap<String,String>()
    }
    private class Connection(val code: Int = 200) : HttpURLConnection(URL("https://web.example/embed")) {
        var closed = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { closed = true }
        override fun getResponseCode() = code
        override fun getContentLengthLong() = 5L
        override fun getContentType() = "text/html"
        override fun getInputStream() = java.io.ByteArrayInputStream("hello".toByteArray())
        override fun getHeaderFields(): Map<String,List<String>> = mapOf("Set-Cookie" to listOf("private=secret"))
        override fun getHeaderField(name: String): String? = null
    }
    @Test fun undeclaredOriginsAndPostNeverReachNetwork() {
        var requests=0; val network=EmbedNetwork(entry) { requests++; Connection() }
        assertEquals(403,network.fetch(Request("https://evil.example/embed")).statusCode)
        assertEquals(403,network.fetch(Request("https://web.example/embed","POST")).statusCode)
        assertEquals(0,requests); network.close()
    }
    @Test fun redirectsAreDeniedAndResourcesCarryConstrainedCsp() {
        val redirect=Connection(302); val first=EmbedNetwork(entry) { redirect }
        assertEquals(403,first.fetch(Request("https://web.example/embed")).statusCode); assertFalse(redirect.instanceFollowRedirects); assertTrue(redirect.closed); first.close()
        val connection=Connection(); val network=EmbedNetwork(entry) { connection }
        val response=network.fetch(Request("https://web.example/embed"))
        assertNull(response.responseHeaders["Set-Cookie"]); assertTrue(response.responseHeaders["Content-Security-Policy"]!!.contains("worker-src 'none'"))
        assertEquals("hello",response.data.use { it.readBytes().toString(Charsets.UTF_8) }); assertTrue(connection.closed); network.close()
    }
    @Test fun closingEmbedInvalidatesOutstandingResourceStreams() {
        val connection=Connection(); val network=EmbedNetwork(entry) { connection }
        val response=network.fetch(Request("https://web.example/embed")); network.close()
        try { response.data.read(); fail("Closed embed must not keep reading") } catch (_: java.io.IOException) {}
        assertTrue(connection.closed)
    }
}
