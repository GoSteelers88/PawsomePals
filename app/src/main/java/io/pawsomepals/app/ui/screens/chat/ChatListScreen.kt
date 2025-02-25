package io.pawsomepals.app.ui.screens.chat

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    navigateToChat: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val chatsWithDetails by viewModel.chatsWithDetails.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val isLoading by viewModel.isLoadingChats.collectAsState()

    // Add logging for state changes
    Log.d("ChatListScreen", """
        State Update:
        - IsUserLoggedIn: $isUserLoggedIn
        - IsLoading: $isLoading
        - Chats Count: ${chatsWithDetails.size}
        - Chat IDs: ${chatsWithDetails.map { it.chat.id }}
    """.trimIndent())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.shadow(elevation = 0.dp)
            )
        }
    ) { paddingValues ->
        if (isUserLoggedIn) {
            Log.d("ChatListScreen", "User is logged in, showing ChatListContent")
            ChatListContent(
                modifier = Modifier.padding(paddingValues),
                chatsWithDetails = chatsWithDetails,
                onChatClick = navigateToChat,
                onDelete = viewModel::deleteChat
            )
        } else {
            Log.d("ChatListScreen", "User is not logged in, showing EmptyStateView")
            EmptyStateView()
        }
    }
}
@Composable
private fun ChatListContent(
    modifier: Modifier = Modifier,
    chatsWithDetails: List<Chat.ChatWithDetails>,
    onChatClick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        // New Matches Section
        val newMatches = chatsWithDetails.filter { it.isNewMatch }
        if (newMatches.isNotEmpty()) {
            NewMatchesSection(newMatches = newMatches, onChatClick = onChatClick)
        }

        // Recent Activity Header
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Active Chats
        val activeChats = chatsWithDetails.filter { !it.isNewMatch }
        LazyColumn {
            items(
                items = activeChats,
                key = { it.chat.id }
            ) { chatDetails ->
                SwipeableChatItem(
                    chatDetails = chatDetails,
                    onChatClick = onChatClick,
                    onDelete = onDelete
                )
            }
        }
    }
}



@Composable
private fun NewMatchesSection(
    newMatches: List<Chat.ChatWithDetails>,
    onChatClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = "New Matches 🎉",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(newMatches) { match ->
                NewMatchCard(match, onChatClick)
            }
        }

        Divider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun NewMatchCard(
    chatDetails: Chat.ChatWithDetails,
    onChatClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .size(160.dp)
            .clickable { onChatClick(chatDetails.chat.id) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image
            AsyncImage(
                model = chatDetails.otherDogPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_dog_placeholder)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dog Name
            Text(
                text = chatDetails.otherDogName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Match Badge
            Surface(
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "New Match",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableChatItem(
    chatDetails: Chat.ChatWithDetails,
    onChatClick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
        modifier = Modifier.animateContentSize(),
        content = {
            ChatItem(chatDetails, onChatClick)
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(dismissValue: SwipeToDismissBoxValue) {
    val color = MaterialTheme.colorScheme.error
    val iconTint = MaterialTheme.colorScheme.onError

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = iconTint
            )
            Text(
                text = "Delete",
                color = iconTint,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
private fun ChatItem(
    chatDetails: Chat.ChatWithDetails,
    onChatClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick(chatDetails.chat.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture with online indicator
            Box {
                AsyncImage(
                    model = chatDetails.otherDogPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_dog_placeholder)
                )

                if (chatDetails.chat.hasUnreadMessages) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatDetails.otherDogName,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = chatDetails.chat.formattedLastMessageTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Last message with optional playdate indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatDetails.chat.lastMessagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chatDetails.pendingPlaydate) {
                        Spacer(modifier = Modifier.width(8.dp))
                        PlaydateTag()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaydateTag() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Pending",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun EmptyStateView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_dog_placeholder),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Start matching with other dogs to begin chatting!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}