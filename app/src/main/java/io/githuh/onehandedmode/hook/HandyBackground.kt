package io.githuh.onehandedmode.hook

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.Surface
import android.view.SurfaceControl
import kotlin.math.max
import kotlin.math.roundToInt

/** Prepare the static surround once; scaling only changes the foreground display areas. */
@SuppressLint("BlockedPrivateApi") // Runs only inside platform SystemUI under LSPosed.
internal class HandyBackground {
    private var surface: SurfaceControl? = null
    private var pending = false
    private var width = 0
    private var height = 0
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var blurredWallpaper = false
    private var color = floatArrayOf(0f, 0f, 0f)
    private var alpha = 1f
    private val colorLayer = SurfaceControl.Builder::class.java.getDeclaredMethod("setColorLayer")
    private val setColor = SurfaceControl.Transaction::class.java.getDeclaredMethod(
        "setColor", SurfaceControl::class.java, FloatArray::class.java,
    )
    private val crop = SurfaceControl.Transaction::class.java.getDeclaredMethod(
        "setWindowCrop", SurfaceControl::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    )
    private val show = SurfaceControl.Transaction::class.java.getDeclaredMethod("show", SurfaceControl::class.java)
    private val remove = SurfaceControl.Transaction::class.java.getDeclaredMethod("remove", SurfaceControl::class.java)

    fun prepare(context: Context, screen: ScreenSpec, backgroundColor: Int, parent: SurfaceControl?, blur: Boolean) {
        width = screen.width
        height = screen.height
        blurredWallpaper = blur
        val builder = SurfaceControl.Builder()
            .setName(if (blur) "MIUI one-handed blurred wallpaper" else "MIUI one-handed background")
            .setParent(parent).setOpaque(true)
        if (blur) {
            bufferWidth = (width + 3) / 4
            bufferHeight = (height + 3) / 4
            builder.setBufferSize(bufferWidth, bufferHeight)
        } else {
            colorLayer.invoke(builder)
        }
        val layer = builder.build()
        surface = layer
        if (blur) {
            prepareWallpaper(context, layer)
        } else {
            color = floatArrayOf(Color.red(backgroundColor) / 255f,
                Color.green(backgroundColor) / 255f, Color.blue(backgroundColor) / 255f)
            alpha = Color.alpha(backgroundColor) / 255f
        }
        pending = true
    }

    private fun prepareWallpaper(context: Context, layer: SurfaceControl) {
        val drawable = WallpaperManager.getInstance(context).drawable!!
        val scale = max(bufferWidth.toFloat() / drawable.intrinsicWidth,
            bufferHeight.toFloat() / drawable.intrinsicHeight)
        val drawnWidth = (drawable.intrinsicWidth * scale).roundToInt()
        val drawnHeight = (drawable.intrinsicHeight * scale).roundToInt()
        val left = (bufferWidth - drawnWidth) / 2
        val top = (bufferHeight - drawnHeight) / 2
        val node = RenderNode("One-handed wallpaper blur").apply {
            setPosition(0, 0, bufferWidth, bufferHeight)
        }
        val recording = node.beginRecording(bufferWidth, bufferHeight)
        drawable.setBounds(left, top, left + drawnWidth, top + drawnHeight)
        drawable.draw(recording)
        node.endRecording()
        val radius = 60f * context.resources.displayMetrics.density
        node.setRenderEffect(RenderEffect.createBlurEffect(
            radius * bufferWidth / width, radius * bufferHeight / height, Shader.TileMode.CLAMP))
        val producer = Surface(layer)
        try {
            val canvas = producer.lockHardwareCanvas()
            canvas.drawColor(Color.BLACK)
            canvas.drawRenderNode(node)
            producer.unlockCanvasAndPost(canvas)
        } finally {
            producer.release()
        }
    }

    fun apply(tx: SurfaceControl.Transaction) {
        if (!pending) return
        val layer = surface!!
        if (blurredWallpaper) {
            tx.setScale(layer, width.toFloat() / bufferWidth, height.toFloat() / bufferHeight)
            crop.invoke(tx, layer, bufferWidth, bufferHeight)
        } else {
            setColor.invoke(tx, layer, color)
            tx.setAlpha(layer, alpha)
            crop.invoke(tx, layer, width, height)
        }
        tx.setLayer(layer, -1)
        show.invoke(tx, layer)
        pending = false
    }

    fun hide() {
        surface?.let { layer ->
            SurfaceControl.Transaction().use { tx ->
                remove.invoke(tx, layer)
                tx.apply()
            }
            layer.release()
        }
        surface = null
        pending = false
    }
}
