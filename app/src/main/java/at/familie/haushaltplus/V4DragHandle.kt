package at.familie.haushaltplus

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Reorder handle with a 48dp interaction target. Accessibility fallback: list options menu. */
@Composable
fun V4DragHandle(key: String, onMove: (Int) -> Unit) {
    val latest = rememberUpdatedState(onMove)
    Icon(Icons.Default.DragHandle, contentDescription = "Lange drücken und ziehen zum Sortieren",
        modifier = Modifier.size(48.dp).pointerInput(key) {
            var distance = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { distance = 0f },
                onDrag = { change, amount ->
                    change.consume()
                    distance += amount.y
                },
                onDragEnd = {
                    val steps = (distance / 60.dp.toPx()).roundToInt()
                    if (steps != 0) latest.value(steps)
                    distance = 0f
                },
                onDragCancel = { distance = 0f }
            )
        }.padding(11.dp))
}
