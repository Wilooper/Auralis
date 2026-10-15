package app.auralis.extensions

import android.app.Application
import app.reva.sdk.ExtensionProtocol
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExtensionProtocolTest {
    @Test fun malformedDuplicateDeepAndTrailingJsonCannotCrossTheBridge() {
        val bad = listOf("{} {}", "[]", "{'x':1}", "{x:1}", "{\"x\":1,\"x\":2}", "{\"x\":NaN}", "{\"x\":1e9999}",
            "{\"x\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}")
        bad.forEach { text -> assertTrue(text, runCatching { ExtensionProtocol.objectValue(text) }.isFailure) }
        assertEquals("hello\nworld", ExtensionProtocol.objectValue("{\"text\":\"hello\\nworld\"}").getString("text"))
    }
    @Test fun limitsCountUtf8BytesRatherThanOnlyCharacters() {
        assertTrue(runCatching { ExtensionProtocol.objectValue("{\"x\":\"${"é".repeat(8192)}\"}") }.isFailure)
        assertTrue(runCatching { ExtensionProtocol.objectValue("{\"x\":\"${"🙂".repeat(5000)}\"}") }.isFailure)
        assertEquals("प्यार", ExtensionProtocol.objectValue("{\"text\":\"प्यार\"}").getString("text"))
    }
    @Test fun panelsRequireTextFieldsWithoutCoercionOrExtraOperations() {
        for (text in listOf("{\"title\":1,\"text\":\"ok\"}", "{\"title\":\"Info\",\"text\":null}", "{\"title\":\"Info\",\"text\":\"ok\",\"exec\":true}", "{\"title\":\" \",\"text\":\"ok\"}")) {
            assertTrue(runCatching { ExtensionProtocol.panel(text) }.isFailure)
        }
        assertEquals("Info", ExtensionProtocol.panel("{\"title\":\"Info\",\"text\":\"line one\\nline two\"}").getString("title"))
    }
}
