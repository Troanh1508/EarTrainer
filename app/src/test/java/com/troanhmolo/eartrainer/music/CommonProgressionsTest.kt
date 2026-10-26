package com.troanhmolo.eartrainer.music

import com.troanhmolo.eartrainer.exercise.ChordVocabulary
import com.troanhmolo.eartrainer.exercise.ProgressionQuestionGenerator
import com.troanhmolo.eartrainer.exercise.ProgressionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonProgressionsTest {

    private val lengths = ProgressionSettings.LENGTH_OPTIONS

    @Test
    fun everyEntryIsAValidQuestionStartingOnTheTonicWithoutRepeats() {
        Mode.entries.forEach { mode ->
            lengths.forEach { length ->
                val catalog = ProgressionQuestionGenerator.allProgressions(mode, ChordVocabulary.ALL, length).toSet()
                CommonProgressions.forMode(mode, length).forEach { progression ->
                    // Being in the full catalog means: right length, starts on the tonic, no chord repeated back to back.
                    assertTrue("$mode ${progression.label}", progression in catalog)
                }
            }
        }
    }

    @Test
    fun entriesAreUnique() {
        Mode.entries.forEach { mode ->
            lengths.forEach { length ->
                val labels = CommonProgressions.forMode(mode, length).map { it.label }
                assertEquals("$mode/$length duplicates", labels.size, labels.toSet().size)
            }
        }
    }

    @Test
    fun everySettingsCombinationHasAtLeastOneCommonProgression() {
        Mode.entries.forEach { mode ->
            lengths.forEach { length ->
                ChordVocabulary.entries.forEach { vocabulary ->
                    val fitting = ProgressionQuestionGenerator.commonProgressions(mode, vocabulary, length)
                    assertTrue("$mode/$length/$vocabulary has none", fitting.isNotEmpty())
                }
                // With every chord allowed there are enough for a full set of choices.
                assertTrue(CommonProgressions.forMode(mode, length).size >= ProgressionSettings.CHOICE_COUNT)
            }
        }
    }

    @Test
    fun wellKnownProgressionsAreIncluded() {
        val major4 = CommonProgressions.forMode(Mode.MAJOR, 4).map { it.label }
        assertTrue("I – V – vi – IV" in major4)
        assertTrue("I – vi – IV – V" in major4)
        assertTrue("i – VII – VI – V" in CommonProgressions.forMode(Mode.MINOR, 4).map { it.label })
    }

    @Test
    fun parsingRejectsNumeralsFromTheOtherMode() {
        assertEquals("I – vi – IV – V", CommonProgressions.parse(Mode.MAJOR, "I vi IV V").label)
        assertThrows(IllegalStateException::class.java) { CommonProgressions.parse(Mode.MINOR, "i IV V") }
    }
}
