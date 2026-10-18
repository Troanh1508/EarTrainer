package com.example.eartrainer.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.eartrainer.audio.Instrument
import com.example.eartrainer.audio.SoundSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSettingsStoreTest {

    @Test
    fun pianoIsTheDefault() {
        assertEquals(SoundSettings(Instrument.PIANO), SoundSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTripsEveryInstrument() {
        Instrument.entries.forEach { instrument ->
            val preferences = mutablePreferencesOf()
            SoundSettingsStore.encode(SoundSettings(instrument), preferences)
            assertEquals(SoundSettings(instrument), SoundSettingsStore.decode(preferences))
        }
    }

    @Test
    fun unknownInstrumentFallsBackToTheDefault() {
        assertEquals(SoundSettings(), SoundSettingsStore.decode(preferencesOf(stringPreferencesKey("sound.instrument") to "THEREMIN")))
    }
}
