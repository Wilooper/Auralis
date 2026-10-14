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

/** SDK transport tests, not a substitute for cross-package isolated-process device tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompanionWorkerTest {
    class EchoWorker : ExtensionWorkerService() {
        override fun executeAction(action: String, input: JSONObject, host: Host): JSONObject {
            check(action == "echo")
            return JSONObject().put("title", "Echo").put("text", input.getString("text"))
        }
    }
    private val host = object : IExtensionHost.Stub() {
        override fun request(taskId: String, requestId: String, operation: String, payload: String, result: IExtensionResult) {
            error("Echo worker must not request host access")
        }
    }
    @Test fun actionReturnsABoundedPanelAndWorkerExceptionsStayOutOfTheHost() {
        for ((action, expected) in listOf("echo" to true, "unsupported" to false)) {
            val controller = Robolectric.buildService(EchoWorker::class.java).create()
            try {
            val worker = IExtensionWorker.Stub.asInterface(controller.get().onBind(Intent()))
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
            } finally { controller.destroy() }
        }
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
}
