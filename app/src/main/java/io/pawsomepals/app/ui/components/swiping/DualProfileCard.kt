package io.pawsomepals.app.ui.components.swiping

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.google.android.gms.maps.model.LatLng
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.service.location.LocationSearchService


private fun safeLatLng(latitude: Double?, longitude: Double?): LatLng? {
    return if (latitude != null && longitude != null) {
        LatLng(latitude, longitude)
    } else null
}

// Then in the distance calculation:



@Composable
fun DualProfileCard(
    owner: User,
    dog: Dog,
    compatibilityScore: Double,
    locationService: LocationSearchService,
    onNavigatePhotos: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeGallery by remember { mutableStateOf(GalleryType.DOG) }
    var currentPhotoIndex by remember { mutableStateOf(0) }

    // Filter null photos and create safe lists
    val dogPhotos = remember(dog.photoUrls) { dog.photoUrls.filterNotNull() }
    val ownerPhotos = remember(owner.profilePictureUrl) { listOfNotNull(owner.profilePictureUrl) }

    val currentPhotos = when (activeGallery) {
        GalleryType.DOG -> dogPhotos
        GalleryType.OWNER -> ownerPhotos
    }
    val distance = remember(owner.latitude, owner.longitude, dog.latitude, dog.longitude) {
        val ownerLatLng = safeLatLng(owner.latitude, owner.longitude)
        val dogLatLng = safeLatLng(dog.latitude, dog.longitude)

        if (ownerLatLng != null && dogLatLng != null) {
            locationService.calculateDistance(ownerLatLng, dogLatLng)
        } else null
    }

    // Calculate distance if coordinates are available


    PhotoPreloader(ownerPhotos = ownerPhotos, dogPhotos = dogPhotos)

    Card(
        modifier = modifier.fillMaxWidth().height(600.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Photo View Area (70%)
            Box(modifier = Modifier.weight(0.7f).fillMaxWidth()) {
                AsyncImage(
                    model = currentPhotos.getOrNull(currentPhotoIndex),
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Photo Navigation Indicators
                PhotoIndicators(
                    totalPhotos = currentPhotos.size,
                    currentIndex = currentPhotoIndex,
                    modifier = Modifier.padding(top = 16.dp).align(Alignment.TopCenter)
                )

                // Gallery Toggles
                GalleryControls(
                    activeGallery = activeGallery,
                    onGalleryChange = { newGallery ->
                        activeGallery = newGallery
                        currentPhotoIndex = 0
                    },
                    modifier = Modifier.padding(16.dp).align(Alignment.TopEnd)
                )

                // Navigation Arrows
                NavigationControls(
                    currentIndex = currentPhotoIndex,
                    totalPhotos = currentPhotos.size,
                    onPrevious = { if (currentPhotoIndex > 0) currentPhotoIndex-- },
                    onNext = { if (currentPhotoIndex < currentPhotos.size - 1) currentPhotoIndex++ },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Thumbnail Strip (10%)
            ThumbnailStrip(
                ownerPhotos = ownerPhotos,
                dogPhotos = dogPhotos,
                currentIndex = currentPhotoIndex,
                activeGallery = activeGallery,
                onPhotoSelected = { index, type ->
                    activeGallery = type
                    currentPhotoIndex = index
                },
                modifier = Modifier.weight(0.1f).fillMaxWidth().background(Color(0xFF1A1A1A))
            )

            // Profile Info (20%)
            ProfileInfo(
                owner = owner,
                dog = dog,
                compatibilityScore = compatibilityScore,
                distance = distance,
                locationService = locationService, // Add this
                modifier = Modifier.weight(0.2f).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            )
        }
    }
}

fun calculateDistance(point1: LatLng, point2: LatLng): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        point1.latitude, point1.longitude,
        point2.latitude, point2.longitude,
        results
    )
    return results[0]
}

@Composable
private fun PhotoIndicators(
    totalPhotos: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalPhotos) { index ->
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index == currentIndex) Color.White
                        else Color.White.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

@Composable
private fun GalleryControls(
    activeGallery: GalleryType,
    onGalleryChange: (GalleryType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GalleryToggleButton(
            selected = activeGallery == GalleryType.OWNER,
            onClick = { onGalleryChange(GalleryType.OWNER) },
            icon = Icons.Default.Person,
            contentDescription = "View owner photos"
        )
        GalleryToggleButton(
            selected = activeGallery == GalleryType.DOG,
            onClick = { onGalleryChange(GalleryType.DOG) },
            icon = Icons.Default.Pets,
            contentDescription = "View dog photos"
        )
    }
}

@Composable
private fun NavigationControls(
    currentIndex: Int,
    totalPhotos: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NavigationButton(
            onClick = onPrevious,
            enabled = currentIndex > 0,
            icon = Icons.Default.ChevronLeft,
            contentDescription = "Previous photo"
        )
        NavigationButton(
            onClick = onNext,
            enabled = currentIndex < totalPhotos - 1,
            icon = Icons.Default.ChevronRight,
            contentDescription = "Next photo"
        )
    }
}

