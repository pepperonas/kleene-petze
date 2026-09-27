package io.celox.notifvault.ui.theme

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Motion tokens, read from the theme's [androidx.compose.material3.MotionScheme] (expressive):
 * physics-based springs, not durations.
 *
 *  - **spatial** — position, size, shape, rotation, scale. May overshoot.
 *  - **effects** — color and alpha. Never overshoots.
 *
 * `Fast` is for small elements (icons, chips, toggles, press feedback), `Slow` for large surfaces.
 * Until v1.10.0 this was a hand-rolled copy of the scheme (material3 1.3 had no public API); the
 * call sites stayed, only the source of the springs changed.
 */
object Motion {
    @Composable fun <T> spatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()
    @Composable fun <T> spatialFast(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()
    @Composable fun <T> spatialSlow(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()
    @Composable fun <T> effects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()
    @Composable fun <T> effectsFast(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()
}

/**
 * True when the user has turned system animations off (developer options or the accessibility
 * "remove animations" switch set ANIMATOR_DURATION_SCALE to 0). Decorative motion must not run
 * then; state changes simply snap. Re-read on every resume — it may change in the meantime.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return remember(state.isAtLeast(Lifecycle.State.RESUMED)) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Press feedback with physics: the element sinks on the theme's fast spatial spring and springs
 * back on release (snaps when animations are off). Same as Brutus and Flipper.
 */
@Composable
fun Modifier.springPressed(interactionSource: InteractionSource, scaleTo: Float = 0.94f): Modifier {
    val reduce = rememberReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed, reduce) {
        val target = if (pressed) scaleTo else 1f
        if (reduce) scale.snapTo(target) else scale.animateTo(target, spec)
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * Staggered spring entrance: the item fades in while rising and settling from a slight
 * under-scale, delayed by its [index] (capped, so a long list does not trickle in for seconds).
 * Runs once per item; a no-op under reduced motion. Ported from Flipper the Ripper.
 */
fun Modifier.springEntrance(index: Int = 0): Modifier = composed {
    if (rememberReducedMotion()) return@composed this
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(ScreenMotion.staggerDelay(index))
        progress.animateTo(1f, spec)
    }
    val p = progress.value.coerceIn(0f, 1f)
    alpha(p).graphicsLayer {
        translationY = (1f - p) * 24.dp.toPx()
        val s = ScreenMotion.ENTER_SCALE + (1f - ScreenMotion.ENTER_SCALE) * p
        scaleX = s; scaleY = s
    }
}

/**
 * Screen-to-screen motion, built only from the theme's [MotionScheme] springs. Kleene Petze's
 * navigation is purely hierarchical (list → chat → back), so a child rises over its parent while
 * the parent settles back; popping reverses both. Reduced motion collapses everything to None.
 */
class ScreenTransitions(private val motion: MotionScheme, private val reduceMotion: Boolean) {
    fun enter(): EnterTransition = if (reduceMotion) EnterTransition.None else
        fadeIn(motion.defaultEffectsSpec()) +
            slideInVertically(motion.defaultSpatialSpec()) { ScreenMotion.riseOffset(it) }

    fun exit(): ExitTransition = if (reduceMotion) ExitTransition.None else
        fadeOut(motion.defaultEffectsSpec()) +
            scaleOut(motion.defaultSpatialSpec(), targetScale = ScreenMotion.PARENT_SCALE)

    fun popEnter(): EnterTransition = if (reduceMotion) EnterTransition.None else
        fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = ScreenMotion.PARENT_SCALE)

    fun popExit(): ExitTransition = if (reduceMotion) ExitTransition.None else
        fadeOut(motion.fastEffectsSpec()) +
            slideOutVertically(motion.defaultSpatialSpec()) { ScreenMotion.riseOffset(it) }
}

/** Geometry behind the motion — pure, so unit tests can pin it. */
object ScreenMotion {
    /** How far (fraction of height) a child screen rises from. */
    const val RISE_FRACTION = 0.10f

    /** A parent shrinks to this while a child covers it, so the child visibly sits on top. */
    const val PARENT_SCALE = 0.98f

    /** List items settle from this under-scale. */
    const val ENTER_SCALE = 0.94f

    const val STAGGER_MS = 40L

    /** Beyond this many items the cascade stops growing — nobody waits for row 30. */
    const val STAGGER_CAP = 8

    fun riseOffset(height: Int): Int = (height * RISE_FRACTION).roundToInt()

    fun staggerDelay(index: Int): Long = index.coerceIn(0, STAGGER_CAP) * STAGGER_MS
}
