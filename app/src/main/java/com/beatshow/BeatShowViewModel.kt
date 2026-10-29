package com.beatshow

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beatshow.audio.MetronomeEngine
import com.beatshow.data.PhotoRepository
import com.beatshow.data.Settings
import com.beatshow.data.SettingsRepository
import com.beatshow.engine.BeatEvent
import com.beatshow.engine.Conductor
import com.beatshow.engine.ConductorConfig
import com.beatshow.engine.ShuffleQueue
import com.beatshow.engine.SlideEvent
import com.beatshow.engine.SlideNavigator
import com.beatshow.engine.TempoChangeEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { LIBRARY, SETTINGS, SLIDESHOW }

data class ShowState(
    val current: File? = null,
    val upcoming: File? = null,
    val playing: Boolean = false,
    val controlsVisible: Boolean = false,
    val bpm: Int = 0,
    val beatsPerBar: Int = 4,
    /** Increments on every downbeat so the overlay can flash. */
    val downbeats: Int = 0,
)

class BeatShowViewModel(app: Application) : AndroidViewModel(app) {

    private val photoRepository = PhotoRepository(app)
    private val settingsRepository = SettingsRepository(app)
    private val engine = MetronomeEngine()
    private var conductor: Conductor? = null
    private var navigator: SlideNavigator<File>? = null

    val photos: StateFlow<List<File>> = photoRepository.photos
    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var screen by mutableStateOf(Screen.LIBRARY)
        private set
    var show by mutableStateOf(ShowState())
        private set
    /** (done, total) while an import runs. */
    var importProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            settings.collect {
                engine.volume = it.volume
                engine.clickEnabled = it.metronomeEnabled
            }
        }
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || importProgress != null) return
        importProgress = 0 to uris.size
        viewModelScope.launch {
            val failed = photoRepository.import(uris) { done -> importProgress = done to uris.size }
            importProgress = null
            if (failed > 0) message = "$failed photo(s) couldn't be imported"
        }
    }

    fun deletePhotos(files: Collection<File>) = photoRepository.delete(files)

    fun clearPhotos() = photoRepository.clear()

    fun messageShown() {
        message = null
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun openLibrary() {
        screen = Screen.LIBRARY
    }

    fun startShow() {
        val list = photos.value
        if (list.isEmpty()) return
        val s = settings.value
        val c = Conductor(
            ConductorConfig(
                sampleRate = engine.sampleRate,
                mode = s.slideMode,
                beatsPerSlide = s.beatsPerSlide,
                secondsPerSlide = s.secondsPerSlide,
                minSlides = s.minSlides,
                maxSlides = s.maxSlides,
                minBpm = s.minBpm,
                maxBpm = s.maxBpm,
                beatsPerBar = s.beatsPerBar,
                sound = s.clickSound,
                randomizeSound = s.randomizeSound,
                randomizeTimeSignature = s.randomizeTimeSignature,
            ),
        )
        val nav = SlideNavigator(ShuffleQueue(list))
        conductor = c
        navigator = nav
        val first = nav.next()
        show = ShowState(current = first, upcoming = nav.peekNext(), bpm = c.bpm, beatsPerBar = c.beatsPerBar)
        screen = Screen.SLIDESHOW
        resume()
    }

    fun resume() {
        val c = conductor ?: return
        if (show.playing) return
        // A fresh audio stream starts at frame 0, with a new bar beginning right away.
        c.restart(0)
        engine.start(c)
        show = show.copy(playing = true)
    }

    fun pause() {
        if (!show.playing) return
        engine.stop()
        show = show.copy(playing = false)
    }

    fun togglePlay() = if (show.playing) pause() else resume()

    fun next() {
        val nav = navigator ?: return
        show = show.copy(current = nav.next(), upcoming = nav.peekNext())
    }

    fun previous() {
        val nav = navigator ?: return
        show = show.copy(current = nav.previous(), upcoming = nav.peekNext())
    }

    fun toggleControls() {
        show = show.copy(controlsVisible = !show.controlsVisible)
    }

    fun exitShow() {
        engine.stop()
        conductor = null
        navigator = null
        show = ShowState()
        screen = Screen.LIBRARY
    }

    /** Called every display frame: applies events the audio clock has reached. */
    fun onFrame() {
        if (!show.playing) return
        val played = engine.playedFrame()
        if (played < 0) return
        var state = show
        while (true) {
            val e = engine.events.peek() ?: break
            if (e.frame > played) break
            engine.events.poll()
            state = when (e) {
                is SlideEvent -> {
                    val nav = navigator ?: break
                    state.copy(current = nav.next(), upcoming = nav.peekNext())
                }
                is BeatEvent -> if (e.isDownbeat) state.copy(downbeats = state.downbeats + 1) else state
                is TempoChangeEvent -> state.copy(bpm = e.bpm, beatsPerBar = e.beatsPerBar)
            }
        }
        if (state != show) show = state
    }

    override fun onCleared() {
        engine.stop()
    }
}
