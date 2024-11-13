import androidx.compose.ui.unit.dp


import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.pawsomepals.app.R


enum class OutlineStyle {
    SOLID, DASHED, GRADIENT, GLOW
}

@Composable
fun CreativeOutlinedPhoto(
    photoUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    outlineStyle: OutlineStyle = OutlineStyle.SOLID,
    shape: Shape = CircleShape,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val shimmerEffect = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val parallaxEffect = infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val outlineAnimation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .drawWithContent {
                    drawContent()
                    when (outlineStyle) {
                        OutlineStyle.SOLID -> drawSolidOutline(outlineAnimation.value)
                        OutlineStyle.DASHED -> drawDashedOutline(outlineAnimation.value)
                        OutlineStyle.GRADIENT -> drawGradientOutline(outlineAnimation.value)
                        OutlineStyle.GLOW -> drawGlowOutline(outlineAnimation.value)
                    }
                }
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.LightGray.copy(alpha = 0.6f),
                                    Color.LightGray.copy(alpha = 0.2f),
                                    Color.LightGray.copy(alpha = 0.6f)
                                ),
                                start = Offset.Zero,
                                end = Offset(shimmerEffect.value, shimmerEffect.value)
                            )
                        )
                )
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .listener(
                        onStart = { isLoading = true },
                        onSuccess = { _, _ -> isLoading = false },
                        onError = { _, _ -> isLoading = false; isError = true }
                    )
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = parallaxEffect.value.dp, y = 0.dp)
            )

            if (isError) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = "Error",
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun DrawScope.drawSolidOutline(animationValue: Float) {
    val strokeWidth = 4.dp.toPx() * (0.5f + animationValue * 0.5f)
    drawRect(
        color = Color.White,
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawDashedOutline(animationValue: Float) {
    val strokeWidth = 4.dp.toPx()
    val dashLength = 10f * animationValue
    val gapLength = 10f * (1f - animationValue)
    drawRect(
        color = Color.White,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
        )
    )
}

private fun DrawScope.drawGradientOutline(animationValue: Float) {
    val strokeWidth = 4.dp.toPx()
    drawRect(
        brush = Brush.sweepGradient(
            0f to Color.Red,
            0.33f to Color.Green,
            0.66f to Color.Blue,
            1f to Color.Red,
            center = Offset(size.width / 2, size.height / 2)
        ),
        style = Stroke(width = strokeWidth),
        blendMode = BlendMode.SrcAtop
    )
}

private fun DrawScope.drawGlowOutline(animationValue: Float) {
    val strokeWidth = 4.dp.toPx()
    val glowWidth = 8.dp.toPx() * animationValue
    drawRect(
        color = Color.White.copy(alpha = 0.5f),
        style = Stroke(width = strokeWidth + glowWidth)
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(size.width / 2, size.height / 2),
            radius = size.minDimension / 2 + glowWidth
        ),
        blendMode = BlendMode.SrcAtop
    )
}