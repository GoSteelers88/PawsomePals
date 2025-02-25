package io.pawsomepals.app.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel,
    navController: NavController,  // Add this parameter

    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val otherUserData by viewModel.otherUserData.collectAsState()
    val typingStatus by viewModel.typingStatus.collectAsState()
    val showLocationSearch by viewModel.showLocationSearch.collectAsState()

    if (showLocationSearch) {
        navController.navigate("chat/$chatId/location-search")
    } else {
        LaunchedEffect(chatId) {
            if (chatId.isNotBlank()) {
                viewModel.loadChatMessages(chatId)
                viewModel.markMessagesAsRead()
            }
        }

        Scaffold(
            topBar = {
                ChatTopBar(
                    otherUserData = otherUserData,
                    typingStatus = typingStatus,
                    onBackClick = onBackClick
                )
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            if (isUserLoggedIn) {
                ChatContent(
                    messages = messages,
                    onSendMessage = viewModel::sendMessage,
                    navController = navController, // Pass navController here

                            viewModel = viewModel, // Add this

                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                LoginPrompt(modifier = Modifier.padding(paddingValues))
            }
        }
    }
}

@Composable
private fun LoginPrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Sign in to Start Chatting",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect with other dog owners and arrange playdates for your furry friends",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { /* Navigate to login */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Sign In")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    otherUserData: User?,
    typingStatus: Boolean,
    onBackClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = otherUserData?.profilePictureUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_dog_placeholder)
                )
                Column {
                    Text(
                        text = otherUserData?.displayName ?: "Chat", // or userName, firstName, etc.
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (typingStatus) {
                        Text(
                            text = "typing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ChatContent(
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel,
    navController: NavController
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Messages List
        MessageList(
            messages = messages,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Input Area
        ChatInputArea(
            onSendMessage = onSendMessage,
            viewModel = viewModel,
            navController = navController  // Pass navController here
// Pass ViewModel
        )
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = messages.reversed(),
            key = { message ->
                // Use a combination of chatId, timestamp, and senderId for uniqueness
                "${message.chatId}_${message.timestamp}_${message.senderId}"
            }
        ) { message ->
            MessageItem(message = message)
        }
    }
}
@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Username text
        Text(
            text = message.senderName ?: "Unknown User",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        when (message.type) {
            MessageType.LOCATION -> LocationMessage(message)
            MessageType.SYSTEM -> SystemMessage(message)
            MessageType.PLAYDATE_SUGGESTION -> PlaydateSuggestionMessage(message)
            MessageType.IMAGE -> ImageMessage(message)
            else -> TextMessage(message)
        }

        Text(
            text = message.formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}


@Composable
private fun TextMessage(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (message.isFromCurrentUser) 20.dp else 4.dp,
                topEnd = if (message.isFromCurrentUser) 4.dp else 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = if (message.isFromCurrentUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isFromCurrentUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SystemMessage(message: Message) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp)
        )
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputArea(
    onSendMessage: (String) -> Unit,
    viewModel: ChatViewModel,
    navController: NavController
) {
    var messageText by remember { mutableStateOf("") }
    var isAttachmentMenuVisible by remember { mutableStateOf(false) }
    Column {
        // Attachment Menu
        AnimatedVisibility(
            visible = isAttachmentMenuVisible,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            AttachmentMenu(
                viewModel = viewModel,
                navController = navController
            )
        }

        // Input Field
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { isAttachmentMenuVisible = !isAttachmentMenuVisible }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add attachment",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
@Composable
fun AttachmentMenu(
    viewModel: ChatViewModel,
    navController: NavController
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AttachmentButton(
            icon = Icons.Default.Image,
            label = "Photo",
            onClick = { /* Handle photo */ }
        )
        AttachmentButton(
            icon = Icons.Default.CalendarToday,
            label = "Schedule Playdate",
            onClick = {
                // Navigate to PlaydateScreen with current chat context
                viewModel.currentChatId?.let { chatId ->
                    navController.navigate("playdate") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
    }
}

@Composable
private fun LocationMessage(message: Message) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (message.isFromCurrentUser)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.metadata?.get("locationName") as? String ?: "Unknown Location",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = message.metadata?.get("address") as? String ?: "",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.metadata?.get("hasFencing") == "true") {
                    AssistChip(
                        onClick = { },
                        label = { Text("Fenced Area") }
                    )
                }
                if (message.metadata?.get("isOffLeashAllowed") == "true") {
                    AssistChip(
                        onClick = { },
                        label = { Text("Off-Leash") }
                    )
                }
            }

            Button(
                onClick = { /* Handle map opening */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open in Maps")
            }
        }
    }
}

@Composable
private fun PlaydateSuggestionMessage(message: Message) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (message.isFromCurrentUser)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            Text(
                text = "Playdate Suggestion",
                style = MaterialTheme.typography.labelLarge,
                color = if (message.isFromCurrentUser)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Date and Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.metadata?.get("suggestedTime") as? String ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Location
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.metadata?.get("locationName") as? String ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Handle accept */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = { /* Handle decline */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
private fun ImageMessage(message: Message) {
    Column(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .widthIn(max = 280.dp)
    ) {
        // Image
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f/3f)
        ) {
            AsyncImage(
                model = message.content,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Caption if any
        message.metadata?.get("caption")?.let { caption ->
            Text(
                text = caption.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

@Composable
private fun AttachmentButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)  // Added horizontal padding
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp)  // Fixed width for consistent layout
        )
    }
}