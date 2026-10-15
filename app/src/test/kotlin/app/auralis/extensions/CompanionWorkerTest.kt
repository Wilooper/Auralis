package app.auralis.extensions

import android.app.Application
import android.content.Intent
import app.reva.sdk.ExtensionWorkerService
import app.reva.sdk.IExtensionHost
import app.reva.sdk.IExtensionResult
import app.reva.sdk.IExtensionWorker
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** SDK transport tests, not a substitute for cross-package isolated-process device tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompanionWorkerTest {
    class EchoWorker : ExtensionWorkerService() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hostReplies = AtomicInteger()
        override fun executeAction(action: String, input: JSONObject, host: Host): JSONObject {
            if (action == "block") { entered.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
            else if (action == "invalid") return JSONObject().put("title", 7).put("text", "Invalid panel")
            else if (action == "huge") return JSONObject().put("title", "Huge").put("text", "x".repeat(17_000))
            else if (action == "host") {
                val ready = CountDownLatch(1)
                host.request("r1", "network.fetch", JSONObject().put("url", "https://example.org")) { _, _ -> hostReplies.incrementAndGet(); ready.countDown() }
                check(ready.await(2, TimeUnit.SECONDS))
            } else check(action == "echo")
            return JSONObject().put("title", "Echo").put("text", input.getString("text"))
        }
    }
    private class Result : IExtensionResult.Stub() {
        val ready = CountDownLatch(1)
        val calls = AtomicInteger()
        @Volatile var success = false
        @Volatile var payload = ""
        override fun complete(requestId: String, success: Boolean, payload: String) {
            calls.incrementAndGet(); this.success = success; this.payload = payload; ready.countDown()
        }
        fun await() { assertTrue("Worker did not return", ready.await(2, TimeUnit.SECONDS)) }
    }
    private val host = object : IExtensionHost.Stub() {
        override fun request(taskId: String, requestId: String, operation: String, payload: String, result: IExtensionResult) {
            error("Echo worker must not request host access")
        }
    }
    @Test fun actionReturnsABoundedPanelAndWorkerExceptionsStayOutOfTheHost() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        try {
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
            for ((action, expected) in listOf("echo" to true, "unsupported" to false, "echo" to true)) {
                val done = CountDownLatch(1)
                var ok = !expected
                var output = ""
                worker.execute("task", action, "{\"text\":\"hello\"}", host, object : IExtensionResult.Stub() {
                    override fun complete(requestId: String, success: Boolean, payload: String) {
                        ok = success; output = payload; done.countDown()
                    }
                })
                assertTrue(done.await(2, TimeUnit.SECONDS))
                assertEquals(expected, ok)
                assertEquals(if (expected) "hello" else "Extension action failed", JSONObject(output).getString(if (expected) "text" else "error"))
            }
        } finally { controller.destroy() }
    }
    @Test fun oversizedInputsAreRejectedBeforeExecution() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        try {
            var accepted = true
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
            worker.execute("task", "echo", "x".repeat(16_385), host, object : IExtensionResult.Stub() {
                override fun complete(requestId: String, success: Boolean, payload: String) { accepted = success }
            })
            assertFalse(accepted)
        } finally { controller.destroy() }
    }
    @Test fun malformedInputAndInvalidOutputReturnOnlyAStableError() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        try {
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
            for ((action, input) in listOf("echo" to "{\"text\":\"ok\"} trailing", "echo" to "{\"text\":\"a\",\"text\":\"b\"}", "invalid" to "{}", "huge" to "{}")) {
                val result = Result(); worker.execute("task", action, input, host, result); result.await()
                assertFalse(result.success); assertEquals(1, result.calls.get())
                assertEquals("Extension action failed", JSONObject(result.payload).getString("error"))
            }
        } finally { controller.destroy() }
    }
    @Test fun terminalCallbackCanImmediatelySubmitTheNextAction() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        try {
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
            val second = Result(); val firstSucceeded = AtomicBoolean(false)
            worker.execute("first", "echo", "{\"text\":\"one\"}", host, object : IExtensionResult.Stub() {
                override fun complete(requestId: String, success: Boolean, payload: String) {
                    firstSucceeded.set(success)
                    worker.execute("second", "echo", "{\"text\":\"two\"}", host, second)
                }
            })
            second.await(); assertTrue(firstSucceeded.get()); assertTrue(second.success)
            assertEquals("two", JSONObject(second.payload).getString("text"))
        } finally { controller.destroy() }
    }
    @Test fun cancellationDoesNotDeliverTwiceOrLeaveTheWorkerBusy() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        try {
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
            val first = Result(); worker.execute("first", "block", "{\"text\":\"one\"}", host, first)
            assertTrue(controller.get().entered.await(2, TimeUnit.SECONDS))
            val busy = Result(); worker.execute("busy", "echo", "{}", host, busy); busy.await(); assertFalse(busy.success)
            worker.cancel("first"); first.await(); worker.cancel("first")
            assertFalse(first.success); assertEquals("Task cancelled", JSONObject(first.payload).getString("error"))
            assertEquals(1, first.calls.get())
            val next = Result(); worker.execute("next", "echo", "{\"text\":\"next\"}", host, next); next.await(); assertTrue(next.success)
        } finally { controller.get().release.countDown(); controller.destroy() }
    }
    @Test fun wrongDuplicateAndLateHostRepliesAreIgnored() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        var callback: IExtensionResult? = null
        val replies = object : IExtensionHost.Stub() {
            override fun request(taskId: String, requestId: String, operation: String, payload: String, result: IExtensionResult) {
                callback = result
                result.complete("wrong-id", true, "{}")
                result.complete(requestId, true, "{}")
                result.complete(requestId, true, "{}")
            }
        }
        try {
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
            val result = Result(); worker.execute("task", "host", "{\"text\":\"ok\"}", replies, result); result.await()
            assertTrue(result.success); callback!!.complete("r1", true, "{}")
            assertEquals(1, controller.get().hostReplies.get())
        } finally { controller.destroy() }
    }
    @Test fun destroyedWorkerRejectsNewActions() {
        val controller = Robolectric.buildService(EchoWorker::class.java).create()
        val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
        controller.destroy()
        val result = Result(); worker.execute("task", "echo", "{\"text\":\"ok\"}", host, result); result.await()
        assertFalse(result.success); assertEquals(1, result.calls.get())
    }
}
