package com.beatshow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beatshow.ui.BeatShowTheme
import com.beatshow.ui.LibraryScreen
import com.beatshow.ui.SettingsScreen
import com.beatshow.ui.SlideshowScreen

class MainActivity : ComponentActivity() {

    private val viewModel: BeatShowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeatShowTheme {
                val vm = viewModel
                val photos by vm.photos.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                when (vm.screen) {
                    Screen.LIBRARY -> LibraryScreen(
                        photos = photos,
                        importProgress = vm.importProgress,
                        message = vm.message,
                        onMessageShown = vm::messageShown,
                        onImport = vm::importPhotos,
                        onDelete = vm::deletePhotos,
                        onClearAll = vm::clearPhotos,
                        onPlay = vm::startShow,
                        onSettings = vm::openSettings,
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onUpdate = vm::updateSettings,
                        onBack = vm::openLibrary,
                    )
                    Screen.SLIDESHOW -> SlideshowScreen(
                        show = vm.show,
                        settings = settings,
                        onFrame = vm::onFrame,
                        onToggleControls = vm::toggleControls,
                        onTogglePlay = vm::togglePlay,
                        onNext = vm::next,
                        onPrevious = vm::previous,
                        onExit = vm::exitShow,
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Pause audio and slides whenever the app leaves the foreground.
        viewModel.pause()
    }
}
