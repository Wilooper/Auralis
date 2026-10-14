package app.auralis.extensions

import org.json.JSONObject

data class CompanionAction(val id: String, val label: String, val icon: String, val location: String)
data class CompanionDescriptor(val name: String, val capabilities: Set<String>, val origins: Set<String>, val actions: List<CompanionAction>) {
    companion object {
        val supported = setOf("ui.actions", "network.fetch", "share.text", "compute.background")
        fun parse(text: String): CompanionDescriptor {
            require(text.toByteArray().size <= 16_384) { "Companion manifest exceeds 16 KiB" }
            checkJsonDepth(text)
            val tokener = org.json.JSONTokener(text)
            val root = tokener.nextValue() as? JSONObject ?: error("Expected manifest object")
            require(tokener.nextClean() == '\u0000')
            require(root.keys().asSequence().all { it in setOf("sdk", "name", "capabilities", "origins", "actions") })
            require(root.getInt("sdk") == 2) { "Unsupported companion SDK" }
            val name = root.getString("name").trim().also { require(it.length in 1..80 && it.none(Char::isISOControl)) }
            fun strings(key: String): List<String> {
                val array = root.getJSONArray(key); require(array.length() <= 12)
                return (0 until array.length()).map { array.getString(it) }.also { require(it.distinct() == it) }
            }
            val capabilities = strings("capabilities").toSet()
            require("ui.actions" in capabilities && capabilities.all { it in supported }) { "Unknown companion capability" }
            val origins = strings("origins").toSet()
            origins.forEach { origin ->
                val uri = java.net.URI(origin)
                require(uri.scheme == "https" && origin == ExtensionManifest.origin(uri) && uri.port in setOf(-1, 443))
                require(!uri.host.equals("localhost", true) && !uri.host.endsWith(".localhost", true))
            }
            require(origins.isEmpty() || "network.fetch" in capabilities)
            require("network.fetch" !in capabilities || origins.isNotEmpty())
            val array = root.getJSONArray("actions"); require(array.length() in 1..12)
            val actions = (0 until array.length()).map { index ->
                val action = array.getJSONObject(index)
                require(action.keys().asSequence().all { it in setOf("id", "label", "icon", "location") })
                CompanionAction(action.getString("id"), action.getString("label"), action.getString("icon"), action.getString("location")).also {
                    require(it.id.matches(Regex("[a-z][a-z0-9_.-]{0,79}")))
                    require(it.label.length in 1..40 && it.label.none(Char::isISOControl))
                    require(it.icon in setOf("info", "lyrics", "playlist", "share", "extension"))
                    require(it.location in setOf("player", "library", "settings"))
                }
            }
            require(actions.map { it.id }.distinct().size == actions.size)
            require(actions.groupBy { it.location }.values.all { it.size <= 4 })
            return CompanionDescriptor(name, capabilities, origins, actions)
        }
    }
}
