package com.auralauncher.app.iconpack

import android.content.Context
import android.graphics.drawable.Drawable
import com.auralauncher.app.data.AppInfo

/**
 * Deliberately plain SharedPreferences, not a Room table — AuraLauncher's
 * database schema just bumped for widget hosting (see Layer 2's README
 * note about fallbackToDestructiveMigration), and this is a single string
 * value that doesn't need Room's query/Flow machinery. Avoids stacking
 * another destructive schema change on top of the last one.
 */
class IconPackManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("auralauncher_iconpack_prefs", Context.MODE_PRIVATE)
    private var cachedParser: IconPackParser? = null
    private var cachedForPackage: String? = null

    var selectedPackage: String?
        get() = prefs.getString("selected_icon_pack", null)
        set(value) {
            prefs.edit().putString("selected_icon_pack", value).apply()
            cachedParser = null // force re-parse on next resolve after switching packs
            cachedForPackage = null
        }

    /** Returns the icon-pack drawable for this app if one's selected and the
     *  pack has a specific icon for it, otherwise null (caller falls back
     *  to the app's normal icon + shape theming, same as before this layer). */
    fun resolveIcon(app: AppInfo): Drawable? {
        val pack = selectedPackage ?: return null
        val parser = getOrCreateParser(pack) ?: return null
        return parser.resolveIcon(app.componentName)
    }

    private fun getOrCreateParser(iconPackPackage: String): IconPackParser? {
        if (cachedForPackage == iconPackPackage && cachedParser != null) return cachedParser
        val parser = IconPackParser(context, iconPackPackage)
        if (!parser.isValid) return null
        cachedParser = parser
        cachedForPackage = iconPackPackage
        return parser
    }
}
