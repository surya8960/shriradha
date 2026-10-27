package com.example

import java.util.Locale

/**
 * Strict, whole-name matcher. There are NO built-in/default names:
 * only the names the user adds are ever recognized or counted.
 *
 * Rules
 *  - A name counts only when it is spoken COMPLETELY (every word of the name, in order).
 *  - A half name / prefix / extra suffix never counts ("रा", "राध", "राधिका" for "राधा").
 *  - A different name never counts.
 *  - Same name repeated back-to-back counts once per repeat ("राम राम" = 2, "रामराम" = 2).
 *  - Longer names win over shorter ones ("हरे कृष्ण" is 1, not 1 + a separate "कृष्ण").
 */
object NameMatcher {

    const val MIN_NAME_LENGTH = 2

    /** Marker used for words the recognizer was not sure about. Never matches any name. */
    const val UNKNOWN_TOKEN = "[unk]"

    private val DELIMITERS = Regex("[\\s,.:;!?'\"\\-_/।॥()]+")

    /** Lowercase, drop zero-width chars and nukta, chandrabindu -> anusvara. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase(Locale.ROOT)) {
            when (ch) {
                '\u200B', '\u200C', '\u200D', '\uFEFF', '\u093C' -> Unit
                '\u0901' -> sb.append('\u0902')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    fun tokens(text: String): List<String> =
        normalize(text).split(DELIMITERS).filter { it.isNotEmpty() }

    /** Cleans the user's list: trims, drops too-short/duplicate entries. Keeps what the user typed. */
    fun cleanNames(names: Collection<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (raw in names) {
            val shown = raw.trim().replace(Regex("\\s+"), " ")
            val key = tokens(shown).joinToString(" ")
            if (key.replace(" ", "").length < MIN_NAME_LENGTH) continue
            if (seen.add(key)) out.add(shown)
        }
        return out
    }

    private fun nameTokenLists(names: Collection<String>): List<List<String>> =
        cleanNames(names).map { tokens(it) }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.size } // longest name first

    /** How many complete names were spoken in [text]. 0 when nothing (or only half a name) matched. */
    fun countMatches(text: String, names: Collection<String>): Int {
        if (text.isBlank() || names.isEmpty()) return 0
        val spoken = tokens(text)
        if (spoken.isEmpty()) return 0
        val lists = nameTokenLists(names)
        if (lists.isEmpty()) return 0

        var count = 0
        var i = 0
        while (i < spoken.size) {
            var advanced = false
            for (name in lists) {
                val n = name.size
                if (i + n <= spoken.size && spoken.subList(i, i + n) == name) {
                    count++
                    i += n
                    advanced = true
                    break
                }
                // fused repeats of a one-word name: "रामराम" -> 2 (only exact full repeats)
                if (n == 1) {
                    val w = name[0]
                    val t = spoken[i]
                    if (t.length > w.length && t.length % w.length == 0 &&
                        t == w.repeat(t.length / w.length)
                    ) {
                        count += t.length / w.length
                        i++
                        advanced = true
                        break
                    }
                }
            }
            if (!advanced) i++
        }
        return count
    }

    fun isMatch(text: String, names: Collection<String>): Boolean = countMatches(text, names) > 0

    /** Top recognition result only: alternatives are far more likely to be wrong names. */
    fun firstMatchingPhrase(results: List<String>?, names: Collection<String>): String? {
        val top = results?.firstOrNull() ?: return null
        return if (isMatch(top, names)) top else null
    }

    private fun hasDevanagari(s: String): Boolean = s.any { it in '\u0900'..'\u097F' }

    /** Offline Vosk (Hindi model) only knows Devanagari words. */
    fun voskNames(names: Collection<String>): List<String> =
        cleanNames(names).filter { hasDevanagari(it) }.map { normalize(it).replace(DELIMITERS, " ").trim() }.distinct()

    /** Number of spoken syllables (aksharas) of a Devanagari word: "राधा"=2, "कृष्ण"=2. */
    fun syllables(word: String): Int {
        var count = 0
        for (i in word.indices) {
            val c = word[i]
            val isVowel = c in '\u0904'..'\u0914'
            val isConsonant = c in '\u0915'..'\u0939' || c in '\u0958'..'\u095F'
            val joinedToNext = i + 1 < word.length && word[i + 1] == '\u094D'
            if (isVowel || (isConsonant && !joinedToNext)) count++
        }
        return maxOf(1, count)
    }

    /** A full word cannot be spoken faster than this. "रा" (short) is shorter than "राधा". */
    fun minSpokenSeconds(word: String): Double = maxOf(0.15, 0.13 * syllables(word))

    /**
     * Decoy words: the half-forms of each name (and each word of a multi-word name).
     * They are added to the grammar so that a half-sound like "रा" is absorbed by a decoy
     * instead of being forced into "राधा". Decoys are NEVER counted.
     */
    fun decoyWords(names: Collection<String>): List<String> {
        val real = voskNames(names).toSet()
        val out = LinkedHashSet<String>()
        for (name in real) {
            val parts = name.split(" ")
            for (part in parts) {
                for (i in 1 until part.length) {
                    if (part[i - 1] == '\u094D') continue
                    out.add(part.substring(0, i))
                }
                if (parts.size > 1) out.add(part)
            }
        }
        out.removeAll(real)
        return out.toList()
    }

    /** Grammar = user's names + decoys (half names) + [unk]. Pure string building (no org.json). */
    fun buildVoskGrammarJson(names: Collection<String>): String {
        val sb = StringBuilder("[")
        for (n in voskNames(names)) {
            sb.append(jsonString(n)).append(',')
        }
        for (d in decoyWords(names)) {
            sb.append(jsonString(d)).append(',')
        }
        sb.append(jsonString(UNKNOWN_TOKEN)).append(']')
        return sb.toString()
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
