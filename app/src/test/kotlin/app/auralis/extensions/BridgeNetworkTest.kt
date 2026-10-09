package app.auralis.extensions

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BridgeNetworkTest {
    private val text = """{"apiVersion":1,"id":"org.test.network","name":"Network","version":"1.0.0","capabilities":["catalog.read","stream.play","party.session"],"origins":["https://music.example"],"provider":{"catalogUrl":"https://music.example/v1/catalog"},"party":{"url":"https://music.example/v1/party"}}"""
    private class Connection(var body: ByteArray, val code: Int = 200, val type: String = "application/json") : HttpURLConnection(URL("https://music.example")) {
        var closed = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { closed = true }
        override fun getResponseCode() = code
        override fun getContentType() = type
        override fun getContentLengthLong() = body.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(body)
    }
    private fun registry() = ExtensionRegistry(RuntimeEnvironment.getApplication()).also { it.install(text); it.enable("org.test.network",true) }
    private fun denied(block: () -> Unit) { try { block(); fail("Expected bridge denial") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    @Test fun redirectsNeverFollowAndConnectionsClose() = runBlocking {
        val registry=registry(); val connection=Connection(ByteArray(0),302)
        val bridge=ExtensionBridge(registry) { connection }
        try { denied { runBlocking { bridge.catalog("org.test.network","") } }; assertFalse(connection.instanceFollowRedirects); assertTrue(connection.closed) } finally { bridge.close() }
    }
    @Test fun responsesAreBoundedAndNestedJsonIsRejected() {
        for(body in listOf(ByteArray(262145) { 32 }, ("{\"tracks\":"+"[".repeat(17)+"]".repeat(17)+"}").toByteArray())) {
            val connection=Connection(body); val bridge=ExtensionBridge(registry()) { connection }
            try { denied { runBlocking { bridge.catalog("org.test.network","") } }; assertTrue(connection.closed) } finally { bridge.close() }
        }
    }
    @Test fun catalogOriginAndFormatsAreCheckedBeforePlayback() {
        for(row in listOf("{\"id\":\"x\",\"title\":\"x\",\"streamUrl\":\"https://evil.example/file\"}","{\"id\":\"x\",\"title\":\"x\",\"streamUrl\":\"https://music.example/file\",\"format\":\"hls\"}")) {
            val bridge=ExtensionBridge(registry()) { Connection("{\"tracks\":[$row]}".toByteArray()) }
            try { denied { runBlocking { bridge.catalog("org.test.network","") } } } finally { bridge.close() }
        }
    }
    @Test fun rateBudgetRejectsEleventhRequest() {
        var count=0; val bridge=ExtensionBridge(registry()) { count++; Connection("{\"tracks\":[]}".toByteArray()) }
        try { repeat(10) { runBlocking { bridge.catalog("org.test.network","") } }; denied { runBlocking { bridge.catalog("org.test.network","") } }; assertEquals(10,count) } finally { bridge.close() }
    }
    @Test fun versionedEnvelopeRejectsWrongModeAndUnknownOperations() {
        val bridge=ExtensionBridge(registry()) { error("Must not access network") }
        try {
            denied { runBlocking { bridge.call("org.test.network",BridgeRequest("r1",1,"catalog.read","background","search")) } }
            denied { runBlocking { bridge.call("org.test.network",BridgeRequest("r1",1,"catalog.read","foreground","exec")) } }
            denied { runBlocking { bridge.call("org.test.network",BridgeRequest("r1",2,"catalog.read","foreground","search")) } }
        } finally { bridge.close() }
    }
    @Test fun streamProxyUsesOpaqueTokenAndRevokesIt() {
        val registry=registry(); var count=0
        val bridge=ExtensionBridge(registry) { count++; Connection("audio-fixture".toByteArray(),200,"audio/mpeg") }
        try {
            val local=bridge.resolve(bridge.mediaUri("org.test.network","https://music.example/file"))
            assertEquals("127.0.0.1",local.host); assertFalse(local.toString().contains("music.example"))
            val first=URL(local.toString()).openConnection() as HttpURLConnection
            assertEquals(200,first.responseCode); assertEquals("audio-fixture",first.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }); first.disconnect()
            registry.enable("org.test.network",false)
            val revoked=URL(local.toString()).openConnection() as HttpURLConnection
            assertEquals(403,revoked.responseCode); revoked.disconnect(); assertEquals(1,count)
        } finally { bridge.close() }
    }
}
