import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.pawsomepals.app.R

@Composable
fun EasterEggScreen(onNavigateBack: () -> Unit) {
    var pawClicks by remember { mutableIntStateOf(0) }
    var showTreat by remember { mutableStateOf(false) }
    val rotationState = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "You found the secret paw print!",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.paw_print),
                contentDescription = "Paw Print",
                modifier = Modifier
                    .size(200.dp)
                    .rotate(rotationState.value)
                    .clickable {
                        pawClicks++
                        coroutineScope.launch {
                            rotationState.animateTo(
                                targetValue = rotationState.value + 360f,
                                animationSpec = tween(500, easing = LinearEasing)
                            )
                        }
                        if (pawClicks >= 5) {
                            showTreat = true
                        }
                    }
            )

            if (showTreat) {
                Image(
                    painter = painterResource(id = R.drawable.dog_treat),
                    contentDescription = "Dog Treat",
                    modifier = Modifier.size(100.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (showTreat) {
            Text(
                "Woof! You found a treat!",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                "Keep tapping the paw...",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onNavigateBack) {
            Text("Return to the dog park")
        }
    }
}