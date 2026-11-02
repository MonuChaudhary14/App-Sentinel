package com.antigravity.appsentinel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.appsentinel.model.SystemHealth
import com.antigravity.appsentinel.ui.theme.*

@Composable
fun SystemHealthCard(
    health: SystemHealth,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val usedGb = health.usedRamMb / 1024f
    val totalGb = health.totalRamMb / 1024f
    val freeGb = (health.freeRamMb / 1024f).coerceAtLeast(0f)
    val progress = (health.usedRamMb / health.totalRamMb).coerceIn(0f, 1f)
    val pct = (progress * 100).toInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Label & Percentage Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MEMORY POOL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colors.textSecondary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (pct > 85) colors.redSubtle else colors.indigoSubtle)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$pct% LOAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (pct > 85) AccentRed else AccentIndigo
                    )
                }
            }

            // Big Stat Display: 6.1 / 7.2 GB
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = String.format("%.1f", usedGb),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    lineHeight = 34.sp
                )
                Text(
                    text = String.format("GB used of %.1f GB", totalGb),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // High-precision smooth progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = AccentIndigo,
                trackColor = colors.surfaceElevated
            )

            // Bottom metric chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(
                    dotColor = AccentIndigo,
                    label = "Active in RAM",
                    value = String.format("%.1f GB", usedGb)
                )
                MetricPill(
                    dotColor = AccentGreen,
                    label = "Free / Available",
                    value = String.format("%.1f GB", freeGb)
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    dotColor: androidx.compose.ui.graphics.Color,
    label: String,
    value: String
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = "$label: ",
            fontSize = 11.sp,
            color = colors.textMuted
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}
