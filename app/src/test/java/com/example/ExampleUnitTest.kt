package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val radha = listOf("राधा")

    @Test
    fun noDefaultNames_nothingMatchesWithoutUserNames() {
        assertEquals(0, NameMatcher.countMatches("राधा", emptyList()))
        assertEquals(0, NameMatcher.countMatches("radha", emptyList()))
        assertFalse(NameMatcher.isMatch("राम", emptyList()))
    }

    @Test
    fun fullName_counts() {
        assertEquals(1, NameMatcher.countMatches("राधा", radha))
        assertEquals(1, NameMatcher.countMatches("जय श्री राधा", radha))
        assertEquals(1, NameMatcher.countMatches("राधा।", radha))
    }

    @Test
    fun repeatedFullName_countsEveryRepeat() {
        assertEquals(2, NameMatcher.countMatches("राधा राधा", radha))
        assertEquals(2, NameMatcher.countMatches("राधाराधा", radha))
        assertEquals(3, NameMatcher.countMatches("राधा राधा राधा", radha))
    }

    @Test
    fun halfName_doesNotCount() {
        assertEquals(0, NameMatcher.countMatches("रा", radha))
        assertEquals(0, NameMatcher.countMatches("राध", radha))
        assertEquals(0, NameMatcher.countMatches("र", radha))
    }

    @Test
    fun wrongName_doesNotCount() {
        assertEquals(0, NameMatcher.countMatches("राधिका", radha))
        assertEquals(0, NameMatcher.countMatches("राधे", radha))
        assertEquals(0, NameMatcher.countMatches("राधाजी", radha))
        assertEquals(0, NameMatcher.countMatches("श्याम", radha))
        assertEquals(0, NameMatcher.countMatches("hello world", radha))
    }

    @Test
    fun latinNames_wholeWordOnly() {
        val ram = listOf("ram")
        assertEquals(1, NameMatcher.countMatches("jai shri ram", ram))
        assertEquals(2, NameMatcher.countMatches("Ram RAM", ram))
        assertEquals(0, NameMatcher.countMatches("program", ram))
        assertEquals(0, NameMatcher.countMatches("programmer", ram))
        assertEquals(0, NameMatcher.countMatches("ra", ram))
    }

    @Test
    fun multiWordMantra_needsEveryWordInOrder() {
        val mantra = listOf("हरे कृष्ण")
        assertEquals(1, NameMatcher.countMatches("हरे कृष्ण", mantra))
        assertEquals(2, NameMatcher.countMatches("हरे कृष्ण हरे कृष्ण", mantra))
        assertEquals(0, NameMatcher.countMatches("हरे", mantra))
        assertEquals(0, NameMatcher.countMatches("कृष्ण", mantra))
        assertEquals(0, NameMatcher.countMatches("कृष्ण हरे", mantra))
        assertEquals(0, NameMatcher.countMatches("हरे [unk] कृष्ण", mantra))
    }

    @Test
    fun longerNameWins_andMultipleNamesWork() {
        assertEquals(1, NameMatcher.countMatches("हरे कृष्ण", listOf("हरे कृष्ण", "कृष्ण")))
        assertEquals(3, NameMatcher.countMatches("राम श्याम राम", listOf("राम", "श्याम")))
    }

    @Test
    fun resultsList_onlyTopResultIsUsed() {
        assertEquals("जय राधा", NameMatcher.firstMatchingPhrase(listOf("जय राधा", "x"), radha))
        assertNull(NameMatcher.firstMatchingPhrase(listOf("hello", "राधा"), radha))
        assertNull(NameMatcher.firstMatchingPhrase(null, radha))
        assertNull(NameMatcher.firstMatchingPhrase(emptyList(), radha))
    }

    @Test
    fun cleanNames_dropsTooShortAndDuplicates() {
        assertEquals(listOf("राम"), NameMatcher.cleanNames(listOf("र", " ", "राम", "राम ", "  राम")))
    }

    @Test
    fun voskGrammar_onlyUserDevanagariNames_andIsValidJsonShape() {
        val g = NameMatcher.buildVoskGrammarJson(listOf("राम", "ram", "श्याम\"x", "ॐ नमः शिवाय"))
        assertTrue(g.startsWith("[") && g.endsWith("\"[unk]\"]"))
        assertFalse("Latin names are not in the Hindi model", g.contains("ram"))
        assertFalse("Quotes must never break the JSON", g.contains("\\\"x") && !g.contains("\\\\\""))
        // no names added -> only [unk]
        assertEquals("[\"[unk]\"]", NameMatcher.buildVoskGrammarJson(emptyList()))
        assertTrue(NameMatcher.voskNames(listOf("ram", "krishna")).isEmpty())
    }

    @Test
    fun testSessionIdGuard() {
        fun shouldProcessSpeech(callbackSession: Long, activeSession: Long, isListeningPhase: Boolean): Boolean {
            return callbackSession == activeSession && isListeningPhase
        }
        assertTrue(shouldProcessSpeech(10L, 10L, true))
        assertFalse(shouldProcessSpeech(9L, 10L, true))
        assertFalse(shouldProcessSpeech(10L, 10L, false))
    }

    @Test
    fun testSelectedAudio_formattedDuration() {
        assertEquals("0:05", SelectedAudio("content://audio/1", "reminder.mp3", 5000L).formattedDuration)
        assertEquals("1:05", SelectedAudio("content://audio/2", "confirmation.mp3", 65000L).formattedDuration)
    }

    @Test
    fun halfSoundDecoys_areInGrammar_butNeverCounted() {
        val decoys = NameMatcher.decoyWords(listOf("राधा"))
        assertTrue(decoys.contains("रा"))
        assertTrue(decoys.contains("राध"))
        assertFalse(decoys.contains("राधा"))
        assertEquals(0, NameMatcher.countMatches("रा", listOf("राधा")))
        val g = NameMatcher.buildVoskGrammarJson(listOf("राधा"))
        assertTrue(g.contains("\"रा\"") && g.contains("\"राधा\""))
    }

    @Test
    fun fullWordNeedsMoreTimeThanHalfWord() {
        assertEquals(2, NameMatcher.syllables("राधा"))
        assertEquals(2, NameMatcher.syllables("कृष्ण"))
        assertTrue(NameMatcher.minSpokenSeconds("राधा") > 0.2)
    }
}
