package app.auralis.model

enum class LyricsTiming { PLAIN, LINE, WORD }
data class LyricWord(val text: String, val timeMs: Long, val endTimeMs: Long? = null)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList(), val endTimeMs: Long? = null)
data class Lyrics(
    val lines: List<LyricLine>,
    val offsetMs: Long = 0,
    val timing: LyricsTiming = if (lines.any { it.words.isNotEmpty() }) LyricsTiming.WORD else LyricsTiming.LINE,
    val source: String = "Lyrics file",
) {
    val isTimed: Boolean get() = timing != LyricsTiming.PLAIN
    fun activeIndex(positionMs: Long): Int {
        if (!isTimed) return -1
        val time = positionMs + offsetMs
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= time) { found = mid; low = mid + 1 }
            else high = mid - 1
        }
        return if (found >= 0 && lines[found].endTimeMs?.let { time >= it } == true) -1 else found
    }
}

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val wordTimestamp = Regex("<(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    fun parse(text: String): Lyrics {
        var offsetMs = 0L
        val lines = mutableListOf<LyricLine>()
        text.lineSequence().forEach { raw ->
            offset.find(raw)?.groupValues?.get(1)?.toLongOrNull()?.let { offsetMs = it }
            val tags = timestamp.findAll(raw).toList()
            val content = if (tags.isEmpty()) "" else raw.substring(tags.last().range.last + 1).trim()
            val wordTags = wordTimestamp.findAll(content).filter { it.groupValues[2].toInt() < 60 }.toList()
            val words = wordTags.mapIndexedNotNull { index, wordTag ->
                val next = wordTags.getOrNull(index + 1)
                val part = content.substring(wordTag.range.last + 1, next?.range?.first ?: content.length)
                if (part.isEmpty()) null else LyricWord(part, time(wordTag), next?.let(::time))
            }
            tags.forEach { tag ->
                val seconds = tag.groupValues[2].toInt()
                if (seconds < 60) {
                    val start = time(tag)
                    val prefix = if (wordTags.isEmpty()) "" else content.substring(0, wordTags.first().range.first)
                    val shift = start - time(tags.first())
                    val shifted = (if (prefix.isEmpty()) words else listOf(LyricWord(prefix, time(tags.first()))) + words)
                        .map { it.copy(timeMs = it.timeMs + shift, endTimeMs = it.endTimeMs?.plus(shift)) }
                    if (shifted.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }) {
                        val end = wordTags.lastOrNull()?.takeIf { it.range.last == content.lastIndex }?.let { time(it) + shift }
                        lines += LyricLine(start, if (shifted.isEmpty()) content else shifted.joinToString("") { it.text }, shifted, end)
                    } else lines += LyricLine(start, wordTimestamp.replace(content, ""))
                }
            }
        }
        return Lyrics(lines.sortedBy { it.timeMs }, offsetMs)
    }

    private fun time(tag: MatchResult): Long {
        val fraction = tag.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
        return tag.groupValues[1].toLong() * 60_000 + tag.groupValues[2].toLong() * 1000 + fraction
    }
}
