package com.auralauncher.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Collections
import java.util.WeakHashMap

/**
 * Drawable → bitmap conversions, done ahead of time on a background thread
 * (see AppListStore) so the first frame of the home screen and drawer
 * doesn't rasterise dozens of icons on the main thread — that stalled
 * startup long enough to trigger "isn't responding" on slow devices.
 * Weak keys: entries vanish with the app list that owns the drawables.
 */
object IconBitmapCache {
    private const val SIZE = 128
    private val cache: MutableMap<Drawable, ImageBitmap> = Collections.synchronizedMap(WeakHashMap())

    fun get(icon: Drawable): ImageBitmap = cache[icon] ?: render(icon)

    fun prewarm(icon: Drawable) {
        if (icon !in cache) render(icon)
    }

    private fun render(icon: Drawable): ImageBitmap =
        icon.toBitmap(SIZE, SIZE).asImageBitmap().also { cache[icon] = it }
}
