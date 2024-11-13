package io.pawsomepals.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun DirectionalIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val icon = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        Icons.Default.ArrowForward
    } else {
        Icons.Default.ArrowBack
    }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier
    )
}