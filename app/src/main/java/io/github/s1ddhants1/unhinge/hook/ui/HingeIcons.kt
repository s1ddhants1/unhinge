package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser as ComposePathParser
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import io.github.s1ddhants1.unhinge.util.attempt

object HingeIcons {

    const val HEART_PATH_DATA =
        "M12 19.455l7.777-7.147C20.54 11.477 21 10.406 21 9.237c0-2.641-2.347-4.782-5.242-4.782-1.474 0-2.805 0.555-3.758 1.448-0.953-0.893-2.284-1.448-3.758-1.448C5.347 4.455 3 6.596 3 9.237c0 1.169 0.46 2.24 1.224 3.071l4.58 4.312L12 19.455z"

    const val ROSE_PATH_DATA =
        "M3.757 1.904c-0.389-0.262-0.918-0.159-1.18 0.23-0.262 0.389-0.16 0.917 0.229 1.18l0.476 0.32 1.214 0.805c1.261 0.827 2.171 1.393 3.031 1.928l0.186 0.116c1.166 0.726 2.281 1.425 4.131 2.698-0.213 1.89-1.818 3.36-3.765 3.36-2.093 0-3.79-1.697-3.79-3.79V6.074l-1.7-1.132v3.809c0 3.032 2.458 5.49 5.49 5.49s5.489-2.458 5.489-5.49c0-0.024 0-0.049-0.003-0.073V2.96c0-0.946-1.058-1.513-1.845-0.981l-1.302 0.879C9.825 2.194 8.962 1.775 8 1.775c-1.04 0-1.965 0.49-2.558 1.251C4.943 2.699 4.388 2.33 3.757 1.904zm2.982 1.961c0.412 0.262 0.786 0.498 1.144 0.721 0.388-0.267 0.793-0.544 1.245-0.852C8.827 3.469 8.432 3.309 8 3.309c-0.499 0-0.949 0.214-1.261 0.556zm5.126 0.067v3.206c-0.955-0.644-1.706-1.127-2.408-1.57 0.672-0.459 1.431-0.975 2.408-1.636z"

    val HeartVector: ImageVector by lazy {
        ImageVector.Builder(
            name = "HingeHeart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = ComposePathParser().parsePathString(HEART_PATH_DATA).toNodes(),
            fill = SolidColor(Color.White)
        ).build()
    }

    val RoseVector: ImageVector by lazy {
        ImageVector.Builder(
            name = "HingeRose",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).addPath(
            pathData = ComposePathParser().parsePathString(ROSE_PATH_DATA).toNodes(),
            fill = SolidColor(Color.White)
        ).build()
    }

    fun getHeartDrawable(context: Context, tintColor: Int): Drawable {
        val hostDrawable = attempt("load host ic_heart_full", silent = true) {
            val resId = context.resources.getIdentifier("ic_heart_full", "drawable", "co.hinge.app")
            if (resId != 0) {
                context.resources.getDrawable(resId, context.theme)?.mutate()?.apply {
                    setTint(tintColor)
                }
            } else null
        }
        return hostDrawable ?: HingeHeartDrawable(tintColor)
    }

    fun getRoseDrawable(context: Context, tintColor: Int): Drawable {
        val hostDrawable = attempt("load host ic_rose_branded_small", silent = true) {
            val resId = context.resources.getIdentifier("ic_rose_branded_small", "drawable", "co.hinge.app").takeIf { it != 0 }
                ?: context.resources.getIdentifier("ic_rose", "drawable", "co.hinge.app")
            if (resId != 0) {
                context.resources.getDrawable(resId, context.theme)?.mutate()?.apply {
                    setTint(tintColor)
                }
            } else null
        }
        return hostDrawable ?: HingeRoseDrawable(tintColor)
    }

    class HingeHeartDrawable(private var color: Int = android.graphics.Color.parseColor("#ED5564")) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@HingeHeartDrawable.color
            style = Paint.Style.FILL
        }
        private val basePath: Path = try {
            PathParser.createPathFromPathData(HEART_PATH_DATA)
        } catch (_: Throwable) {
            Path()
        }
        private val renderPath = Path()
        private val matrix = Matrix()

        fun setColor(newColor: Int) {
            color = newColor
            paint.color = newColor
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            matrix.reset()
            val scale = minOf(bounds.width() / 24f, bounds.height() / 24f)
            val dx = bounds.left + (bounds.width() - 24f * scale) / 2f
            val dy = bounds.top + (bounds.height() - 24f * scale) / 2f
            matrix.postScale(scale, scale)
            matrix.postTranslate(dx, dy)
            renderPath.set(basePath)
            renderPath.transform(matrix)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(renderPath, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class HingeRoseDrawable(private var color: Int = android.graphics.Color.parseColor("#ED5564")) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@HingeRoseDrawable.color
            style = Paint.Style.FILL
        }
        private val basePath: Path = try {
            PathParser.createPathFromPathData(ROSE_PATH_DATA)
        } catch (_: Throwable) {
            Path()
        }
        private val renderPath = Path()
        private val matrix = Matrix()

        fun setColor(newColor: Int) {
            color = newColor
            paint.color = newColor
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            matrix.reset()
            val scale = minOf(bounds.width() / 16f, bounds.height() / 16f)
            val dx = bounds.left + (bounds.width() - 16f * scale) / 2f
            val dy = bounds.top + (bounds.height() - 16f * scale) / 2f
            matrix.postScale(scale, scale)
            matrix.postTranslate(dx, dy)
            renderPath.set(basePath)
            renderPath.transform(matrix)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(renderPath, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
