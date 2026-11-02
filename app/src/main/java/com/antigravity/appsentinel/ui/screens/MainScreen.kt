package com.antigravity.appsentinel.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.appsentinel.model.AppInfo
import com.antigravity.appsentinel.model.SafetyLevel
import com.antigravity.appsentinel.ui.components.*
import com.antigravity.appsentinel.ui.theme.*
import com.antigravity.appsentinel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Filter logic
    val runningApps = remember(uiState.apps) { uiState.apps.filter { it.isRunning && !it.isUninstalledForUser } }
    val runningActiveCount = remember(runningApps) { runningApps.count { it.isForeground || it.hasActiveActivities } }
    val runningBgCount = remember(runningApps) { runningApps.count { !it.isForeground && !it.hasActiveActivities } }

    val displayedApps = remember(uiState, runningApps) {
        var list = uiState.apps

        list = when (uiState.selectedTab) {
            0 -> {
                when (uiState.selectedMonitorFilter) {
                    1 -> runningApps.filter { it.isForeground || it.hasActiveActivities }
                    2 -> runningApps.filter { !it.isForeground && !it.hasActiveActivities }
                    else -> runningApps
                }
            }
            1 -> list.filter { !it.isUninstalledForUser }
            2 -> list.filter { it.isUninstalledForUser || !it.isEnabled }
            else -> list
        }

        if (uiState.selectedSafetyFilter != null && uiState.selectedTab == 1) {
            list = list.filter { it.safetyLevel == uiState.selectedSafetyFilter }
        }

        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim().lowercase()
            list = list.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        list
    }

    val safeCount = remember(uiState.apps) { uiState.apps.count { it.safetyLevel == SafetyLevel.SAFE_DEBLOAT && !it.isUninstalledForUser } }
    val cautionCount = remember(uiState.apps) { uiState.apps.count { it.safetyLevel == SafetyLevel.CAUTION && !it.isUninstalledForUser } }
    val userCount = remember(uiState.apps) { uiState.apps.count { it.safetyLevel == SafetyLevel.USER_APP && !it.isUninstalledForUser } }
    val totalInstalledCount = remember(uiState.apps) { uiState.apps.count { !it.isUninstalledForUser } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.bg,
        topBar = {
            TopBar(
                title = when (uiState.selectedTab) {
                    0 -> "Monitor"
                    1 -> "Debloat"
                    2 -> "Restorable"
                    else -> "AppSentinel"
                },
                subtitle = when (uiState.selectedTab) {
                    0 -> "${runningApps.size} running • $runningActiveCount active • $runningBgCount background"
                    1 -> "$totalInstalledCount total packages installed"
                    2 -> "${displayedApps.size} disabled & removed"
                    else -> ""
                },
                isDarkTheme = uiState.isDarkTheme,
                isRefreshing = uiState.isRefreshing,
                onToggleTheme = {
                    focusManager.clearFocus()
                    viewModel.toggleTheme()
                },
                onRefresh = {
                    focusManager.clearFocus()
                    viewModel.refreshData()
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = {
                    focusManager.clearFocus()
                    viewModel.setSelectedTab(it)
                }
            )
        },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // RAM Consumption Card — ONLY in Monitor Tab
            if (uiState.selectedTab == 0) {
                SystemHealthCard(health = uiState.systemHealth)
            }

            // Search Bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onSearchDismiss = { focusManager.clearFocus() }
            )

            // Process State Filter Chips (All Running / Active UI / Background) — Monitor Tab
            if (uiState.selectedTab == 0) {
                MonitorChipsRow(
                    selectedFilter = uiState.selectedMonitorFilter,
                    totalCount = runningApps.size,
                    activeCount = runningActiveCount,
                    backgroundCount = runningBgCount,
                    onFilterSelected = {
                        focusManager.clearFocus()
                        viewModel.setMonitorFilter(it)
                    }
                )
            }

            // Category Filter Chips (with counts) — Debloat Tab
            if (uiState.selectedTab == 1) {
                FilterChipsRow(
                    selectedFilter = uiState.selectedSafetyFilter,
                    totalCount = totalInstalledCount,
                    safeCount = safeCount,
                    cautionCount = cautionCount,
                    userCount = userCount,
                    onFilterSelected = {
                        focusManager.clearFocus()
                        viewModel.setSelectedFilter(it)
                    }
                )
            }

            // App List
            if (uiState.isLoading && displayedApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentIndigo,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (displayedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No packages match the criteria",
                        color = colors.textMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayedApps, key = { it.packageName }) { app ->
                        AppItemCard(
                            app = app,
                            onFreeze = {
                                focusManager.clearFocus()
                                viewModel.freezeApp(app)
                            },
                            onUnfreeze = {
                                focusManager.clearFocus()
                                viewModel.unfreezeApp(app)
                            },
                            onUninstall = {
                                focusManager.clearFocus()
                                viewModel.uninstallApp(app)
                            },
                            onRestore = {
                                focusManager.clearFocus()
                                viewModel.restoreApp(app)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    isDarkTheme: Boolean,
    isRefreshing: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                letterSpacing = (-0.5).sp
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Theme Toggle Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Refresh Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentIndigo
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val colors = LocalAppColors.current

    NavigationBar(
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(width = 1.dp, color = colors.border)
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = "Monitor"
                )
            },
            label = { Text("Monitor", fontSize = 11.5.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentIndigo,
                selectedTextColor = AccentIndigo,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary,
                indicatorColor = colors.indigoSubtle
            )
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Debloat"
                )
            },
            label = { Text("Debloat", fontSize = 11.5.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentIndigo,
                selectedTextColor = AccentIndigo,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary,
                indicatorColor = colors.indigoSubtle
            )
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = "Restorable"
                )
            },
            label = { Text("Restorable", fontSize = 11.5.sp, fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentIndigo,
                selectedTextColor = AccentIndigo,
                unselectedIconColor = colors.textSecondary,
                unselectedTextColor = colors.textSecondary,
                indicatorColor = colors.indigoSubtle
            )
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchDismiss: () -> Unit
) {
    val colors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search applications & services...",
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchDismiss() },
                    onDone = { onSearchDismiss() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onSearchDismiss()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorChipsRow(
    selectedFilter: Int,
    totalCount: Int,
    activeCount: Int,
    backgroundCount: Int,
    onFilterSelected: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            FilterChipPill(
                label = "All Running",
                count = totalCount,
                isSelected = selectedFilter == 0,
                onClick = { onFilterSelected(0) }
            )
        }
        item {
            FilterChipPill(
                label = "Active UI",
                count = activeCount,
                isSelected = selectedFilter == 1,
                onClick = { onFilterSelected(1) }
            )
        }
        item {
            FilterChipPill(
                label = "Background",
                count = backgroundCount,
                isSelected = selectedFilter == 2,
                onClick = { onFilterSelected(2) }
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: SafetyLevel?,
    totalCount: Int,
    safeCount: Int,
    cautionCount: Int,
    userCount: Int,
    onFilterSelected: (SafetyLevel?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            FilterChipPill(
                label = "All",
                count = totalCount,
                isSelected = selectedFilter == null,
                onClick = { onFilterSelected(null) }
            )
        }
        item {
            FilterChipPill(
                label = "Safe",
                count = safeCount,
                isSelected = selectedFilter == SafetyLevel.SAFE_DEBLOAT,
                onClick = { onFilterSelected(SafetyLevel.SAFE_DEBLOAT) }
            )
        }
        item {
            FilterChipPill(
                label = "Optional",
                count = cautionCount,
                isSelected = selectedFilter == SafetyLevel.CAUTION,
                onClick = { onFilterSelected(SafetyLevel.CAUTION) }
            )
        }
        item {
            FilterChipPill(
                label = "User",
                count = userCount,
                isSelected = selectedFilter == SafetyLevel.USER_APP,
                onClick = { onFilterSelected(SafetyLevel.USER_APP) }
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentIndigo else colors.surface)
            .border(
                1.dp,
                if (isSelected) AccentIndigo else colors.border,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) colors.onIndigo else colors.textPrimary
            )
            Text(
                text = "$count",
                fontSize = 10.5.sp,
                color = if (isSelected) colors.onIndigo.copy(alpha = 0.8f) else colors.textMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
