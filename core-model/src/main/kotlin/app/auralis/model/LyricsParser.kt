package app.auralis.model

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object LyricsParser {
    const val MAX_TEXT_BYTES = 1024 * 1024
    fun parse(text: String, source: String = "Lyrics file"): Lyrics? {
        val clean = text.removePrefix("\uFEFF").trim().replace("\r\n", "\n").replace('\r', '\n')
        if (clean.isBlank() || clean.length > MAX_TEXT_BYTES) return null
        if (clean.startsWith("<")) return parseTtml(clean)?.copy(source = source)
        val lrc = LrcParser.parse(clean)
        if (lrc.lines.isNotEmpty()) return lrc.copy(source = source)
        return Lyrics(clean.lines().map { LyricLine(-1, it) }, timing = LyricsTiming.PLAIN, source = source)
    }

    private fun parseTtml(text: String): Lyrics? {
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Android DOM factories may not expose these SAX features. DOCTYPE/ENTITY
                // declarations are rejected before parsing, and resolution is disabled below.
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            val document = builder.parse(InputSource(StringReader(text)))
            if (document.documentElement.localName != "tt") return null
            val pending = java.util.ArrayDeque<Pair<Node, Int>>()
            pending.add(document.documentElement to 0)
            var nodes = 0
            while (pending.isNotEmpty()) {
                val (node, depth) = pending.removeFirst()
                if (++nodes > 50_000 || depth > 64) return null
                for (i in 0 until node.childNodes.length) pending.add(node.childNodes.item(i) to depth + 1)
            }
            val root = document.documentElement
            val frameRate = root.getAttributeNS("http://www.w3.org/ns/ttml#parameter", "frameRate").toDoubleOrNull() ?: 30.0
            val tickRate = root.getAttributeNS("http://www.w3.org/ns/ttml#parameter", "tickRate").toDoubleOrNull() ?: 1.0
            if (frameRate <= 0 || tickRate <= 0) return null
            fun time(value: String, parent: Long): Long? {
                if (value.isBlank()) return null
                if (value.contains(':')) {
                    val parts = value.split(':')
                    if (parts.size !in 3..4) return null
                    val hours = parts[0].toLongOrNull() ?: return null
                    val minutes = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                    val seconds = parts[2].toDoubleOrNull()?.takeIf { it in 0.0..<60.0 } ?: return null
                    val frames = if (parts.size == 4) (parts[3].toDoubleOrNull() ?: return null) / frameRate else 0.0
                    return ((hours * 3600 + minutes * 60 + seconds + frames) * 1000).toLong()
                }
                val match = Regex("(\\d+(?:\\.\\d+)?)(ms|s|m|h|f|t)").matchEntire(value) ?: return null
                val factor = when (match.groupValues[2]) {
                    "ms" -> 1.0; "s" -> 1000.0; "m" -> 60_000.0; "h" -> 3_600_000.0
                    "f" -> 1000 / frameRate; else -> 1000 / tickRate
                }
                return parent + (match.groupValues[1].toDouble() * factor).toLong()
            }
            fun start(element: Element): Long {
                val parent = element.parentNode as? Element
                val inherited = parent?.let { start(it) } ?: 0
                return time(element.getAttribute("begin"), inherited) ?: inherited
            }
            val paragraphs = document.getElementsByTagNameNS("*", "p")
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until paragraphs.length.coerceAtMost(10_000)) {
                val p = paragraphs.item(i) as Element
                val lineStart = start(p)
                val end = time(p.getAttribute("end"), (p.parentNode as? Element)?.let { start(it) } ?: 0)
                    ?: time(p.getAttribute("dur"), lineStart)
                val timed = p.hasAttribute("begin") || p.getElementsByTagNameNS("*", "span").let { spans ->
                    (0 until spans.length).any { (spans.item(it) as Element).hasAttribute("begin") }
                } || generateSequence(p.parentNode as? Element) { it.parentNode as? Element }.any { it.hasAttribute("begin") }
                val tokens = mutableListOf<LyricWord>()
                fun collect(node: Node, inheritedStart: Long, inheritedEnd: Long?) {
                    when (node.nodeType) {
                        Node.TEXT_NODE -> if (node.nodeValue.isNotEmpty()) tokens += LyricWord(node.nodeValue, inheritedStart, inheritedEnd)
                        Node.ELEMENT_NODE -> {
                            val element = node as Element
                            if (element.localName == "br") tokens += LyricWord("\n", inheritedStart, inheritedEnd)
                            else {
                                val nodeStart = time(element.getAttribute("begin"), inheritedStart) ?: inheritedStart
                                val nodeEnd = time(element.getAttribute("end"), inheritedStart)
                                    ?: time(element.getAttribute("dur"), nodeStart) ?: inheritedEnd
                                for (j in 0 until node.childNodes.length) collect(node.childNodes.item(j), nodeStart, nodeEnd)
                            }
                        }
                    }
                }
                for (j in 0 until p.childNodes.length) collect(p.childNodes.item(j), lineStart, end)
                val content = tokens.joinToString("") { it.text }.trim()
                if (content.isNotEmpty()) lines += LyricLine(if (timed) lineStart else -1, content,
                    if (timed && tokens.any { it.timeMs != lineStart }) tokens else emptyList(), end)
            }
            if (lines.isEmpty()) null else if (lines.any { it.timeMs >= 0 }) Lyrics(lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs })
            else Lyrics(lines, timing = LyricsTiming.PLAIN)
        } catch (_: Exception) { null }
    }
}
