package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern Android 17-inspired Frosted Glass card surface with subtle translucent tint,
 * glowing hairline border, and optional soft blur backing.
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0xFF0D1527).copy(alpha = 0.72f),
    borderColor: Color = Color.White.copy(alpha = 0.14f),
    accentGlow: Color? = null,
    content: @Composable () -> Unit
) {
    val borderBrush = if (accentGlow != null) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.28f),
                accentGlow.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderBrush),
        content = content
    )
}

/**
 * Ambient Glowing Light Orb for Android 17 dynamic glass depth effect.
 */
@Composable
fun AmbientGlowOrb(
    color: Color,
    size: Dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    alpha: Float = 0.20f,
    modifier: Modifier = Modifier
) {
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(48.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .then(blurModifier)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.4f),
                        Color.Transparent
                    )
                )
            )
    )
}
