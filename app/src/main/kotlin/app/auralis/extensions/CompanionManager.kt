package app.auralis.extensions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import app.auralis.model.ExtensionTaskPolicy
import app.auralis.model.ExtensionRequestGate
import app.reva.sdk.ExtensionWorkerService
import app.reva.sdk.IExtensionHost
import app.reva.sdk.IExtensionResult
import app.reva.sdk.IExtensionWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CompanionEntry(val component: ComponentName, val descriptor: CompanionDescriptor,
    val publisher: String, val identity: String, val grants: Set<String> = emptySet(), val background: Boolean = false) {
    val key: String get() = component.flattenToString()
    val enabled: Boolean get() = "ui.actions" in grants
}
data class CompanionEvent(val elapsed: Long, val name: String, val event: String)
data class CompanionPanel(val name: String, val title: String, val text: String, val share: Boolean,
    val sourceKey: String? = null, val sourceIdentity: String? = null)

/** Installed companion discovery never starts workers. All state mutation is on the main looper. */
class CompanionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("reva-companions-v2", Context.MODE_PRIVATE)
    private val policy = ExtensionTaskPolicy(SystemClock::elapsedRealtime)
    private val mutable = MutableStateFlow<List<CompanionEntry>>(emptyList())
    val entries = mutable.asStateFlow()
    private val history = MutableStateFlow<List<CompanionEvent>>(emptyList())
    val events = history.asStateFlow()
    private val output = MutableStateFlow<CompanionPanel?>(null)
    val panel = output.asStateFlow()
    private val live = MutableStateFlow<Set<String>>(emptySet())
    val running = live.asStateFlow()
    private val sessions = mutableMapOf<String, Session>()
    private val rate = mutableMapOf<String, ArrayDeque<Long>>()
    private val failures = mutableMapOf<String, Int>()
    private val calls = ConcurrentHashMap<Call, String>()
    private val networkSlots = Semaphore(2)
    private var refreshEpoch = 0L
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "Private network addresses are blocked" }
            }
        }).build()
    private data class Session(val entry: CompanionEntry, val connection: ServiceConnection,
        var bound: Boolean = false, var worker: IExtensionWorker? = null,
        var deadline: Job? = null, var networkJob: Job? = null, var verification: Job? = null,
        val cancelled: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false))

    fun refresh() { val epoch = ++refreshEpoch; scope.launch {
        val rows = withContext(Dispatchers.IO) { runCatching { discover() }.getOrDefault(emptyList()) }
        if (epoch != refreshEpoch) return@launch
        sessions.toMap().forEach { (task, session) ->
            if (rows.none { it.key == session.entry.key && it.identity == session.entry.identity }) finish(task, "package changed")
        }
        mutable.value = rows
        rate.keys.retainAll(rows.map { it.key }.toSet())
        failures.keys.retainAll(rows.map { it.key }.toSet())
    } }
    @Suppress("DEPRECATION")
    private fun discover(): List<CompanionEntry> {
        val pm = context.packageManager
        val services = pm.queryIntentServices(Intent(ExtensionWorkerService.DISCOVERY_ACTION), PackageManager.GET_META_DATA)
        return services.sortedBy { it.serviceInfo.packageName }.take(32).mapNotNull { resolved -> runCatching {
            val service = resolved.serviceInfo
            require(service.exported && service.enabled && service.applicationInfo.enabled)
            require(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0) { "Worker must be isolated" }
            require(service.permission == null) { "Worker requires an unsupported Android binding permission" }
            val resource = service.metaData?.getInt(ExtensionWorkerService.MANIFEST_METADATA) ?: 0
            require(resource != 0)
            val text = pm.getResourcesForApplication(service.applicationInfo).openRawResource(resource).use {
                String(it.readBytesBounded(16_384), Charsets.UTF_8)
            }
            val descriptor = CompanionDescriptor.parse(text)
            val info = pm.getPackageInfo(service.packageName, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            require(!signatures.isNullOrEmpty())
            val publisher = signatures.map { digest(it.toByteArray()) }.sorted().joinToString(",")
            val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            val identity = digest("$publisher|$version|${info.lastUpdateTime}|$text".toByteArray())
            val component = ComponentName(service.packageName, service.name)
            require(component.flattenToString().length <= 512)
            val saved = runCatching { JSONObject(prefs.getString(component.flattenToString(), "{}")!!) }.getOrDefault(JSONObject())
            val valid = saved.optString("identity") == identity
            val grants = if (valid) saved.optJSONArray("grants")?.let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet().intersect(descriptor.capabilities)
            }.orEmpty() else emptySet()
            CompanionEntry(component, descriptor, publisher, identity, grants,
                valid && "compute.background" in grants && saved.optBoolean("background"))
        }.getOrNull() }
    }
    fun grant(key: String, grants: Set<String>, background: Boolean = false) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val entry = entries.value.first { it.key == key }
        require(grants.all { it in entry.descriptor.capabilities })
        require(!background || "compute.background" in grants)
        refreshEpoch++
        failures.remove(key)
        policy.revoke(key).forEach { finish(it.task, "permission changed") }
        prefs.edit().putString(key, JSONObject().put("identity", entry.identity).put("grants", JSONArray(grants.toList()))
            .put("background", background).toString()).apply()
        mutable.value = entries.value.map { if (it.key == key) it.copy(grants = grants, background = background) else it }
        record(entry, if (grants.isEmpty()) "disabled" else "permissions updated")
    }
    fun visibility(visible: Boolean) {
        policy.visibility(visible).forEach { finish(it.task, "cancelled: app left foreground") }
        if (visible) refresh()
    }
    fun dismissPanel() { output.value = null }
    fun shareIntent(): Intent? {
        val value = output.value ?: return null
        if (!value.share || entries.value.none { it.key == value.sourceKey && it.identity == value.sourceIdentity && it.enabled && "share.text" in it.grants }) return null
        return Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value.text)
    }
    fun cancelAll() { sessions.keys.toList().forEach { finish(it, "cancelled by user") } }
    fun invoke(key: String, action: CompanionAction, input: JSONObject = JSONObject()) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val entry = entries.value.firstOrNull { it.key == key && it.enabled } ?: return
        require(action in entry.descriptor.actions && input.toString().toByteArray().size <= 16_384)
        val task = UUID.randomUUID().toString()
        try { policy.begin(task, key, entry.background) } catch (error: IllegalStateException) {
            output.value = CompanionPanel(entry.descriptor.name, "Task not started", error.message.orEmpty(), false); return
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val session = sessions[task] ?: return
                if (!valid(task)) { finish(task, "session expired"); return }
                session.verification = scope.launch {
                val verified = withContext(Dispatchers.IO) { runCatching { discover().firstOrNull { it.key == key } }.getOrNull() }
                if (!valid(task) || verified?.identity != entry.identity) {
                    finish(task, "package changed"); refresh(); return@launch
                }
                val worker = IExtensionWorker.Stub.asInterface(binder)
                session.worker = worker
                val completed = java.util.concurrent.atomic.AtomicBoolean(false)
                try { worker.execute(task, action.id, input.toString(), host(task), object : IExtensionResult.Stub() {
                    override fun complete(requestId: String, success: Boolean, payload: String) {
                        if (requestId != task || !completed.compareAndSet(false, true)) return
                        val safePayload = payload.takeIf { it.length <= 16_384 && it.toByteArray().size <= 16_384 }.orEmpty()
                        handler.post {
                        if (requestId != task || !valid(task)) return@post
                        var outcome = if (success) "completed" else "action failed"
                        if (success) {
                            val value = runCatching {
                                checkJsonDepth(safePayload)
                                JSONObject(safePayload).also {
                                    require(it.keys().asSequence().all { field -> field in setOf("title", "text") })
                                    require(it.getString("title").length in 1..80 && it.getString("text").length <= 8000)
                                }
                            }.getOrNull()
                            if (value == null) outcome = "invalid result"
                            output.value = if (value != null) CompanionPanel(entry.descriptor.name, value.getString("title"),
                                value.getString("text"), "share.text" in entry.grants, entry.key, entry.identity) else CompanionPanel(entry.descriptor.name, "Invalid result", "The extension returned an unsupported panel.", false)
                        } else output.value = CompanionPanel(entry.descriptor.name, "Extension action failed", "Open extension diagnostics for the task state.", false)
                        finish(task, outcome)
                    } }
                }) } catch (_: Exception) { finish(task, "worker connection failed") }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) { finish(task, "worker stopped") }
            override fun onBindingDied(name: ComponentName) { finish(task, "binding died") }
            override fun onNullBinding(name: ComponentName) { finish(task, "worker refused binding") }
        }
        val session = Session(entry, connection)
        sessions[task] = session; live.value = sessions.values.map { it.entry.key }.toSet()
        session.deadline = scope.launch { delay(30_000); if (sessions.containsKey(task)) finish(task, "deadline exceeded") }
        session.verification = scope.launch {
        val verified = withContext(Dispatchers.IO) { runCatching { discover().firstOrNull { it.key == key } }.getOrNull() }
        if (!valid(task) || verified?.identity != entry.identity) {
            finish(task, "package changed"); refresh(); return@launch
        }
        try {
            session.bound = context.bindService(Intent(ExtensionWorkerService.DISCOVERY_ACTION).setComponent(entry.component), connection, Context.BIND_AUTO_CREATE)
            if (!session.bound) { finish(task, "worker unavailable"); return@launch }
            record(entry, "started")
        } catch (_: Exception) { finish(task, "binding rejected") }
        }
    }
    private fun valid(task: String): Boolean {
        val session = sessions[task] ?: return false
        return policy.valid(task) && entries.value.any { it.key == session.entry.key && it.enabled && it.identity == session.entry.identity && it.grants == session.entry.grants }
    }
    private fun finish(task: String, reason: String) {
        val session = sessions.remove(task) ?: return
        session.cancelled.set(true)
        policy.finish(task); session.deadline?.cancel(); session.networkJob?.cancel(); session.verification?.cancel()
        calls.entries.filter { it.value == task }.forEach { it.key.cancel() }
        runCatching { session.worker?.cancel(task) }
        if (session.bound) runCatching { context.unbindService(session.connection) }
        live.value = sessions.values.map { it.entry.key }.toSet()
        record(session.entry, reason)
        if (reason in setOf("worker stopped", "binding died", "worker connection failed", "deadline exceeded", "action failed", "invalid result")) {
            val count = (failures[session.entry.key] ?: 0) + 1
            failures[session.entry.key] = count
            if (count >= 3) {
                prefs.edit().remove(session.entry.key).apply()
                refreshEpoch++
                mutable.value = entries.value.map { if (it.key == session.entry.key) it.copy(grants = emptySet(), background = false) else it }
                record(session.entry, "suspended after repeated failures; review access to retry")
            }
        }
    }
    private fun record(entry: CompanionEntry, event: String) {
        history.value = (history.value + CompanionEvent(SystemClock.elapsedRealtime(), entry.descriptor.name, event)).takeLast(100)
    }
    private fun host(task: String) = object : IExtensionHost.Stub() {
        private val admission = ExtensionRequestGate(SystemClock::elapsedRealtime)
        override fun request(taskId: String, requestId: String, operation: String, payload: String, result: IExtensionResult) {
            // Malformed/excess requests are dropped before they can flood the host looper.
            if (taskId != task || operation.length !in 1..80 || requestId.length !in 1..80 || payload.length > 4096 || payload.toByteArray().size > 4096 || !admission.acquire()) return
            handler.post {
            fun reply(ok: Boolean, value: JSONObject) { runCatching { result.complete(requestId, ok, value.toString()) }; admission.release() }
            val session = sessions[task]
            if (taskId != task || !valid(task) || session == null) { reply(false, JSONObject().put("error", "Session revoked")); return@post }
            if (operation != "network.fetch" || operation !in session.entry.grants) { reply(false, JSONObject().put("error", "Capability not granted or unsupported")); return@post }
            val now = SystemClock.elapsedRealtime()
            val times = rate.getOrPut(session.entry.key) { ArrayDeque() }
            while (times.isNotEmpty() && now - times.first() >= 60_000) times.removeFirst()
            if (times.size >= 10) { reply(false, JSONObject().put("error", "Rate limit reached")); return@post }
            times.addLast(now)
            session.networkJob = scope.launch {
                try {
                val response = runCatching { networkSlots.withPermit { withContext(Dispatchers.IO) {
                    check(!session.cancelled.get())
                    checkJsonDepth(payload)
                    val data = JSONObject(payload); require(data.keys().asSequence().toList() == listOf("url"))
                    val uri = ExtensionManifest.allowed(data.getString("url"), session.entry.descriptor.origins)
                    val call = client.newCall(Request.Builder().url(uri.toString()).get().build())
                    calls[call] = task
                    try {
                        if (session.cancelled.get()) call.cancel()
                        call.execute().use { response ->
                        require(response.code in 200..299)
                        require(response.header("Content-Type")?.substringBefore(';')?.lowercase()?.trim() in setOf("application/json", "text/plain"))
                        val bytes = response.body?.byteStream()?.use { it.readBytesBounded(262_144) } ?: error("Empty response")
                        JSONObject().put("status", response.code).put("text", String(bytes, Charsets.UTF_8)).also {
                            require(it.toString().toByteArray().size <= 262_144)
                        }
                    } } finally { calls.remove(call) }
                } } }
                if (valid(task)) {
                    reply(response.isSuccess, response.getOrElse { JSONObject().put("error", "Network request rejected or failed") })
                    record(session.entry, if (response.isSuccess) "network completed" else "network rejected")
                }
                } finally { admission.release() }
            }
        } }
    }
    companion object {
        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        internal fun publicAddress(address: java.net.InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address
            if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
            if (bytes.size == 4 && (bytes[0].toInt() and 255) == 100 && (bytes[1].toInt() and 255) in 64..127) return false
            return true
        }
    }
}
