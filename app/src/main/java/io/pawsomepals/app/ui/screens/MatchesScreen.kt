
package io.pawsomepals.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.getMatchIcon
import io.pawsomepals.app.data.model.getReasonIcon
import io.pawsomepals.app.data.model.getStatusColor
import io.pawsomepals.app.data.model.getStatusIcon
import io.pawsomepals.app.viewmodel.MatchesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class MatchUiState {
    object Idle : MatchUiState()
    object Loading : MatchUiState()
    data class Error(val message: String) : MatchUiState()
    data class Success(val message: String) : MatchUiState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MatchesScreen(
    viewModel: MatchesViewModel,
    onNavigateBack: () -> Unit,
    onChatClick: (String) -> Unit,
    onSchedulePlaydate: (String) -> Unit
) {
    val matches by viewModel.matches.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedFilter by remember { mutableStateOf<MatchStatus?>(null) }
    val uiState by viewModel.uiState.collectAsState()

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
            MatchStatusFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            if (isLoading) {
                LoadingIndicator()
            } else {
                val filteredMatches = if (selectedFilter != null) {
                    matches.filter { it.match.status == selectedFilter }
                } else {
                    matches
                }

                if (filteredMatches.isEmpty()) {
                    EmptyMatchesState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = filteredMatches,
                            key = { it.match.id }
                        ) { matchWithDetails ->
                            MatchCard(
                                matchWithDetails = matchWithDetails,
                                modifier = Modifier.animateItemPlacement(),
                                onAccept = { viewModel.acceptMatch(matchWithDetails.match.id) },
                                onDecline = { viewModel.declineMatch(matchWithDetails.match.id) },
                                onRemove = { viewModel.removeMatch(matchWithDetails.match.id) },
                                onChatClick = { onChatClick(matchWithDetails.match.id) },
                                onSchedulePlaydate = { onSchedulePlaydate(matchWithDetails.match.id) }
                            )
                        }
                    }
                }
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
@Composable
private fun MatchInfoSection(
    matchType: MatchType,
    compatibilityScore: Double,
    timestamp: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = matchType.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Compatibility: ${(compatibilityScore * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Matched ${formatTimestamp(timestamp)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MatchStatusSection(
    status: MatchStatus,
    hasUnreadMessages: Boolean,
    expiryTimestamp: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(status.getStatusColor().replace("#", "FF", true).toLong(16))
        ) {
            Text(
                text = "${status.getStatusIcon()} ${status.name.lowercase().capitalize()}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }

        Text(
            text = "Expires in ${getTimeRemaining(expiryTimestamp)}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PlaydatePreferences(
    location: String?,
    time: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Playdate Preferences",
                style = MaterialTheme.typography.titleSmall
            )
            if (location != null) {
                Text("📍 Preferred Location: $location")
            }
            if (time != null) {
                Text("🕒 Preferred Time: $time")
            }
        }
    }
}
@Composable
private fun MatchCardContent(
    match: Match,
    otherDog: Dog,
    distanceAway: String,
    expanded: Boolean,
    onExpandClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit,
    onChatClick: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        DogProfileHeader(
            dog = otherDog,
            distanceAway = distanceAway
        )

        MatchInfoSection(
            matchType = match.matchType,
            compatibilityScore = match.compatibilityScore,
            timestamp = match.timestamp
        )

        MatchStatusSection(
            status = match.status,
            hasUnreadMessages = match.hasUnreadMessages,
            expiryTimestamp = match.expiryTimestamp
        )

        if (expanded) {
            if (match.matchReasons.isNotEmpty()) {
                MatchReasonsList(reasons = match.matchReasons)
            }

            if (match.preferredPlaydateLocation != null || match.preferredPlaydateTime != null) {
                PlaydatePreferences(
                    location = match.preferredPlaydateLocation,
                    time = match.preferredPlaydateTime
                )
            }

            LocationDetails(
                dog1Lat = match.dog1Latitude,
                dog1Long = match.dog1Longitude,
                dog2Lat = match.dog2Latitude,
                dog2Long = match.dog2Longitude,
                distance = match.locationDistance
            )
        }

        MatchActions(
            match = match,
            expanded = expanded,
            onExpandClick = onExpandClick,
            onAccept = onAccept,
            onDecline = onDecline,
            onRemove = onRemove,
            onChatClick = onChatClick,
            onSchedulePlaydate = onSchedulePlaydate
        )
    }
}


private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

@Composable
private fun MatchCard(
    matchWithDetails: Match.MatchWithDetails,
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit,
    onChatClick: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    var isAccepting by remember { mutableStateOf(false) }
    var isDeclining by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = when {
            isAccepting -> 1.05f
            isDeclining -> 0.8f
            else -> 1f
        },
        label = "scale"
    )

    val rotation by animateFloatAsState(
        targetValue = when {
            isAccepting -> 2f
            isDeclining -> -2f
            else -> 0f
        },
        label = "rotation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        finishedListener = {
            if (!isVisible) {
                // Once fade out is complete, trigger the appropriate action
                when {
                    isAccepting -> onAccept()
                    isDeclining -> onDecline()
                }
            }
        },
        label = "alpha"
    )

    fun handleAccept() {
        isAccepting = true
        showConfetti = true
        scope.launch {
            delay(800)
            isVisible = false // Start fade out
        }
    }

    fun handleDecline() {
        isDeclining = true
        scope.launch {
            delay(300)
            isVisible = false // Start fade out
        }
    }

    if (alpha > 0f) { // Only render if not completely faded out
        CelebrationEffect(
            show = showConfetti,
            modifier = modifier
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .rotate(rotation)
                    .animateContentSize()
                    .alpha(alpha),
                colors = CardDefaults.cardColors(
                    containerColor = Color(matchWithDetails.match.matchType.getMatchColor()
                        .replace("#", "FF", true).toLong(16))
                        .copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                MatchCardContent(
                    match = matchWithDetails.match,
                    otherDog = matchWithDetails.otherDog,
                    distanceAway = matchWithDetails.distanceAway,
                    expanded = expanded,
                    onExpandClick = { expanded = !expanded },
                    onAccept = { handleAccept() },
                    onDecline = { handleDecline() },
                    onRemove = onRemove,
                    onChatClick = onChatClick,
                    onSchedulePlaydate = onSchedulePlaydate
                )
            }
        }
    }
}

@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    particleCount: Int = 20,
    colors: List<Color> = listOf(
        Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan
    )
) {
    Box(modifier = modifier) {
        repeat(particleCount) { index ->
            ConfettiParticle(
                delay = index * 50,
                colors = colors
            )
        }
    }
}

