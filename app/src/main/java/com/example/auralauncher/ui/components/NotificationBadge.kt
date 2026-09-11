package com.auralauncher.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wraps any icon content with a small red dot in the top-right corner
 * when [show] is true — driven by AuraNotificationListenerService's
 * active-package set. Deliberately just a dot, not a number: getting an
 * accurate unread COUNT requires parsing notification content, which is
 * far more fragile across different apps' notification formats than
 * just knowing "does this app have something pending."
 */
@Composable
fun BadgedIcon(show: Boolean, content: @Composable () -> Unit) {
    Box {
        content()
        if (show) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(10.dp)
                    .background(Color.Red, CircleShape)
            )
        }
    }
}
