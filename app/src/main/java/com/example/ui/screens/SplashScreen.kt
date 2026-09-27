package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.RoutePilotBrandLogo
import com.example.ui.theme.RoutePilotBlue
import kotlinx.coroutines.delay

/**
 * SCREEN 1 — SPLASH SCREEN
 * Displays the RoutePilot logo, "Safer Roads • Smarter Journeys", and realistic road illustration.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1700L)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onSplashComplete() }
            .testTag("splash_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_splash_road),
            contentDescription = "RoutePilot highway background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Clean top sky gradient overlay for crisp logo readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE2F0FF).copy(alpha = 0.94f),
                            Color(0xFFF5FAFF).copy(alpha = 0.85f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            RoutePilotBrandLogo(
                iconSize = 92.dp,
                showTagline = true
            )

            Spacer(modifier = Modifier.weight(1f))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(140.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = RoutePilotBlue,
                trackColor = Color.White.copy(alpha = 0.45f)
            )
        }
    }
}
