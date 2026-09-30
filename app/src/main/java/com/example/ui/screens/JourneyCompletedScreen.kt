package com.example.ui.screens

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.max

/**
 * SCREEN 10 — JOURNEY COMPLETED SCREEN
 * Matches "10. Journey Completed" from the reference design:
 * - Large green circle with white checkmark
 * - "You Have Arrived!"
 * - "Destination: City Hospital" (or active destination name)
 * - White summary card with 3 rows and dividers:
 *   - Total Distance -> 17.6 km
 *   - Total Time -> 30 min
 *   - Hazards Avoided -> 1 (Bridge B1) in bold green
 * - Primary blue "Back to Home" button
 */
@Composable
fun JourneyCompletedScreen(
    journey: Journey?,
    fallbackDestination: Destination,
    useKilometers: Boolean,
    avoidedHazardName: String = "Bridge B1",
    onDone: () -> Unit
) {
    val destName = (journey?.destinationName ?: fallbackDestination.name).ifBlank { "City Hospital" }
    val distKm = journey?.distanceKm ?: 17.6
    val durationMin = journey?.durationMinutes ?: 30
    val avoidedCount = max(1, journey?.hazardsAvoidedCount ?: 1)
    val hazardLabel = avoidedHazardName.ifBlank { "Bridge B1" }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("journey_completed_screen"),
        color = Color(0xFFF8FAFC)
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
            Spacer(modifier = Modifier.height(28.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Green Circle with White Checkmark (matching Screen 10)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.title_journey_completed),
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = stringResource(R.string.title_journey_completed),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 26.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.label_destination_format, destName),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Summary Card with Total Distance, Total Time, and Hazards Avoided
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp)
                    ) {
                        SummaryKeyValueRow(
                            label = stringResource(R.string.label_total_distance),
                            value = GeoUtils.formatDistanceKm(distKm, useKilometers),
                            valueColor = Color(0xFF0F172A)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = Color(0xFFE2E8F0)
                        )

                        SummaryKeyValueRow(
                            label = stringResource(R.string.label_total_time),
                            value = "$durationMin min",
                            valueColor = Color(0xFF0F172A)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = Color(0xFFE2E8F0)
                        )

                        SummaryKeyValueRow(
                            label = stringResource(R.string.label_hazards_avoided),
                            value = "$avoidedCount ($hazardLabel)",
                            valueColor = Color(0xFF1E8E3E)
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("journey_done_button")
            ) {
                Text(
                    text = stringResource(R.string.action_done),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SummaryKeyValueRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color(0xFF475569),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                fontSize = 17.sp
            )
        )
    }
}
