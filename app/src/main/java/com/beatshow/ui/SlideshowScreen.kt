package com.beatshow.ui

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.beatshow.ShowState
import com.beatshow.data.Settings
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun SlideshowScreen(
    show: ShowState,
    settings: Settings,
    onFrame: () -> Unit,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExit: () -> Unit,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    // Immersive fullscreen with the orientation locked for the whole show.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    DisposableEffect(activity, show.playing) {
        val window = activity?.window
        if (show.playing) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Visuals follow the audio clock: every frame, apply whatever the speaker has reached.
    LaunchedEffect(show.playing) {
        if (show.playing) {
            while (isActive) {
                withFrameNanos { }
                onFrame()
            }
        }
    }

    // Decode the next photo ahead of time so the crossfade never starts from black.
    LaunchedEffect(show.upcoming) {
        show.upcoming?.let {
            SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(it).build())
        }
    }

    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }) }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > swipeThreshold) {
                            if (total < 0) onNext() else onPrevious()
                        }
                    },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            },
    ) {
        val fadeMs = settings.crossfadeMs
        Crossfade(
            targetState = show.current,
            animationSpec = if (fadeMs <= 0) snap() else tween(fadeMs),
            label = "slide",
        ) { file ->
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = if (settings.fillScreen) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (settings.showOverlay && show.bpm > 0) {
            BeatOverlay(show, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp))
        }

        AnimatedVisibility(
            visible = show.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                IconButton(
                    onClick = onExit,
                    colors = buttonColors,
                    modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
                ) { Icon(AppIcons.Close, "Exit slideshow") }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, colors = buttonColors, modifier = Modifier.size(64.dp)) {
                        Icon(AppIcons.SkipPrevious, "Previous", Modifier.size(40.dp))
                    }
                    IconButton(onClick = onTogglePlay, colors = buttonColors, modifier = Modifier.size(80.dp)) {
                        Icon(if (show.playing) AppIcons.Pause else AppIcons.Play, if (show.playing) "Pause" else "Play", Modifier.size(56.dp))
                    }
                    IconButton(onClick = onNext, colors = buttonColors, modifier = Modifier.size(64.dp)) {
                        Icon(AppIcons.SkipNext, "Next", Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BeatOverlay(show: ShowState, modifier: Modifier) {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(show.downbeats) {
        if (show.downbeats > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(250))
        }
    }
    val base = Color.Black.copy(alpha = 0.45f)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .drawBehind {
                drawRoundRect(
                    color = lerp(base, accent, flash.value),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "${show.bpm} BPM · ${show.beatsPerBar}/4",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
