package com.troanhmolo.eartrainer.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.troanhmolo.eartrainer.data.settings.ChordSettingsStore
import com.troanhmolo.eartrainer.data.settings.DictationSettingsStore
import com.troanhmolo.eartrainer.data.settings.GoalSettingsStore
import com.troanhmolo.eartrainer.data.settings.IntervalSettingsStore
import com.troanhmolo.eartrainer.data.settings.ProgressionSettingsStore
import com.troanhmolo.eartrainer.data.settings.RhythmSettingsStore
import com.troanhmolo.eartrainer.data.settings.ScaleDegreeSettingsStore
import com.troanhmolo.eartrainer.data.settings.SoundSettingsStore

/**
 * Converts the shared settings DataStore to and from backup values.
 *
 * Every value passes through the settings stores' own decode and encode, so a backup always
 * holds a complete, valid set of settings, and importing can never store a value the app would
 * misread: unknown keys and values of the wrong type are dropped, and invalid choices fall back
 * to their defaults.
 */
internal object SettingsBackup {

    /** Copies one group of settings, replacing anything missing or invalid with defaults. */
    private val sections: List<(Preferences, MutablePreferences) -> Unit> = listOf(
        { from, to -> IntervalSettingsStore.encode(IntervalSettingsStore.decode(from), to) },
        { from, to -> ChordSettingsStore.encode(ChordSettingsStore.decode(from), to) },
        { from, to -> ScaleDegreeSettingsStore.encode(ScaleDegreeSettingsStore.decode(from), to) },
        { from, to -> ProgressionSettingsStore.encode(ProgressionSettingsStore.decode(from), to) },
        { from, to -> DictationSettingsStore.encode(DictationSettingsStore.decode(from), to) },
        { from, to -> RhythmSettingsStore.encode(RhythmSettingsStore.decode(from), to) },
        { from, to -> SoundSettingsStore.encode(SoundSettingsStore.decode(from), to) },
        { from, to -> GoalSettingsStore.encode(GoalSettingsStore.decode(from), to) },
    )

    fun export(preferences: Preferences): Map<String, SettingValue> =
        normalized(preferences).asMap().entries.mapNotNull { (key, value) -> value.toSettingValue()?.let { key.name to it } }.toMap()

    /** The settings to store for [values] from a backup. */
    fun import(values: Map<String, SettingValue>): Preferences {
        // The defaults name every known key, with a value of the type the stores expect.
        val expected = normalized(emptyPreferences()).asMap().mapKeys { it.key.name }
        val accepted = mutablePreferencesOf()
        values.forEach { (name, value) ->
            when (expected[name]) {
                is String -> value.string?.let { accepted[stringPreferencesKey(name)] = it }
                is Int -> value.int?.let { accepted[intPreferencesKey(name)] = it }
                is Boolean -> value.boolean?.let { accepted[booleanPreferencesKey(name)] = it }
                is Set<*> -> value.stringSet?.let { accepted[stringSetPreferencesKey(name)] = it }
            }
        }
        return normalized(accepted)
    }

    private fun normalized(preferences: Preferences): Preferences =
        mutablePreferencesOf().also { to -> sections.forEach { copy -> copy(preferences, to) } }

    private fun Any.toSettingValue(): SettingValue? = when (this) {
        is String -> SettingValue(string = this)
        is Int -> SettingValue(int = this)
        is Boolean -> SettingValue(boolean = this)
        is Set<*> -> SettingValue(stringSet = filterIsInstance<String>().toSet())
        else -> null
    }
}
