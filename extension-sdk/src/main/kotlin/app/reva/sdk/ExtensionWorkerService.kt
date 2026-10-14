package app.reva.sdk

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Cooperative SDK runner. Host revocation and isolation, not this class alone, protect ReVa. */
abstract class ExtensionWorkerService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var runningId: String? = null
    @Volatile private var running: Future<*>? = null
    protected abstract fun executeAction(action: String, input: JSONObject, host: Host): JSONObject
    class Host internal constructor(private val task: String, private val bridge: IExtensionHost) {
        /** Runs on the worker's task thread; the host network operation is asynchronous and bounded. */
        fun fetch(url: String): JSONObject {
            val ready = java.util.concurrent.CountDownLatch(1)
            var success = false
            var response = JSONObject()
            request(java.util.UUID.randomUUID().toString(), "network.fetch", JSONObject().put("url", url)) { ok, value ->
                success = ok; response = value; ready.countDown()
            }
            check(ready.await(16, TimeUnit.SECONDS)) { "Host request timed out" }
            check(success) { response.optString("error", "Host request failed") }
            return response
        }
        fun request(id: String, operation: String, payload: JSONObject, result: (Boolean, JSONObject) -> Unit) {
            require(id.length in 1..80 && operation.length in 1..80 && payload.toString().toByteArray().size <= 4096)
            bridge.request(task, id, operation, payload.toString(), object : IExtensionResult.Stub() {
                override fun complete(requestId: String, success: Boolean, payload: String) {
                    if (payload.toByteArray().size <= 262_144) result(success, JSONObject(payload))
                }
            })
        }
    }
    private val binder = object : IExtensionWorker.Stub() {
        override fun execute(taskId: String, actionId: String, input: String, host: IExtensionHost, result: IExtensionResult) {
            if (taskId.length !in 1..80 || actionId.length !in 1..80 || input.toByteArray().size > 16_384 || !busy.compareAndSet(false, true)) {
                result.complete(taskId.take(80), false, "{\"error\":\"Invalid or busy worker\"}"); return
            }
            runningId = taskId
            val deadline = timer.schedule({
                // Isolated sample workers have no other app state to preserve.
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 30, TimeUnit.SECONDS)
            running = executor.submit {
                try {
                    val output = executeAction(actionId, JSONObject(input), Host(taskId, host)).toString()
                    require(output.toByteArray().size <= 16_384) { "Result exceeds 16 KiB" }
                    result.complete(taskId, true, output)
                } catch (_: Exception) {
                    runCatching { result.complete(taskId, false, "{\"error\":\"Extension action failed\"}") }
                } finally { deadline.cancel(false); runningId = null; busy.set(false) }
            }
        }
        override fun cancel(taskId: String) { if (taskId == runningId) running?.cancel(true) }
    }
    final override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() { running?.cancel(true); executor.shutdownNow(); timer.shutdownNow(); super.onDestroy() }
    companion object {
        const val DISCOVERY_ACTION = "app.reva.extension.WORKER"
        const val MANIFEST_METADATA = "app.reva.extension.MANIFEST"
        const val PROTOCOL_VERSION = 2
    }
}
