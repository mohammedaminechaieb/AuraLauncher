package com.auralauncher.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.auralauncher.app.data.IconShape

@Composable
fun AppIconView(icon: Drawable, shape: IconShape, sizeDp: Int = 56, isPreShaped: Boolean = false) {
    val bitmap = remember(icon) { icon.toBitmap(width = 128, height = 128).asImageBitmap() }

    // isPreShaped = true for real icon-pack artwork (see iconpack/IconPackManager.kt) —
    // those images already have their own background/shape baked in by the
    // pack's designer, so clipping them the same way as a raw app icon would
    // just cut off part of the artwork. Shape theming only applies when
    // we're working with an app's own default icon.
    if (isPreShaped) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp)
        )
        return
    }

    // A true squircle needs a custom superellipse Path; RoundedCornerShape
    // at a high percent is a close-enough visual approximation for v0.1.
    val clipShape: Shape = when (shape) {
        IconShape.CIRCLE -> CircleShape
        IconShape.SQUIRCLE -> RoundedCornerShape(percent = 35)
        IconShape.ROUNDED_SQUARE -> RoundedCornerShape(16.dp)
        IconShape.TEARDROP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 4.dp)
        IconShape.SYSTEM_DEFAULT -> RoundedCornerShape(0.dp) // unclipped — app's own adaptive icon shape
    }

    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = null,
        modifier = Modifier.size(sizeDp.dp).clip(clipShape)
    )
}
