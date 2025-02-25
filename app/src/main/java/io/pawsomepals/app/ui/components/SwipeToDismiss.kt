import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@ExperimentalMaterial3Api
class DismissState(
    initialValue: SwipeToDismissBoxValue = SwipeToDismissBoxValue.Settled,
    confirmStateChange: (SwipeToDismissBoxValue) -> Boolean = { true }
) {
    var currentValue by mutableStateOf(initialValue)
        private set

    var targetValue by mutableStateOf(initialValue)
        private set

    var dismissDirection: SwipeToDismissBoxValue by mutableStateOf(SwipeToDismissBoxValue.Settled)

    private var confirmStateChange: (SwipeToDismissBoxValue) -> Boolean = confirmStateChange

    fun dismiss(direction: SwipeToDismissBoxValue) {
        if (confirmStateChange(direction)) {
            currentValue = direction
            targetValue = direction
            dismissDirection = direction
        }
    }

    fun reset() {
        currentValue = SwipeToDismissBoxValue.Settled
        targetValue = SwipeToDismissBoxValue.Settled
        dismissDirection = SwipeToDismissBoxValue.Settled
    }
}

@ExperimentalMaterial3Api
@Composable
fun SwipeToDismiss(
    state: DismissState,
    modifier: Modifier = Modifier,
    directions: Set<SwipeToDismissBoxValue> = setOf(
        SwipeToDismissBoxValue.StartToEnd,
        SwipeToDismissBoxValue.EndToStart
    ),
    dismissThreshold: Float = 0.5f,
    background: @Composable () -> Unit,
    dismissContent: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableStateOf(0f) }
    var totalWidth by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .onSizeChanged { coordinates ->
                totalWidth = coordinates.width.toFloat()
            }
    ) {
        Box(
            modifier = Modifier.matchParentSize()
        ) {
            background()
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (delta > 0 && SwipeToDismissBoxValue.StartToEnd in directions ||
                            delta < 0 && SwipeToDismissBoxValue.EndToStart in directions
                        ) {
                            offsetX = (offsetX + delta).coerceIn(-totalWidth, totalWidth)

                            val direction = if (delta > 0) {
                                SwipeToDismissBoxValue.StartToEnd
                            } else {
                                SwipeToDismissBoxValue.EndToStart
                            }
                            state.dismissDirection = direction

                            if (kotlin.math.abs(offsetX) > totalWidth * dismissThreshold) {
                                state.dismiss(direction)
                            }
                        }
                    }
                )
        ) {
            dismissContent()
        }
    }
}