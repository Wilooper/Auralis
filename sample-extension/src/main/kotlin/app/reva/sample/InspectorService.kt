package app.reva.sample

import app.reva.sdk.ExtensionWorkerService
import org.json.JSONObject

/** No network, AI, storage or audio effects: a small native companion protocol proof. */
class InspectorService : ExtensionWorkerService() {
    override fun executeAction(action: String, input: JSONObject, host: Host): JSONObject = when (action) {
        "inspect" -> JSONObject().put("title", "Track info").put("text",
            "${input.optString("title", "No track selected")}\n${input.optString("artist", "Unknown artist")}\n\nThis action ran in a separate isolated companion worker. Only the selected title and artist were supplied by ReVa.")
        "about" -> JSONObject().put("title", "Track Inspector").put("text", "Companion SDK v2 proof. No Android permissions, internet service, AI model or audio effect is included.")
        else -> error("Unknown action")
    }
}
