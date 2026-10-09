package app.auralis.extensions

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExtensionSecurityTest {
    private fun manifest(extra: String = "") = """{"apiVersion":1,"id":"org.test.player","name":"Test","version":"1.0.0","capabilities":["catalog.read","stream.play"],"origins":["https://music.example"],"provider":{"catalogUrl":"https://music.example/v1/catalog"}$extra}"""
    private fun denied(block: () -> Unit) { try { block(); fail("Expected denial") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    @Test fun publishedSdkExamplesAreAcceptedByAndroid() {
        val root = java.io.File(System.getProperty("auralis.fixtures")).parentFile.resolve("sdk/examples")
        val examples = root.listFiles { file -> file.name.endsWith(".auralis.json") }!!
        assertEquals(4,examples.size)
        examples.forEach { ExtensionManifest.parse(it.readText()) }
    }
    @Test fun scriptsAndNativeCodeCannotBeImported() { denied { ExtensionManifest.parse(manifest(",\"script\":\"evil.js\"")) }; denied { ExtensionManifest.parse(manifest().replace("catalog.read", "device.exec")) } }
    @Test fun originBoundaryRejectsLookalikesCredentialsHttpAndPorts() {
        val origins = setOf("https://music.example")
        listOf("https://music.example.evil/track", "https://music.example@evil/track", "http://music.example/track", "https://music.example:8443/track", "file:///secret", "content://secret", "https://music.example/track#fragment", "https://music.example\\@evil/track").forEach { denied { ExtensionManifest.allowed(it,origins) } }
        assertEquals("music.example", ExtensionManifest.allowed("https://music.example/track?a=1",origins).host)
    }
    @Test fun unknownVersionsAndContributionCapabilitiesAreRejected() { denied { ExtensionManifest.parse(manifest().replace("\"apiVersion\":1", "\"apiVersion\":2")) }; denied { ExtensionManifest.parse(manifest().replace("\"catalog.read\",", "")) } }
    @Test fun parsingBoundsBytesDepthAndTrailingContent() { denied { ExtensionManifest.parse(" ".repeat(65_537)) }; denied { ExtensionManifest.parse("[".repeat(17)+"]".repeat(17)) }; denied { ExtensionManifest.parse(manifest()+" trailing") } }
    @Test fun safeSkinRejectsUnreadableColorsAndExtremeLayout() { denied { Skin.parse(JSONObject().put("textScale",4)) }; denied { Skin.parse(JSONObject().put("background","#FFFFFF")) }; denied { Skin.parse(JSONObject().put("rowHeight",1)) }; assertEquals("compact", Skin.parse(JSONObject().put("layout","compact")).layout) }
    @Test fun installsDisabledAndPartialGrantsAreEnforced() {
        val registry = ExtensionRegistry(RuntimeEnvironment.getApplication()); registry.install(manifest())
        denied { registry.requireEnabled("org.test.player","catalog.read") }
        registry.enable("org.test.player",true,setOf("catalog.read"))
        assertTrue(registry.requireEnabled("org.test.player","catalog.read").enabled)
        denied { registry.requireEnabled("org.test.player","stream.play") }
    }
    @Test fun updatesDisableAndInvalidatePreviouslyIssuedRevision() {
        val registry = ExtensionRegistry(RuntimeEnvironment.getApplication()); registry.install(manifest()); registry.enable("org.test.player",true)
        val grant = registry.requireEnabled("org.test.player","stream.play")
        registry.install(manifest().replace("1.0.0","1.0.1"))
        assertFalse(registry.valid("org.test.player",grant.revision)); assertFalse(registry.entries.value.single().enabled)
    }
    @Test fun disableAndUninstallInvokeRevocationAndSurviveRestart() {
        val context = RuntimeEnvironment.getApplication(); val registry = ExtensionRegistry(context); val revoked = mutableListOf<String>(); registry.revoke = { revoked.add(it) }
        registry.install(manifest()); registry.enable("org.test.player",true); val old = registry.entries.value.single().revision
        registry.enable("org.test.player",false); assertFalse(registry.valid("org.test.player",old)); assertFalse(ExtensionRegistry(context).entries.value.single().enabled)
        registry.remove("org.test.player"); assertEquals(4,revoked.size); assertTrue(ExtensionRegistry(context).entries.value.isEmpty())
    }
    @Test fun registryCapacityAndInputStreamSizeAreBounded() {
        val registry = ExtensionRegistry(RuntimeEnvironment.getApplication())
        repeat(24) { registry.install(manifest().replace("org.test.player", "org.test.player$it")) }
        denied { registry.install(manifest()) }
        denied { java.io.ByteArrayInputStream(ByteArray(101)).readBytesBounded(100) }
        assertEquals(100,java.io.ByteArrayInputStream(ByteArray(100)).readBytesBounded(100).size)
    }
    @Test fun remoteUrlsStayOpaqueAndRequireActiveStreamGrant() {
        val registry = ExtensionRegistry(RuntimeEnvironment.getApplication()); val bridge = ExtensionBridge(registry); registry.install(manifest()); registry.enable("org.test.player",true)
        val uri = bridge.mediaUri("org.test.player","https://music.example/audio?id=2")
        assertEquals("auralis",uri.scheme); assertEquals("https://music.example/audio?id=2",uri.getQueryParameter("url"))
        denied { bridge.mediaUri("org.test.player","https://evil.example/audio") }
        registry.enable("org.test.player",false); denied { bridge.resolve(uri) }
    }
}
