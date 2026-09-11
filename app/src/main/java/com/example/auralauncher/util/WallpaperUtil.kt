package com.auralauncher.app.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Same technique LockForge's overlay uses to show the real wallpaper —
 *  a launcher without it just looks like a blank app, not a home screen. */
@Composable
fun rememberDeviceWallpaper(context: Context): ImageBitmap? {
    return remember {
        runCatching {
            val drawable = WallpaperManager.getInstance(context).drawable
            (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
        }.getOrNull()
    }
}