@Composable
private fun CompatibilityScore(score: Double) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            border = BorderStroke(
                width = 4.dp,
                color = when {
                    score >= 0.8 -> Color.Green
                    score >= 0.6 -> Color.Yellow
                    else -> Color.Red
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(score * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = "Match",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
@Composable
fun DistanceDisplay(
    owner: User,
    dog: Dog,
    locationService: LocationSearchService,
    modifier: Modifier = Modifier
) {
    val distance = remember(owner.latitude, owner.longitude, dog.latitude, dog.longitude) {
        if (owner.latitude != null && owner.longitude != null &&
            dog.latitude != null && dog.longitude != null) {
            locationService.calculateDistance(
                LatLng(owner.latitude, owner.longitude),
                LatLng(dog.latitude!!, dog.longitude!!)
            )
        } else null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "${distance?.let { "${(it/1000).toInt()} km" } ?: "Unknown"} away",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun LocationInfoChip(
    owner: User,
    dog: Dog,
    locationService: LocationSearchService,
    modifier: Modifier = Modifier
) {
    val distance = remember(owner.latitude, owner.longitude, dog.latitude, dog.longitude) {
        if (owner.latitude != null && owner.longitude != null &&
            dog.latitude != null && dog.longitude != null) {
            locationService.calculateDistance(
                LatLng(owner.latitude, owner.longitude),
                LatLng(dog.latitude!!, dog.longitude!!)
            )
        } else null
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${distance?.let { "${(it/1000).toInt()} km" } ?: "Unknown"} away",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun GalleryToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            CircleShape
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProfileInfo(
    owner: User,
    dog: Dog,
    compatibilityScore: Double,
    distance: Float?,
    locationService: LocationSearchService,
    modifier: Modifier = Modifier
){
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "${owner.username ?: "User"} & ${dog.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                DistanceDisplay(
                    owner = owner,
                    dog = dog,
                    locationService = locationService
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoChip(icon = Icons.Default.Pets, text = dog.breed)
                    InfoChip(icon = Icons.Default.Cake, text = "${dog.age} years")
                    InfoChip(icon = Icons.Default.Speed, text = dog.energyLevel)
                }
            }

            // Compatibility Score - unchanged
            CompatibilityScore(score = compatibilityScore)
        }
    }
}

enum class GalleryType {
    DOG, OWNER
}


@OptIn(ExperimentalCoilApi::class)
@Composable
private fun PhotoPreloader(
    ownerPhotos: List<String>,
    dogPhotos: List<String>
) {
    val allPhotos = remember(ownerPhotos, dogPhotos) {
        ownerPhotos + dogPhotos
    }

    // Create painters for all photos but keep them invisible
    allPhotos.forEach { photoUrl ->
        // Create and cache the painter
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photoUrl)
                .size(Size.ORIGINAL) // Load full size
                .build()
        )

        // Monitor loading state
        val state = painter.state

        // Invisible box to hold the preloaded image
        Box(modifier = Modifier.size(1.dp).alpha(0f)) {
            if (state is AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(1.dp)
                )
            }
        }
    }
}
@Composable
private fun ThumbnailStrip(
    ownerPhotos: List<String>,
    dogPhotos: List<String>,
    currentIndex: Int,
    activeGallery: GalleryType,
    onPhotoSelected: (Int, GalleryType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ownerPhotos.forEachIndexed { index, url ->
            EnhancedThumbnail(
                url = url,
                isSelected = activeGallery == GalleryType.OWNER && index == currentIndex,
                type = GalleryType.OWNER,
                onClick = { onPhotoSelected(index, GalleryType.OWNER) }
            )
        }
        dogPhotos.forEachIndexed { index, url ->
            EnhancedThumbnail(
                url = url,
                isSelected = activeGallery == GalleryType.DOG && index == currentIndex,
                type = GalleryType.DOG,
                onClick = { onPhotoSelected(index, GalleryType.DOG) }
            )
        }
    }
}
@Composable
private fun Thumbnail(
    url: String,
    isSelected: Boolean,
    type: GalleryType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp)),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        onClick = onClick
    ) {
        Box {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Icon(
                imageVector = if (type == GalleryType.OWNER) Icons.Default.Person else Icons.Default.Pets,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PhotoView(
    photoUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photoUrl)
                .crossfade(true)
                .build()
        )

        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }
            is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Failed to load image",
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Image(
                    painter = painter,
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
@Composable
private fun EnhancedThumbnail(
    url: String,
    isSelected: Boolean,
    type: GalleryType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val painter = rememberAsyncImagePainter(url)
    val isLoading = painter.state is AsyncImagePainter.State.Loading

    Surface(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp)),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        onClick = onClick
    ) {
        Box {
            // Show loading indicator while image loads
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp
                )
            }

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Icon(
                imageVector = if (type == GalleryType.OWNER) Icons.Default.Person else Icons.Default.Pets,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun NavigationButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(32.dp)
            .background(Color.White.copy(alpha = 0.8f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black
        )
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}