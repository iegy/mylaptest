package net.iegy.hisn.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Horizontal swipe detector that works on top of children which handle drags themselves (a text field, for example).
 *
 * It watches the gesture in the Initial pass, so it sees the movement before the child does, and only consumes the
 * movement once it is clearly a horizontal swipe. Plain taps and long-presses still reach the child untouched.
 * The lambdas are read through rememberUpdatedState, so they never go stale.
 *
 * [onNext] fires for a swipe toward the "next" side (finger moves left in LTR, right in RTL), [onPrevious] for the other.
 */
fun Modifier.horizontalSwipe(onNext: () -> Unit, onPrevious: () -> Unit): Modifier = composed {
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    pointerInput(rtl) {
        val slop = viewConfiguration.touchSlop
        val threshold = 56.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = Offset.Zero
            var swiping = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp() || !change.pressed) break
                total += change.positionChange()
                if (!swiping && abs(total.x) > slop && abs(total.x) > abs(total.y) * 1.5f) swiping = true
                if (swiping) change.consume()
            }
            if (swiping && abs(total.x) >= threshold) {
                val towardNext = if (rtl) total.x > 0 else total.x < 0
                if (towardNext) next() else previous()
            }
        }
    }
}
