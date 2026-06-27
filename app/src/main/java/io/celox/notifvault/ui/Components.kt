package io.celox.notifvault.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.celox.notifvault.ui.theme.Motion

/**
 * Circular identity avatar: a deterministic solid color from [name] with either the
 * group glyph or the person's initials. We have no real profile pictures
 * (notifications don't carry them), so a stable colored monogram is the cleanest
 * recognizable stand-in. The foreground is picked by luminance so it stays legible
 * on every palette color and in both light and dark themes.
 */
@Composable
fun Avatar(name: String, isGroup: Boolean, size: Dp = 48.dp) {
    val color = identityColor(name)
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = onColor)
        } else {
            Text(
                text = initials(name),
                color = onColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

/**
 * Clickable with spring-physics press feedback: the element scales down on press and
 * springs back (spatial token), keeping the standard ripple. Use on whole-row / card
 * targets where a plain ripple feels flat.
 */
@Composable
fun Modifier.clickableScale(scaleTo: Float = 0.97f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = Motion.spatial(),
        label = "pressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
}
