package com.beatshow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.beatshow.engine.ClickSound
import com.beatshow.engine.SlideMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val slideMode: SlideMode = SlideMode.BEAT_SYNC,
    val beatsPerSlide: Int = 4,
    val secondsPerSlide: Float = 3f,
    val crossfadeMs: Int = 300,
    val fillScreen: Boolean = false,
    val metronomeEnabled: Boolean = true,
    val volume: Float = 0.8f,
    val beatsPerBar: Int = 4,
    val clickSound: ClickSound = ClickSound.WOODBLOCK,
    val minSlides: Int = 2,
    val maxSlides: Int = 10,
    val minBpm: Int = 70,
    val maxBpm: Int = 140,
    val randomizeSound: Boolean = false,
    val randomizeTimeSignature: Boolean = false,
    val showOverlay: Boolean = true,
) {
    companion object {
        val BEATS_PER_SLIDE = 1..16
        val SECONDS_PER_SLIDE = 0.5f..30f
        val CROSSFADE_MS = 0..1000
        val BEATS_PER_BAR = 2..7
        val SLIDES = 1..30
        val BPM = 40..240
        /** Keeps room for a tempo at least 5 BPM away from any current tempo. */
        const val MIN_BPM_SPAN = 10
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val slideMode = stringPreferencesKey("slide_mode")
        val beatsPerSlide = intPreferencesKey("beats_per_slide")
        val secondsPerSlide = floatPreferencesKey("seconds_per_slide")
        val crossfadeMs = intPreferencesKey("crossfade_ms")
        val fillScreen = booleanPreferencesKey("fill_screen")
        val metronomeEnabled = booleanPreferencesKey("metronome_enabled")
        val volume = floatPreferencesKey("volume")
        val beatsPerBar = intPreferencesKey("beats_per_bar")
        val clickSound = stringPreferencesKey("click_sound")
        val minSlides = intPreferencesKey("min_slides")
        val maxSlides = intPreferencesKey("max_slides")
        val minBpm = intPreferencesKey("min_bpm")
        val maxBpm = intPreferencesKey("max_bpm")
        val randomizeSound = booleanPreferencesKey("randomize_sound")
        val randomizeTimeSignature = booleanPreferencesKey("randomize_time_signature")
        val showOverlay = booleanPreferencesKey("show_overlay")
    }

    val settings: Flow<Settings> = context.dataStore.data.map(::fromPrefs)

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val old = fromPrefs(p)
            val new = sanitize(old, transform(old))
            p[Keys.slideMode] = new.slideMode.name
            p[Keys.beatsPerSlide] = new.beatsPerSlide
            p[Keys.secondsPerSlide] = new.secondsPerSlide
            p[Keys.crossfadeMs] = new.crossfadeMs
            p[Keys.fillScreen] = new.fillScreen
            p[Keys.metronomeEnabled] = new.metronomeEnabled
            p[Keys.volume] = new.volume
            p[Keys.beatsPerBar] = new.beatsPerBar
            p[Keys.clickSound] = new.clickSound.name
            p[Keys.minSlides] = new.minSlides
            p[Keys.maxSlides] = new.maxSlides
            p[Keys.minBpm] = new.minBpm
            p[Keys.maxBpm] = new.maxBpm
            p[Keys.randomizeSound] = new.randomizeSound
            p[Keys.randomizeTimeSignature] = new.randomizeTimeSignature
            p[Keys.showOverlay] = new.showOverlay
        }
    }

    private fun fromPrefs(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            slideMode = p[Keys.slideMode]?.let { runCatching { SlideMode.valueOf(it) }.getOrNull() } ?: d.slideMode,
            beatsPerSlide = p[Keys.beatsPerSlide] ?: d.beatsPerSlide,
            secondsPerSlide = p[Keys.secondsPerSlide] ?: d.secondsPerSlide,
            crossfadeMs = p[Keys.crossfadeMs] ?: d.crossfadeMs,
            fillScreen = p[Keys.fillScreen] ?: d.fillScreen,
            metronomeEnabled = p[Keys.metronomeEnabled] ?: d.metronomeEnabled,
            volume = p[Keys.volume] ?: d.volume,
            beatsPerBar = p[Keys.beatsPerBar] ?: d.beatsPerBar,
            clickSound = p[Keys.clickSound]?.let { runCatching { ClickSound.valueOf(it) }.getOrNull() } ?: d.clickSound,
            minSlides = p[Keys.minSlides] ?: d.minSlides,
            maxSlides = p[Keys.maxSlides] ?: d.maxSlides,
            minBpm = p[Keys.minBpm] ?: d.minBpm,
            maxBpm = p[Keys.maxBpm] ?: d.maxBpm,
            randomizeSound = p[Keys.randomizeSound] ?: d.randomizeSound,
            randomizeTimeSignature = p[Keys.randomizeTimeSignature] ?: d.randomizeTimeSignature,
            showOverlay = p[Keys.showOverlay] ?: d.showOverlay,
        )
    }

    /** Clamps values and keeps min/max pairs consistent, moving the partner of whichever side changed. */
    private fun sanitize(old: Settings, s: Settings): Settings {
        var minSlides = s.minSlides.coerceIn(Settings.SLIDES)
        var maxSlides = s.maxSlides.coerceIn(Settings.SLIDES)
        if (minSlides > maxSlides) {
            if (minSlides != old.minSlides) maxSlides = minSlides else minSlides = maxSlides
        }
        var minBpm = s.minBpm.coerceIn(Settings.BPM.first, Settings.BPM.last - Settings.MIN_BPM_SPAN)
        var maxBpm = s.maxBpm.coerceIn(Settings.BPM.first + Settings.MIN_BPM_SPAN, Settings.BPM.last)
        if (maxBpm - minBpm < Settings.MIN_BPM_SPAN) {
            if (minBpm != old.minBpm) maxBpm = minBpm + Settings.MIN_BPM_SPAN else minBpm = maxBpm - Settings.MIN_BPM_SPAN
        }
        return s.copy(
            beatsPerSlide = s.beatsPerSlide.coerceIn(Settings.BEATS_PER_SLIDE),
            secondsPerSlide = s.secondsPerSlide.coerceIn(Settings.SECONDS_PER_SLIDE.start, Settings.SECONDS_PER_SLIDE.endInclusive),
            crossfadeMs = s.crossfadeMs.coerceIn(Settings.CROSSFADE_MS),
            volume = s.volume.coerceIn(0f, 1f),
            beatsPerBar = s.beatsPerBar.coerceIn(Settings.BEATS_PER_BAR),
            minSlides = minSlides,
            maxSlides = maxSlides,
            minBpm = minBpm,
            maxBpm = maxBpm,
        )
    }
}
