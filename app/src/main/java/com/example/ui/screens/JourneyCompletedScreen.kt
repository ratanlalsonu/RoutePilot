package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Journey
import com.example.domain.routing.GeoUtils
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.theme.SurfaceBackground

/**
 * SCREEN 10 — JOURNEY COMPLETED SCREEN
 * Matches Screen 10 of the reference design:
 * - Large green checkmark emblem with subtle celebratory accents
 * - "Journey Completed!"
 * - "You have safely reached your destination."
 * - Summary card with Destination (District Hospital, Jhansi, UP), Total Distance (18.4 km), Total Time (31 min)
 * - Primary blue "Done" button returning to Home
 */
@Composable
fun JourneyCompletedScreen(
    journey: Journey?,
    fallbackDestination: Destination,
    useKilometers: Boolean,
    onDone: () -> Unit
) {
    val destName = journey?.destinationName ?: fallbackDestination.name
    val destAddress = journey?.destinationAddress ?: fallbackDestination.address
    val distKm = journey?.distanceKm ?: 18.4
    val durationMin = journey?.durationMinutes ?: 31

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("journey_completed_screen"),
        color = SurfaceBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Celebratory Green Checkmark Badge (matching Screen 10)
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Subtle confetti dots around checkmark
                        drawCircle(color = Color(0xFFF59E0B), radius = 5.dp.toPx(), center = Offset(w * 0.14f, h * 0.22f))
                        drawCircle(color = RoutePilotBlue, radius = 4.dp.toPx(), center = Offset(w * 0.85f, h * 0.18f))
                        drawCircle(color = SafeRouteGreen, radius = 4.5.dp.toPx(), center = Offset(w * 0.88f, h * 0.72f))
                        drawCircle(color = Color(0xFFEC4899), radius = 4.dp.toPx(), center = Offset(w * 0.12f, h * 0.70f))
                    }

                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(SafeRouteGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.title_journey_completed),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.title_journey_completed),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.subtitle_journey_completed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Journey Details Summary Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        CompletedMetricRow(
                            icon = Icons.Default.LocationOn,
                            title = destName,
                            subtitle = destAddress,
                            titleBold = true
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = Color(0xFFF1F5F9)
                        )

                        CompletedMetricRow(
                            icon = Icons.Default.Route,
                            title = stringResource(R.string.label_total_distance),
                            subtitle = GeoUtils.formatDistanceKm(distKm, useKilometers),
                            titleBold = false
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = Color(0xFFF1F5F9)
                        )

                        CompletedMetricRow(
                            icon = Icons.Default.AccessTime,
                            title = stringResource(R.string.label_total_time),
                            subtitle = GeoUtils.formatDurationMinutes(durationMin),
                            titleBold = false
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("journey_done_button")
            ) {
                Text(
                    text = stringResource(R.string.action_done),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CompletedMetricRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleBold: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF0F172A),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = if (titleBold) {
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = if (titleBold) {
                    MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
                } else {
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                }
            )
        }
    }
}
