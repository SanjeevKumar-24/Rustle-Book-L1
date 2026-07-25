package com.example.bookl1

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoSplashScreen(onSplashFinished: () -> Unit) {
    val context = LocalContext.current
    var isFinished by remember { mutableStateOf(false) }

    // Ensures we only trigger the navigation callback once
    val finishSplash = {
        if (!isFinished) {
            isFinished = true
            onSplashFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Touching anywhere on the screen instantly skips the video!
            .clickable { finishSplash() }
    ) {
        // Fullscreen Video Player
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val videoUri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.splash_video}")
                    setVideoURI(videoUri)

                    setOnPreparedListener { mediaPlayer ->
                        // Scales video to fill screen edge-to-edge seamlessly
                        mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)

                        // Jumps 300ms ahead to bypass any initial dark/empty frames from exporting
                        mediaPlayer.seekTo(300)
                        mediaPlayer.start()
                    }

                    // When video finishes on its own, jump to Library screen
                    setOnCompletionListener {
                        finishSplash()
                    }

                    // If video fails to load for any reason, don't trap the user—jump directly to Library
                    setOnErrorListener { _, _, _ ->
                        finishSplash()
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}