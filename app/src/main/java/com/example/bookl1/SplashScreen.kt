package com.example.bookl1

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoSplashScreen(onSplashFinished: () -> Unit) {
    var isVisible by remember { mutableStateOf(true) }
    var hasFinished by remember { mutableStateOf(false) }

    val finishSplash = {
        if (!hasFinished) {
            hasFinished = true
            isVisible = false
            onSplashFinished()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(1000))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { finishSplash() },
            contentAlignment = Alignment.Center
        ) {

            // THE FULLSCREEN VIDEO FIX
            AndroidView(
                factory = { context ->
                    // 1. Create a custom inline VideoView that forces itself to match the screen size
                    object : VideoView(context) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            setMeasuredDimension(
                                getDefaultSize(0, widthMeasureSpec),
                                getDefaultSize(0, heightMeasureSpec)
                            )
                        }
                    }.apply {
                        setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.splash_video}"))

                        // 2. Tell the internal player to crop the video to fit the new full-screen bounds
                        setOnPreparedListener { mp ->
                            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        }

                        setOnCompletionListener {
                            finishSplash()
                        }
                        start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

        }
    }
}