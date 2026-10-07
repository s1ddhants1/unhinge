package io.github.s1ddhants1.unhinge.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import java.util.concurrent.ConcurrentHashMap

object HingeFonts {
    private val familyCache = ConcurrentHashMap<String, FontFamily>()

    fun getFontFamily(context: Context, fontName: String, fallback: FontFamily): FontFamily {
        familyCache[fontName]?.let { return it }

        val typeface = loadTypeface(context, fontName)
        val family = if (typeface != null) {
            try {
                FontFamily(typeface)
            } catch (_: Throwable) {
                fallback
            }
        } else {
            fallback
        }

        familyCache[fontName] = family
        return family
    }

    private fun loadTypeface(context: Context, fontName: String): Typeface? {

        try {
            val hostPkg = context.packageName
            val hostId = context.resources.getIdentifier(fontName, "font", hostPkg)
            if (hostId != 0) {
                val tf = ResourcesCompat.getFont(context, hostId)
                if (tf != null) return tf
            }
        } catch (_: Throwable) {}

        try {
            if (context.packageName != "co.hinge.app") {
                val hingeId = context.resources.getIdentifier(fontName, "font", "co.hinge.app")
                if (hingeId != 0) {
                    val tf = ResourcesCompat.getFont(context, hingeId)
                    if (tf != null) return tf
                }
            }
        } catch (_: Throwable) {}

        try {
            val unhingePkg = "io.github.s1ddhants1.unhinge"
            val unhingeCtx = if (context.packageName == unhingePkg) {
                context
            } else {
                context.createPackageContext(unhingePkg, Context.CONTEXT_RESTRICTED)
            }
            val unhingeId = unhingeCtx.resources.getIdentifier(fontName, "font", unhingePkg)
            if (unhingeId != 0) {
                val tf = ResourcesCompat.getFont(unhingeCtx, unhingeId)
                if (tf != null) return tf
            }
        } catch (_: Throwable) {}

        return null
    }

    fun tiemposRegular(context: Context): FontFamily =
        getFontFamily(context, "tiempos_headline_regular", FontFamily.Serif)

    fun modernEraRegular(context: Context): FontFamily =
        getFontFamily(context, "modern_era_regular", FontFamily.SansSerif)

    fun modernEraMedium(context: Context): FontFamily =
        getFontFamily(context, "modern_era_medium", FontFamily.SansSerif)

    fun modernEraBold(context: Context): FontFamily =
        getFontFamily(context, "modern_era_bold", FontFamily.SansSerif)
}
