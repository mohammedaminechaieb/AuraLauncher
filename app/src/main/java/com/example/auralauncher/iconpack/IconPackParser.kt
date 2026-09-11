package com.auralauncher.app.iconpack

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

/**
 * Every icon pack (regardless of which launcher family it was originally
 * built for) bundles a resource literally named "appfilter.xml" — a flat
 * list of <item component="ComponentInfo{some.app/some.app.MainActivity}"
 * drawable="some_drawable_name" /> lines mapping a specific app's launcher
 * activity to one of the pack's own drawable resources. This is the real
 * mechanism — reading this file is what "icon pack support" actually
 * means, as opposed to the shape-masking IconThemeScreen already had.
 */
class IconPackParser(context: Context, private val iconPackPackage: String) {

    private val pm: PackageManager = context.packageManager
    private val iconPackResources: Resources? = runCatching { pm.getResourcesForApplication(iconPackPackage) }.getOrNull()

    /** componentName (e.g. "com.whatsapp/com.whatsapp.HomeActivity") -> drawable resource name */
    private val componentToDrawableName: Map<String, String> by lazy { parseAppFilter() }

    val isValid: Boolean get() = iconPackResources != null && componentToDrawableName.isNotEmpty()

    /** Returns the icon pack's own artwork for this app's launcher
     *  activity, or null if the pack doesn't have a specific icon for it
     *  (most packs cover popular apps only, not every app on the device —
     *  callers should fall back to the app's normal icon when this is null). */
    fun resolveIcon(componentName: String): Drawable? {
        val resources = iconPackResources ?: return null
        val drawableName = componentToDrawableName[componentName] ?: return null
        val resId = resources.getIdentifier(drawableName, "drawable", iconPackPackage)
        if (resId == 0) return null
        return runCatching { resources.getDrawable(resId, null) }.getOrNull()
    }

    private fun parseAppFilter(): Map<String, String> {
        val resources = iconPackResources ?: return emptyMap()
        val xmlResId = resources.getIdentifier("appfilter", "xml", iconPackPackage)
        if (xmlResId == 0) return emptyMap()

        val map = mutableMapOf<String, String>()
        runCatching {
            val parser = resources.getXml(xmlResId)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val componentAttr = parser.getAttributeValue(null, "component")
                    val drawableAttr = parser.getAttributeValue(null, "drawable")
                    if (componentAttr != null && drawableAttr != null) {
                        // componentAttr looks like "ComponentInfo{pkg/pkg.MainActivity}" —
                        // strip the wrapper down to "pkg/pkg.MainActivity" to match what
                        // PackageManager gives us elsewhere.
                        val cleaned = componentAttr.removePrefix("ComponentInfo{").removeSuffix("}")
                        map[cleaned] = drawableAttr
                    }
                }
                eventType = parser.next()
            }
        }
        return map
    }
}
