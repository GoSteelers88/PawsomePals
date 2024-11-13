package io.pawsomepals.app.ui.screens.playdate.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.pawsomepals.app.data.model.PlaydateTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaydateTopBar(
    currentTab: PlaydateTab,
    onTabSelected: (PlaydateTab) -> Unit,
    onNavigateBack: () -> Unit,
    isCalendarSynced: Boolean,
    onToggleCalendarSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text("Playdates") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = { onToggleCalendarSync() }
                ) {
                    Icon(
                        imageVector = if (isCalendarSynced) Icons.Default.Sync
                        else Icons.Default.SyncDisabled,
                        contentDescription = if (isCalendarSynced) "Calendar Synced"
                        else "Calendar Not Synced"
                    )
                }
            }
        )

        TabRow(selectedTabIndex = currentTab.ordinal) {
            PlaydateTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(text = tab.title) },
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                PlaydateTab.UPCOMING -> Icons.Default.Upcoming
                                PlaydateTab.CALENDAR -> Icons.Default.CalendarMonth
                                PlaydateTab.AVAILABLE -> Icons.Default.AccessTime
                                PlaydateTab.LOCATIONS -> Icons.Default.LocationOn
                                PlaydateTab.HISTORY -> Icons.Default.History
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

private val PlaydateTab.title: String
    get() = when (this) {
        PlaydateTab.UPCOMING -> "Upcoming"
        PlaydateTab.CALENDAR -> "Calendar"
        PlaydateTab.AVAILABLE -> "Available"
        PlaydateTab.LOCATIONS -> "Locations"
        PlaydateTab.HISTORY -> "History"
    }