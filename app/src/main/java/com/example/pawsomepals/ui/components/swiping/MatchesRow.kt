package com.example.pawsomepals.ui.components.swiping


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pawsomepals.data.model.Dog

@Composable
fun MatchesRow(matches: List<Dog>) {
    if (matches.isNotEmpty()) {
        Text(
            "Your Matches",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(matches, key = { it.id }) { match ->
                MatchItem(match)
            }
        }
        Divider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun MatchItem(match: Dog) {
    Column(
        modifier = Modifier
            .padding(end = 16.dp)
            .width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = match.profilePictureUrl,
            contentDescription = "Match ${match.name}",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = match.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}