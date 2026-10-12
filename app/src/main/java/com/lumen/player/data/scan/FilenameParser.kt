package com.lumen.player.data.scan

/**
 * Turns names like "Alina Baraz & Galimatias - Make You Feel (Cover Art).mp3" or
 * "Assassins_Creed_3_Lorne_Balfe_-_(getmp3.pro).mp3" into a clean artist + title.
 */
object FilenameParser {
    data class Parsed(val artist: String, val title: String)

    private val noise = listOf(
        "official music video", "official video", "official audio", "official lyric video", "lyric video", "lyrics video",
        "lyrics", "lyric", "audio", "visualizer", "visualiser", "music video", "video", "full hd song", "full hd", "hd", "hq",
        "4k", "explicit", "clean", "cover art", "with lyrics", "letra", "remastered", "remaster", "kbps", "320", "128",
        "getmp3", "mp3", "download", "free download", "out now", "premiere", "stem player audio",
    )
    private val bracket = Regex("""[\(\[【『「]([^\)\]】』」]*)[\)\]】』」]""")
    private val sites = Regex("""(?i)[-_ ]*\(?\s*(?:www\.)?[a-z0-9-]+(?:mp3|tube|downloader|converter)[a-z0-9-]*\.(?:com|pro|net|io|org|cc|to)\s*\)?""")
    private val bitrate = Regex("""(?i)\(?\b\d{2,3}\s?kbps\)?""")
    private val shouting = Regex("""(?i)!+[^!]*!+""")

    fun parse(fileName: String): Parsed {
        var s = fileName.substringBeforeLast('.')
        s = s.replace('_', ' ').replace(Regex("""\s+"""), " ").trim()
        s = sites.replace(s, " ")
        s = bitrate.replace(s, " ")
        s = shouting.replace(s, " ")
        // Drop bracket groups that are pure noise, keep ones that carry meaning (feat., remix, version…)
        s = bracket.replace(s) { m ->
            val inner = m.groupValues[1].trim().lowercase()
            val keep = inner.startsWith("feat") || inner.startsWith("ft.") || inner.contains("remix") ||
                inner.contains("version") || inner.contains("cover") && !inner.contains("cover art") ||
                inner.contains("instrumental") || inner.contains("acoustic") || inner.contains("live") || inner.contains("edit")
            if (!keep && (inner.isEmpty() || noise.any { inner.contains(it) } || inner.matches(Regex("""[\d\s.-]+""")))) " " else m.value
        }
        s = s.replace(Regex("""(?i)\b(official\s+)?(music\s+)?(lyric\s+)?video\b"""), " ")
        s = s.replace(Regex("""(?i)\blyrics\b"""), " ")
        s = s.replace(Regex("""\s+"""), " ").trim().trim('-', '|', '•', '·', ' ')

        val seps = listOf(" - ", " – ", " — ", " | ", " -", "- ")
        for (sep in seps) {
            val i = s.indexOf(sep)
            if (i > 0 && i < s.length - sep.length) {
                val a = s.substring(0, i).trim()
                val t = s.substring(i + sep.length).trim().trim('-', ' ')
                if (a.isNotEmpty() && t.isNotEmpty() && a.length <= 60) return Parsed(tidy(a), tidy(t))
            }
        }
        return Parsed("", tidy(s.ifBlank { fileName.substringBeforeLast('.') }))
    }

    private fun tidy(x: String): String = x.replace(Regex("""\s+"""), " ").trim().trim('-', '_', ' ', '.')

    /** Loose similarity used to sanity-check online matches (0..1). */
    fun similarity(a: String, b: String): Double {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (x.contains(y) || y.contains(x)) return 0.9
        val tx = x.split(' ').toSet(); val ty = y.split(' ').toSet()
        return tx.intersect(ty).size.toDouble() / tx.union(ty).size
    }

    fun norm(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("""\b(feat|ft)\.?\b.*"""), " ")
        .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()
}
