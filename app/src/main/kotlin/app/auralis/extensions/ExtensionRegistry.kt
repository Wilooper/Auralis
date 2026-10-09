package app.auralis.extensions

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ExtensionRegistry(private val context: Context) {
    data class Entry(val manifest: ExtensionManifest, val enabled: Boolean, val revision: Long, val grants: Set<String> = emptySet())
    private val prefs = context.getSharedPreferences("extensions-v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val entries = mutable.asStateFlow()
    var revoke: (String) -> Unit = {}
    private fun load(): List<Entry> = runCatching {
        val rows = JSONArray(prefs.getString("entries", "[]"))
        (0 until minOf(rows.length(), 24)).map { val r = rows.getJSONObject(it); Entry(ExtensionManifest.parse(r.getString("manifest")), r.optBoolean("enabled"), r.optLong("revision", 1), r.optJSONArray("grants")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()) }
    }.getOrDefault(emptyList())
    @Synchronized private fun save(rows: List<Entry>) {
        val encoded = JSONArray().apply { rows.forEach { put(JSONObject().put("manifest", it.manifest.raw).put("enabled", it.enabled).put("revision", it.revision).put("grants", JSONArray(it.grants.toList()))) } }
        check(prefs.edit().putString("entries", encoded.toString()).commit()) { "Could not save extension settings" }
        mutable.value = rows
    }
    fun importFile(uri: Uri): Entry {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(ExtensionManifest.MAX_BYTES) } ?: error("Could not open extension")
        return install(String(bytes, Charsets.UTF_8))
    }
    @Synchronized fun install(text: String): Entry {
        val manifest = ExtensionManifest.parse(text)
        val previous = entries.value.firstOrNull { it.manifest.id == manifest.id }
        require(previous != null || entries.value.size < 24) { "Maximum 24 installed extensions" }
        val entry = Entry(manifest, false, (previous?.revision ?: 0) + 1)
        save(entries.value.filterNot { it.manifest.id == manifest.id } + entry)
        revoke(manifest.id)
        return entry
    }
    @Synchronized fun enable(id: String, enabled: Boolean, grants: Set<String> = entries.value.firstOrNull { it.manifest.id == id }?.manifest?.capabilities.orEmpty()) {
        val entry = entries.value.firstOrNull { it.manifest.id == id } ?: error("Unknown extension")
        require(grants.all { it in entry.manifest.capabilities } && (!enabled || grants.isNotEmpty())) { "Select at least one declared capability" }
        val updated = entry.copy(enabled = enabled, revision = entry.revision+1, grants = if(enabled) grants else emptySet())
        save(if(enabled) entries.value.filterNot { it.manifest.id == id } + updated else entries.value.map { if(it.manifest.id == id) updated else it })
        revoke(id)
    }
    @Synchronized fun remove(id: String) {
        save(entries.value.filterNot { it.manifest.id == id }); revoke(id)
        prefs.edit().remove("token:$id").apply()
    }
    fun requireEnabled(id: String, capability: String): Entry = entries.value.firstOrNull { it.manifest.id == id }?.also {
        check(it.enabled && capability in it.grants) { "Extension permission revoked or missing: $capability" }
    } ?: error("Extension is not installed")
    fun valid(id: String, revision: Long) = entries.value.any { it.manifest.id == id && it.enabled && it.revision == revision }
    val activeSkin: Skin? get() = entries.value.lastOrNull { it.enabled && "skin.apply" in it.grants && it.manifest.skin != null }?.manifest?.skin
    @Synchronized fun saveToken(id: String, token: String) {
        require(token.length <= 4096 && token.none { it == '\r' || it == '\n' }) { "Invalid token" }
        enable(id, false) // Changing credentials requires a fresh grant.
        if(token.isBlank()) prefs.edit().remove("token:$id").commit()
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
            val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)
            check(prefs.edit().putString("token:$id", encoded).commit())
        }
    }
    fun token(id: String): String? = prefs.getString("token:$id", null)?.let {
        val bytes = Base64.decode(it, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("auralis-extension-token-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("auralis-extension-token-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
internal fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while(true) { val n = read(buffer); if(n == -1) break; require(output.size()+n <= max) { "Response/file exceeds $max bytes" }; output.write(buffer, 0, n) }
    return output.toByteArray()
}
