package com.antigravity.appsentinel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.appsentinel.model.AppInfo
import com.antigravity.appsentinel.model.SafetyLevel
import com.antigravity.appsentinel.ui.theme.*

@Composable
fun AppItemCard(
    app: AppInfo,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onUninstall: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    var showUninstallDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: App Icon + Name / Package + RAM Usage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Real App Icon (42dp Squircle)
                AppIcon(
                    packageName = app.packageName,
                    size = 42.dp
                )

                // App Title & Package
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!app.isEnabled || app.isUninstalledForUser) colors.textSecondary else colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (app.isUninstalledForUser) {
                            StatusTag("Removed", colors.redSubtle, AccentRed)
                        } else if (!app.isEnabled) {
                            StatusTag("Frozen", colors.indigoSubtle, AccentIndigo)
                        } else if (app.isForeground) {
                            StatusTag("Foreground", colors.indigoSubtle, AccentIndigo)
                        } else if (app.hasActiveActivities) {
                            StatusTag("Active UI", colors.indigoSubtle, AccentIndigo)
                        } else if (app.isRunning) {
                            StatusTag("Background", colors.surfaceElevated, colors.textSecondary)
                        }
                    }

                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textMuted,
                        maxLines = 1
                    )
                }

                // RAM Badge (Compact Monospace)
                if (app.ramUsageMb > 0f) {
                    val ramText = if (app.ramUsageMb >= 1000f) {
                        String.format("%.1f GB", app.ramUsageMb / 1024f)
                    } else {
                        String.format("%.0f MB", app.ramUsageMb)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = ramText,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Row 2: Category Tag & Compact Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Safety Tag
                Text(
                    text = when (app.safetyLevel) {
                        SafetyLevel.SAFE_DEBLOAT -> "Safe to remove"
                        SafetyLevel.CAUTION -> "Optional / Caution"
                        SafetyLevel.USER_APP -> "User installed"
                        SafetyLevel.SYSTEM_CORE -> "System Core"
                    },
                    fontSize = 11.sp,
                    color = when (app.safetyLevel) {
                        SafetyLevel.SAFE_DEBLOAT -> AccentIndigo
                        SafetyLevel.CAUTION -> AccentAmber
                        SafetyLevel.USER_APP -> AccentBlue
                        SafetyLevel.SYSTEM_CORE -> colors.textMuted
                    },
                    fontWeight = FontWeight.Medium
                )

                // Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (app.isUninstalledForUser) {
                        ActionButton(
                            label = "Restore",
                            icon = Icons.Outlined.Refresh,
                            tint = AccentIndigo,
                            bg = colors.indigoSubtle,
                            onClick = onRestore
                        )
                    } else if (app.safetyLevel != SafetyLevel.SYSTEM_CORE) {
                        if (app.isEnabled) {
                            ActionButton(
                                label = "Freeze",
                                icon = Icons.Outlined.AcUnit,
                                tint = colors.textSecondary,
                                bg = colors.surfaceElevated,
                                onClick = onFreeze
                            )
                        } else {
                            ActionButton(
                                label = "Unfreeze",
                                icon = Icons.Outlined.WbSunny,
                                tint = AccentIndigo,
                                bg = colors.indigoSubtle,
                                onClick = onUnfreeze
                            )
                        }

                        ActionButton(
                            label = "Remove",
                            icon = Icons.Outlined.DeleteOutline,
                            tint = AccentRed,
                            bg = colors.redSubtle,
                            onClick = { showUninstallDialog = true }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Protected",
                                fontSize = 11.sp,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = {
                Text("Uninstall ${app.appName}?", color = colors.textPrimary)
            },
            text = {
                Text(
                    "This removes the app for your user profile (pm uninstall -k --user 0). Its background services will stop. You can restore it anytime in the Restorable tab.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUninstallDialog = false
                        onUninstall()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Remove", color = colors.onIndigo, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun StatusTag(label: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint
                )
            }
        }
    }
}
