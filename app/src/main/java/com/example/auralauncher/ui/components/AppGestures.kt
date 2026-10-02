package com.auralauncher.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * One gesture handler for a home-screen icon, so the three interactions
 * never fire together (the old code attached a click listener AND a
 * long-press-drag detector, so a long press both opened a dialog and
 * started a drag):
 *   - tap                         → [onTap]
 *   - long-press, release in place → [onLongPress] (shows the actions sheet)
 *   - long-press, then move        → [onDragStart] / [onDrag] / [onDragEnd]
 * A swipe that starts on an icon is left alone so the pager and the
 * home-screen swipe gestures still work.
 */
fun Modifier.appIconGestures(
    key: Any,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && up.changedToUp() && !up.isConsumed &&
                (up.position - down.position).getDistance() < viewConfiguration.touchSlop
            ) {
                up.consume()
                onTap()
            }
            return@awaitEachGesture
        }

        onLongPressStart()
        var total = Offset.Zero
        var dragging = false
        val completed = drag(longPress.id) { change ->
            total += change.positionChange()
            if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                onDragStart(longPress.position)
            }
            if (dragging) onDrag(change.positionChange())
            change.consume()
        }
        currentEvent.changes.forEach { it.consume() }
        when {
            dragging -> onDragEnd(!completed)
            completed -> onLongPress()
            else -> Unit // cancelled before moving (e.g. parent took the gesture)
        }
    }
}
