package io.celox.notifvault.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * A small spring-physics motion system in the spirit of M3 Expressive's `MotionScheme`
 * (whose public API only ships on material3 1.5.0-alpha — we stay on stable 1.3.x).
 *
 * Two token families, used strictly by what they animate:
 *  - **spatial** — position, size, shape, rotation, scale. May overshoot / bounce.
 *  - **effects** — color and alpha. High damping, never overshoots.
 *
 * Pick `Fast` for small elements (icons, chips, toggles, press feedback), the default for
 * most, `Slow` for large surfaces (sheets, full-screen, large containers).
 */
object Motion {
    fun <T> spatial(): SpringSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)  // ~400, gentle overshoot
    fun <T> spatialFast(): SpringSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = 800f)
    fun <T> spatialSlow(): SpringSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)        // ~200

    fun <T> effects(): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)        // ~1500, no bounce
    fun <T> effectsFast(): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)          // ~10000
}
