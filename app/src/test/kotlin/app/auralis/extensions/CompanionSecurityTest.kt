package app.auralis.extensions

import android.app.Application
import androidx.core.graphics.ColorUtils
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompanionSecurityTest {
    private fun manifest() = JSONObject("""{"sdk":2,"name":"Test","capabilities":["ui.actions"],"origins":[],"actions":[{"id":"info","label":"Info","icon":"info","location":"player"}]}""")
    @Test fun unsupportedExecutablesAndCapabilitiesCannotEnterTheManifest() {
        for (field in listOf("code", "libraries", "commands")) {
            assertThrows(IllegalArgumentException::class.java) { CompanionDescriptor.parse(manifest().put(field, "exec").toString()) }
        }
        assertThrows(IllegalArgumentException::class.java) { CompanionDescriptor.parse(manifest().put("capabilities", org.json.JSONArray(listOf("ui.actions", "engine.replace"))).toString()) }
        assertThrows(IllegalArgumentException::class.java) { CompanionDescriptor.parse(manifest().put("sdk", 3).toString()) }
    }
    @Test fun originsNeedTheirOwnCapabilityAndExactHttpsOrigin() {
        val value = manifest().put("capabilities", org.json.JSONArray(listOf("ui.actions", "network.fetch")))
        for (origin in listOf("http://example.org", "https://example.org/path", "https://user:pass@example.org", "https://localhost", "https://example.org:8443")) {
            assertThrows(IllegalArgumentException::class.java) { CompanionDescriptor.parse(value.put("origins", org.json.JSONArray(listOf(origin))).toString()) }
        }
        assertEquals(setOf("https://example.org"), CompanionDescriptor.parse(value.put("origins", org.json.JSONArray(listOf("https://example.org"))).toString()).origins)
    }
    @Test fun publicDnsFilterRejectsLocalIpv4AndIpv6() {
        for (address in listOf("127.0.0.1", "0.0.0.0", "10.1.2.3", "192.168.1.1", "172.16.1.1", "169.254.1.1", "100.64.1.1", "::1", "fc00::1", "fe80::1")) {
            assertFalse(address, CompanionManager.publicAddress(InetAddress.getByName(address)))
        }
        assertTrue(CompanionManager.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(CompanionManager.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun actionContributionsRejectUnknownLocationsAndDuplicates() {
        val value = manifest()
        val action = value.getJSONArray("actions").getJSONObject(0)
        action.put("location", "anywhere")
        assertThrows(IllegalArgumentException::class.java) { CompanionDescriptor.parse(value.toString()) }
        action.put("location", "player"); value.getJSONArray("actions").put(action)
        assertThrows(IllegalArgumentException::class.java) { CompanionDescriptor.parse(value.toString()) }
    }
    @Test fun stockSkinIsLightWithReadableTextAndDarkSkinsRemainValid() {
        val stock = Skin.parse(JSONObject())
        assertEquals("#FAF8F5", stock.background); assertFalse(stock.atmosphere)
        for (foreground in listOf(stock.text, stock.muted)) assertTrue(ColorUtils.calculateContrast(android.graphics.Color.parseColor(foreground), android.graphics.Color.parseColor(stock.background)) >= 4.5)
        assertEquals("#09090C", Skin.parse(JSONObject().put("background", "#09090C").put("surface", "#19191F").put("text", "#F8F6F7").put("muted", "#ADABB6")).background)
        assertEquals("#F8F6F7", Skin.parse(JSONObject().put("background", "#09090C")).text)
    }
}
