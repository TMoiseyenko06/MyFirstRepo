package com.beatshow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beatshow.data.Settings
import com.beatshow.engine.ClickSound
import com.beatshow.engine.SlideMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Section("Slide timing")
            Choice(
                "Mode",
                listOf(SlideMode.BEAT_SYNC to "Beat-sync", SlideMode.SECONDS to "Fixed seconds"),
                settings.slideMode,
            ) { v -> onUpdate { it.copy(slideMode = v) } }
            if (settings.slideMode == SlideMode.BEAT_SYNC) {
                IntSlider("Beats per slide", settings.beatsPerSlide, Settings.BEATS_PER_SLIDE) { v ->
                    onUpdate { it.copy(beatsPerSlide = v) }
                }
            } else {
                StepSlider(
                    "Seconds per slide",
                    settings.secondsPerSlide,
                    Settings.SECONDS_PER_SLIDE.start,
                    Settings.SECONDS_PER_SLIDE.endInclusive,
                    0.5f,
                    format = { "%.1f s".format(it) },
                ) { v -> onUpdate { it.copy(secondsPerSlide = v) } }
            }

            Section("Display")
            StepSlider(
                "Crossfade",
                settings.crossfadeMs.toFloat(),
                Settings.CROSSFADE_MS.first.toFloat(),
                Settings.CROSSFADE_MS.last.toFloat(),
                50f,
                format = { "${it.roundToInt()} ms" },
            ) { v -> onUpdate { it.copy(crossfadeMs = v.roundToInt()) } }
            Choice("Image scaling", listOf(false to "Fit (black bars)", true to "Fill (crop)"), settings.fillScreen) { v ->
                onUpdate { it.copy(fillScreen = v) }
            }
            Toggle("Show BPM overlay", settings.showOverlay) { v -> onUpdate { it.copy(showOverlay = v) } }

            Section("Metronome")
            Toggle("Metronome sound", settings.metronomeEnabled) { v -> onUpdate { it.copy(metronomeEnabled = v) } }
            StepSlider("Volume", settings.volume * 100, 0f, 100f, 5f, format = { "${it.roundToInt()}%" }) { v ->
                onUpdate { it.copy(volume = v / 100f) }
            }
            IntSlider("Beats per bar", settings.beatsPerBar, Settings.BEATS_PER_BAR) { v -> onUpdate { it.copy(beatsPerBar = v) } }
            Choice("Click sound", ClickSound.entries.map { it to it.label }, settings.clickSound) { v ->
                onUpdate { it.copy(clickSound = v) }
            }

            Section("Random tempo switching")
            IntSlider("Min slides before switch", settings.minSlides, Settings.SLIDES) { v -> onUpdate { it.copy(minSlides = v) } }
            IntSlider("Max slides before switch", settings.maxSlides, Settings.SLIDES) { v -> onUpdate { it.copy(maxSlides = v) } }
            IntSlider("Min BPM", settings.minBpm, Settings.BPM) { v -> onUpdate { it.copy(minBpm = v) } }
            IntSlider("Max BPM", settings.maxBpm, Settings.BPM) { v -> onUpdate { it.copy(maxBpm = v) } }
            Toggle("Randomize click sound on switch", settings.randomizeSound) { v -> onUpdate { it.copy(randomizeSound = v) } }
            Toggle("Randomize time signature on switch", settings.randomizeTimeSignature) { v ->
                onUpdate { it.copy(randomizeTimeSignature = v) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun IntSlider(title: String, value: Int, range: IntRange, onCommit: (Int) -> Unit) {
    StepSlider(title, value.toFloat(), range.first.toFloat(), range.last.toFloat(), 1f, format = { it.roundToInt().toString() }) {
        onCommit(it.roundToInt())
    }
}

/** Slider snapped to [step]; it saves only when the drag ends, to keep DataStore writes low. */
@Composable
private fun StepSlider(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    val positions = ((max - min) / step).roundToInt()
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current,
            onValueChange = { current = min + ((it - min) / step).roundToInt() * step },
            valueRange = min..max,
            steps = (positions - 1).coerceAtLeast(0),
            onValueChangeFinished = { onCommit(current) },
        )
    }
}
