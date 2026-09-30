package io.github.s1ddhants1.unhinge.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Native Hinge typography resolver providing exact matches for Hinge's signature typefaces:
 * - Tiempos Headline: Hinge's editorial serif for prompt answers, candidate headlines, and names.
 * - Modern Era: Hinge's clean geometric sans-serif for UI controls, questions, badges, and inputs.
 *
 * Resolves fonts from either the host Hinge application context (when running inside the host overlay),
 * from Unhinge's bundled APK font resources, or from Unhinge's package context with protective fallbacks.
 */
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
        // Strategy 1: Host app (co.hinge.app) resources
        try {
            val hostPkg = context.packageName
            val hostId = context.resources.getIdentifier(fontName, "font", hostPkg)
            if (hostId != 0) {
                val tf = ResourcesCompat.getFont(context, hostId)
                if (tf != null) return tf
            }
        } catch (_: Throwable) {}

        // Strategy 2: Explicitly query co.hinge.app if context wasn't co.hinge.app
        try {
            if (context.packageName != "co.hinge.app") {
                val hingeId = context.resources.getIdentifier(fontName, "font", "co.hinge.app")
                if (hingeId != 0) {
                    val tf = ResourcesCompat.getFont(context, hingeId)
                    if (tf != null) return tf
                }
            }
        } catch (_: Throwable) {}

        // Strategy 3: Unhinge module package resources (when in host app or standalone)
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

    // Tiempos Headline (Editorial Serif)
    fun tiemposRegular(context: Context): FontFamily =
        getFontFamily(context, "tiempos_headline_regular", FontFamily.Serif)

    fun tiemposMedium(context: Context): FontFamily =
        getFontFamily(context, "tiempos_headline_medium", FontFamily.Serif)

    fun tiemposSemiBold(context: Context): FontFamily =
        getFontFamily(context, "tiempos_headline_semi_bold", FontFamily.Serif)

    fun tiemposLight(context: Context): FontFamily =
        getFontFamily(context, "tiempos_headline_light", FontFamily.Serif)

    // Modern Era (Brand Sans-Serif)
    fun modernEraRegular(context: Context): FontFamily =
        getFontFamily(context, "modern_era_regular", FontFamily.SansSerif)

    fun modernEraMedium(context: Context): FontFamily =
        getFontFamily(context, "modern_era_medium", FontFamily.SansSerif)

    fun modernEraBold(context: Context): FontFamily =
        getFontFamily(context, "modern_era_bold", FontFamily.SansSerif)

    fun modernEraExtraBold(context: Context): FontFamily =
        getFontFamily(context, "modern_era_extra_bold", FontFamily.SansSerif)
}
