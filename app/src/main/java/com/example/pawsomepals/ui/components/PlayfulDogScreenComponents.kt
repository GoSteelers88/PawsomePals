
package com.example.pawsomepals.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pawsomepals.R
import com.example.pawsomepals.data.model.Dog
import kotlin.random.Random
import androidx.compose.material.icons.outlined.Bolt



@Composable
fun DogCard(
    dog: Dog,
    responses: Map<String, String>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(360.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 12.dp else 4.dp
        )
    ) {
        Box {
            // Dog Image
            AsyncImage(
                model = dog.profilePictureUrl ?: R.drawable.ic_dog_placeholder,
                contentDescription = "${dog.name}'s photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // Info overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                DogNameBadge(dog.name)
                Spacer(modifier = Modifier.height(8.dp))
                DogInfoChips(dog, responses)
            }

            // Personality indicator
            PersonalityBadge(
                responses["energyLevel"] ?: dog.energyLevel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun PersonalityContent(dog: Dog, responses: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        StatBar(
            label = "Energy Level",
            value = responses["energyLevel"] ?: dog.energyLevel,
            icon = Icons.Outlined.Bolt
        )
        StatBar(
            label = "Friendliness",
            value = responses["friendliness"] ?: dog.friendliness,
            icon = Icons.Default.Favorite
        )
        StatBar(
            label = "Trainability",
            value = responses["trainability"] ?: dog.trainability ?: "Medium",
            icon = Icons.Default.Psychology
        )
    }
}

@Composable
fun SocialContent(dog: Dog, responses: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        SocialCompatibilityItem(
            label = "With Dogs",
            value = responses["friendlyWithDogs"] ?: dog.friendlyWithDogs ?: "Unknown",
            icon = Icons.Default.Pets
        )
        SocialCompatibilityItem(
            label = "With Children",
            value = responses["friendlyWithChildren"] ?: dog.friendlyWithChildren ?: "Unknown",
            icon = Icons.Default.ChildCare
        )
        SocialCompatibilityItem(
            label = "With Strangers",
            value = responses["friendlyWithStrangers"] ?: dog.friendlyWithStrangers ?: "Unknown",
            icon = Icons.Default.People
        )
    }
}

@Composable
fun CareContent(dog: Dog, responses: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        CareNeedItem(
            label = "Exercise Needs",
            value = responses["exerciseNeeds"] ?: dog.exerciseNeeds ?: "Unknown",
            icon = Icons.Default.DirectionsRun
        )
        CareNeedItem(
            label = "Grooming Needs",
            value = responses["groomingNeeds"] ?: dog.groomingNeeds ?: "Unknown",
            icon = Icons.Default.Brush
        )
        if (dog.specialNeeds != null && dog.specialNeeds!!.isNotEmpty()) {
            SpecialNeedsCard(dog.specialNeeds!!)
        }
    }
}

@Composable
fun StatBar(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val progress = when (value.lowercase()) {
        "high" -> 1f
        "medium" -> 0.6f
        "low" -> 0.3f
        else -> 0.5f
    }

    val progressAnimation by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "progressAnimation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progressAnimation,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = when (value.lowercase()) {
                "high" -> MaterialTheme.colorScheme.primary
                "medium" -> MaterialTheme.colorScheme.secondary
                "low" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
fun SocialCompatibilityItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null)
            Text(label)
        }
        CompatibilityChip(value)
    }
}

@Composable
fun CareNeedItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null)
                Text(label)
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SpecialNeedsCard(specialNeeds: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                "Special Needs: $specialNeeds",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun CompatibilityChip(value: String) {
    val chipColor = when (value.lowercase()) {
        "high" -> MaterialTheme.colorScheme.primary
        "medium" -> MaterialTheme.colorScheme.secondary
        "low" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = chipColor.copy(alpha = 0.2f),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = value,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = chipColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun FloatingBonesAnimation() {
    val bones = remember {
        List(6) {
            BoneAnimationState(
                xOffset = Random.nextFloat() * 1000f,
                yOffset = Random.nextFloat() * -1000f,
                rotation = Random.nextFloat() * 360f,
                scale = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        bones.forEach { bone ->
            FloatingBone(bone)
        }
    }
}

@Composable
fun FloatingBone(state: BoneAnimationState) {
    val infiniteTransition = rememberInfiniteTransition(label = "bone")

    val xOffset by infiniteTransition.animateFloat(
        initialValue = state.xOffset,
        targetValue = state.xOffset + 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "xOffset"
    )

    val yOffset by infiniteTransition.animateFloat(
        initialValue = state.yOffset,
        targetValue = state.yOffset + 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = state.rotation,
        targetValue = state.rotation + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000)
        ),
        label = "rotation"
    )

    Icon(
        imageVector = Icons.Default.Pets,
        contentDescription = null,
        modifier = Modifier
            .offset(xOffset.dp, yOffset.dp)
            .rotate(rotation)
            .scale(state.scale)
            .size(24.dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    )
}

data class BoneAnimationState(
    val xOffset: Float,
    val yOffset: Float,
    val rotation: Float,
    val scale: Float
)

@Composable
fun DogNameBadge(name: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun DogInfoChips(dog: Dog, responses: Map<String, String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoChip(icon = Icons.Default.Pets, text = dog.breed)
        InfoChip(icon = Icons.Default.Cake, text = "${dog.age} years")
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PersonalityBadge(value: String, modifier: Modifier = Modifier) {
    val backgroundColor = when (value.lowercase()) {
        "high" -> MaterialTheme.colorScheme.primary
        "medium" -> MaterialTheme.colorScheme.secondary
        "low" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = CircleShape,
        color = backgroundColor.copy(alpha = 0.9f),
        modifier = modifier
    ) {
        Text(
            text = value,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}
