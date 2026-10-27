package app.reva.sdk

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Cooperative runner. Android isolation and the host broker remain the security boundaries. */
abstract class ExtensionWorkerService : Service() {
    private val lock = Any()
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(1))
    private val timer = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private var active: Task? = null
    @Volatile private var closed = false
    private class Task(val id: String, val result: IExtensionResult) {
        val terminal = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val started = AtomicBoolean(false)
        lateinit var future: FutureTask<Unit>
        var deadline: ScheduledFuture<*>? = null
    }
    protected abstract fun executeAction(action: String, input: JSONObject, host: Host): JSONObject

    class Host internal constructor(private val task: String, private val bridge: IExtensionHost, private val valid: () -> Boolean) {
        /** Call on a worker thread. Network I/O remains asynchronous in the host. */
        fun fetch(url: String): JSONObject {
            val ready = CountDownLatch(1)
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
            check(valid()) { "Task is no longer active" }
            val text = payload.toString()
            require(id.length in 1..80 && operation.length in 1..80)
            ExtensionProtocol.objectValue(text, ExtensionProtocol.MAX_REQUEST_BYTES)
            val delivered = AtomicBoolean(false)
            bridge.request(task, id, operation, text, object : IExtensionResult.Stub() {
                override fun complete(requestId: String, success: Boolean, payload: String) {
                    if (requestId != id || !valid() || !delivered.compareAndSet(false, true)) return
                    val response = runCatching { ExtensionProtocol.objectValue(payload, ExtensionProtocol.MAX_REPLY_BYTES) }.getOrNull()
                    result(success && response != null, response ?: JSONObject().put("error", "Invalid host response"))
                }
            })
        }
    }
    private val binder = object : IExtensionWorker.Stub() {
        override fun execute(taskId: String, actionId: String, input: String, host: IExtensionHost, result: IExtensionResult) {
            if (taskId.length !in 1..80 || actionId.length !in 1..80 || input.length > ExtensionProtocol.MAX_INPUT_BYTES || input.toByteArray().size > ExtensionProtocol.MAX_INPUT_BYTES) {
                reply(result, taskId.take(80), false, "{\"error\":\"Invalid worker request\"}"); return
            }
            val task = Task(taskId, result)
            task.future = FutureTask {
                task.started.set(true)
                var success = false
                var output = "{\"error\":\"Extension action failed\"}"
                try {
                    check(!task.cancelled.get())
                    val value = executeAction(actionId, ExtensionProtocol.objectValue(input), Host(taskId, host) { !closed && !task.cancelled.get() && !task.terminal.get() })
                    output = ExtensionProtocol.panel(value.toString()).toString(); success = true
                } catch (_: Exception) { /* Send a stable error, never an exception/credential payload. */ }
                finally { finish(task, success, output) }
                Unit
            }
            val accepted = synchronized(lock) {
                if (closed || active != null) false else {
                    active = task
                    try {
                        task.deadline = timer.schedule({
                            if (task.terminal.compareAndSet(false, true)) android.os.Process.killProcess(android.os.Process.myPid())
                        }, 30, TimeUnit.SECONDS)
                        executor.execute(task.future)
                        true
                    } catch (_: Exception) {
                        active = null; task.deadline?.cancel(false); false
                    }
                }
            }
            if (!accepted) reply(result, taskId, false, "{\"error\":\"Worker busy or closed\"}")
        }
        override fun cancel(taskId: String) { cancelTask(taskId) }
    }
    private fun finish(task: Task, success: Boolean, output: String) {
        if (!task.terminal.compareAndSet(false, true)) return
        task.deadline?.cancel(false)
        synchronized(lock) { if (active === task) active = null }
        // Release admission before the callback: its receiver may immediately submit the next action.
        reply(task.result, task.id, success && !task.cancelled.get(), if (task.cancelled.get()) "{\"error\":\"Task cancelled\"}" else output)
    }
    private fun cancelTask(id: String) {
        val task = synchronized(lock) { active?.takeIf { it.id == id } } ?: return
        task.cancelled.set(true); task.future.cancel(true)
        if (!task.started.get()) {
            executor.remove(task.future)
            finish(task, false, "{\"error\":\"Task cancelled\"}")
        }
    }
    private fun reply(result: IExtensionResult, id: String, success: Boolean, payload: String) {
        runCatching { result.complete(id, success, payload) }
    }
    final override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        closed = true
        synchronized(lock) { active?.id }?.let(::cancelTask)
        executor.shutdownNow()
        // A running action that ignores interruption retains its already scheduled watchdog.
        timer.shutdown()
        super.onDestroy()
    }
    companion object {
        const val DISCOVERY_ACTION = "app.reva.extension.WORKER"
        const val MANIFEST_METADATA = "app.reva.extension.MANIFEST"
        const val PROTOCOL_VERSION = 2
    }
}
