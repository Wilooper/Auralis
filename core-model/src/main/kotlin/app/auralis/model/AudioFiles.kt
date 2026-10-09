package app.auralis.model

object AudioFiles {
    private val extensions = setOf(
        "mp3", "mp2", "mp1", "aac", "m4a", "m4b", "m4p", "flac", "wav", "wave", "aif", "aiff", "aifc",
        "ogg", "oga", "opus", "spx", "wma", "ape", "wv", "tta", "tak", "mka", "ac3", "eac3", "dts",
        "dsf", "dff", "amr", "awb", "au", "snd", "caf", "a52", "mid", "midi", "mod", "xm", "it", "s3m", "mtm", "3ga",
    )
    fun isAudio(name: String, mime: String?): Boolean =
        mime?.startsWith("audio/", ignoreCase = true) == true || name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in extensions
}
