
package io.pawsomepals.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.getMatchIcon
import io.pawsomepals.app.data.model.getReasonIcon
import io.pawsomepals.app.data.model.getStatusColor
import io.pawsomepals.app.data.model.getStatusIcon
import io.pawsomepals.app.viewmodel.MatchesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MatchesScreen(
    viewModel: MatchesViewModel,
    onNavigateBack: () -> Unit,
    onChatClick: (String) -> Unit,
    onSchedulePlaydate: (String) -> Unit
) {
    val matches by viewModel.matches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedFilter by remember { mutableStateOf<MatchStatus?>(null) }

    LaunchedEffect(Unit) {
        viewModel.checkAndUpdateExpiredMatches()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Matches") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Match Status Filter Chips
            MatchStatusFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            if (isLoading) {
                LoadingIndicator()
            } else {
                MatchList(
                    matches = matches.filter { match ->
                        selectedFilter?.let { match.status == it } ?: true
                    },
                    onAccept = viewModel::acceptMatch,
                    onDecline = viewModel::declineMatch,
                    onRemove = viewModel::removeMatch,
                    onChatClick = onChatClick,
                    onSchedulePlaydate = onSchedulePlaydate
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchStatusFilterChips(
    selectedFilter: MatchStatus?,
    onFilterSelected: (MatchStatus?) -> Unit
) {
    val filters = remember { MatchStatus.values().toList() }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text("All") }
            )
        }
        items(filters) { status ->
            FilterChip(
                selected = selectedFilter == status,
                onClick = { onFilterSelected(status) },
                label = { Text(status.name.lowercase().capitalize()) },
                leadingIcon = {
                    Text(
                        text = status.getStatusIcon(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchList(
    matches: List<Match>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRemove: (String) -> Unit,
    onChatClick: (String) -> Unit,
    onSchedulePlaydate: (String) -> Unit
) {
    if (matches.isEmpty()) {
        EmptyMatchesState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = matches,
                key = { it.id }
            ) { match ->
                MatchCard(
                    match = match,
                    modifier = Modifier.animateItemPlacement(),
                    onAccept = { onAccept(match.id) },
                    onDecline = { onDecline(match.id) },
                    onRemove = { onRemove(match.id) },
                    onChatClick = { onChatClick(match.id) },
                    onSchedulePlaydate = { onSchedulePlaydate(match.dog2Id) }
                )
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: Match,
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit,
    onChatClick: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = Color(match.matchType.getMatchColor().replace("#", "FF", true).toLong(16))
                .copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Match Header
            MatchHeader(match)

            Spacer(modifier = Modifier.height(8.dp))

            // Match Details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MatchDetails(match)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Match Actions
            MatchActions(
                match = match,
                expanded = expanded,
                onExpandClick = { expanded = !expanded },
                onAccept = onAccept,
                onDecline = onDecline,
                onRemove = onRemove,
                onChatClick = onChatClick,
                onSchedulePlaydate = onSchedulePlaydate
            )
        }
    }
}

@Composable
private fun MatchHeader(match: Match) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Match Type and Icon
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = match.matchType.getMatchIcon(),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = match.matchType.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Compatibility: ${(match.compatibilityScore * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Match Status Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(match.status.getStatusColor().replace("#", "FF", true).toLong(16))
        ) {
            Text(
                text = "${match.status.getStatusIcon()} ${match.status.name.lowercase().capitalize()}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchDetails(match: Match) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Match Reasons
        Text(
            text = "Why You Matched",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            match.matchReasons.forEach { reason ->
                AssistChip(
                    onClick = { },
                    label = { Text(reason.description) },
                    leadingIcon = { Text(reason.getReasonIcon()) }
                )
            }
        }

        // Location and Time Info
        if (match.locationDistance != null) {
            Text(
                text = "📍 ${String.format("%.1f", match.locationDistance)} km away",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Match Timing
        Text(
            text = "⏳ Expires in ${getTimeRemaining(match.expiryTimestamp)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MatchActions(
    match: Match,
    expanded: Boolean,
    onExpandClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit,
    onChatClick: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (match.status) {
            MatchStatus.PENDING -> {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Accept")
                }
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Decline")
                }
            }
            MatchStatus.ACTIVE -> {
                Button(
                    onClick = onChatClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat")
                }
                Button(
                    onClick = onSchedulePlaydate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule")
                }
            }
            else -> {
                Button(
                    onClick = onRemove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            }
        }

        IconButton(onClick = onExpandClick) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Show less" else "Show more"
            )
        }
    }
}

@Composable
private fun EmptyMatchesState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Matches Yet",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Keep swiping to find the perfect playdate for your pup!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private fun getTimeRemaining(expiryTimestamp: Long): String {
    val remainingTime = expiryTimestamp - System.currentTimeMillis()
    val days = remainingTime / (1000 * 60 * 60 * 24)
    val hours = (remainingTime % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)

    return when {
        days > 0 -> "$days days"
        hours > 0 -> "$hours hours"
        else -> "Soon"
    }
}