@Composable
private fun ConfettiParticle(
    delay: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    // States for particle animation
    var offsetY by remember { mutableFloatStateOf(0f) }
    val offsetX = remember { Random.nextFloat() * 500f }
    val rotation = remember { Random.nextFloat() * 360f }
    val size = remember { Random.nextFloat() * 12f + 8f }
    val color = remember { colors[Random.nextInt(colors.size)] }

    // Create infinite transition for continuous animation
    val infiniteTransition = rememberInfiniteTransition(label = "particle")

    // Animate vertical movement
    val yAnimation by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                delayMillis = delay,
                easing = FastOutLinearInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "y-position"
    )

    // Animate rotation
    val rotationAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                delayMillis = delay,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        rotate(rotationAnimation) {
            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = size.dp.toPx(),
                center = Offset(offsetX, yAnimation)
            )
        }
    }
}

// Extension to use in MatchCard
@Composable
fun CelebrationEffect(
    show: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (show) {
            ConfettiOverlay(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.7f),
                particleCount = 30,
                colors = listOf(
                    Color(0xFF1E88E5), // Blue
                    Color(0xFF43A047), // Green
                    Color(0xFFFFB300), // Yellow
                    Color(0xFFE53935), // Red
                    Color(0xFF5E35B1)  // Purple
                )
            )
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchReasonsList(reasons: List<MatchReason>) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = "Why You Matched",
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            reasons.forEach { reason ->
                AssistChip(
                    onClick = { },
                    label = { Text(reason.description) },
                    leadingIcon = { Text(reason.getReasonIcon()) }
                )
            }
        }
    }
}

@Composable
private fun LocationDetails(
    dog1Lat: Double?,
    dog1Long: Double?,
    dog2Lat: Double?,
    dog2Long: Double?,
    distance: Double?
) {
    if (distance != null && dog1Lat != null && dog1Long != null && dog2Lat != null && dog2Long != null) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = "Location Details",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Distance: ${String.format("%.1f", distance)} km",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
@Composable
private fun DogProfileHeader(dog: Dog, distanceAway: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = dog.profilePictureUrl,  // Changed from profileImageUrl
                contentDescription = "Dog profile picture",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = dog.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${dog.age}y • ${dog.breed}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            text = "📍 $distanceAway",
            style = MaterialTheme.typography.bodyMedium
        )
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
                        containerColor = colorScheme.primary
                    )
                ) {
                    Text("Accept")
                }
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error
                    )
                ) {
                    Text("Decline")
                }
            }
            MatchStatus.ACTIVE -> {
                Button(
                    onClick = onChatClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat")
                }
                Button(
                    onClick = onSchedulePlaydate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.tertiary
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
                        containerColor = colorScheme.error
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
                tint = colorScheme.primary
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
                color = colorScheme.onSurfaceVariant
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
