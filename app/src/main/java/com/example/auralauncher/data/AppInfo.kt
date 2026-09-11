package com.auralauncher.app.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    /** "pkg/pkg.MainActivity" — the exact format icon-pack appfilter.xml
     *  files use to key their per-app drawable mappings. */
    val componentName: String,
    /** ApplicationInfo.CATEGORY_* constant (Games, Social, Productivity,
     *  etc.) — what powers the drawer's category tabs. -1 (CATEGORY_UNDEFINED)
     *  when the app's own manifest doesn't declare one, which is common. */
    val category: Int
)
