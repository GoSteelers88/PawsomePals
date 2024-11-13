// ChatBubbleArea.kt
package io.pawsomepals.app.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.ui.theme.MessageBubble

// ChatBubbleArea.kt
@Composable
fun ChatBubbleArea(
    messages: List<Message>,
    weatherCard: @Composable () -> Unit = {},
    parkCard: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true
    ) {
        item {
            weatherCard()
            parkCard()
        }

        items(messages.reversed()) { message ->
            MessageBubble(message = message)
        }
    }
}