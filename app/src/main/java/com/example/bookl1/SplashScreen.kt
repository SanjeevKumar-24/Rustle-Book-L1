package com.example.bookl1

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current

    // 1. Single video file for both phones and tablets
    val videoRes = R.raw.splash_video

    // 2. Backup Safety Timer: Auto-navigate after 5 seconds if loading hangs
    LaunchedEffect(Unit) {
        delay(5000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)), // Rustle Mandala Black
        contentAlignment = Alignment.Center
    ) {
        // --- LAYER 1: THE NATIVE VIDEO PLAYER ---
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                object : VideoView(ctx) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        val width = getDefaultSize(0, widthMeasureSpec)
                        val height = getDefaultSize(0, heightMeasureSpec)
                        setMeasuredDimension(width, height)
                    }
                }.apply {
                    val videoUri = Uri.parse("android.resource://${ctx.packageName}/$videoRes")
                    setVideoURI(videoUri)

                    setMediaController(null)

                    setOnCompletionListener {
                        onTimeout()
                    }

                    setOnErrorListener { _, _, _ ->
                        onTimeout()
                        true
                    }

                    start()
                }
            },
            update = { videoView ->
                if (!videoView.isPlaying) {
                    videoView.start()
                }
            }
        )

        // --- LAYER 2: TRANSPARENT TOUCH INTERCEPTOR ---
        // Tapping anywhere on the video will now instantly skip the splash screen!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = { onTimeout() })
        )

        // --- LAYER 3: VISIBLE SKIP BUTTON ---
        // Styled with the Rustle Gold theme so users know they can skip
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x801E1E1E))
                .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(20.dp))
                .clickable { onTimeout() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Skip ⏭️",
                color = Color(0xFFD4AF37),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}