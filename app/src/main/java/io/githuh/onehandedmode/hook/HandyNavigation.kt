package io.githuh.onehandedmode.hook

import android.content.Context
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import io.github.libxposed.api.XposedModule

/** Navigation bars live outside the display areas organized by one-handed mode. */
internal class HandyNavigation {
    private val bars = mutableSetOf<View>()
    private val handles = mutableMapOf<View, View>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var scale = 1f
    @Volatile private var x = 0f
    @Volatile private var updatePosted = false
    private val animationMatrix = Matrix()
    private val taskbar = TaskbarGeometryServer()
    private val updateViews = Runnable {
        updatePosted = false
        bars.forEach(::apply)
    }
    private val layoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> apply(view) }
    private val barAttachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) {
            bars.remove(view)
            view.removeOnLayoutChangeListener(layoutListener)
            view.removeOnAttachStateChangeListener(this)
        }
    }

    fun attach(context: Context) = taskbar.attach(context)

    fun install(module: XposedModule, loader: ClassLoader) {
        val handle = Class.forName("com.android.systemui.navigationbar.gestural.NavigationHandle", false, loader)
        // Resolve the pre/post .views namespace from the actual platform ButtonInterface.
        val buttonPackage = handle.interfaces.single { it.simpleName == "ButtonInterface" }.packageName
        val barType = Class.forName(buttonPackage.removeSuffix(".buttons") + ".NavigationBarView", false, loader)
        val attached = barType.getDeclaredMethod("onAttachedToWindow")
        module.deoptimize(attached)
        module.hook(attached).intercept { chain ->
            val result = chain.proceed()
            track(chain.thisObject as View)
            result
        }
        val constructor = handle.getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
        module.hook(constructor).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as View).addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    if (view.display.displayId != 0) return
                    val bar = generateSequence(view.parent as? View) { it.parent as? View }
                        .firstOrNull { barType.isInstance(it) } ?: return
                    handles[view] = bar
                    track(bar)
                }

                override fun onViewDetachedFromWindow(view: View) {
                    handles.remove(view)
                }
            })
            result
        }
    }

    private fun track(view: View) {
        if (view.display.displayId != 0 || !bars.add(view)) return
        view.addOnLayoutChangeListener(layoutListener)
        view.addOnAttachStateChangeListener(barAttachment)
        apply(view)
    }

    fun update(visualScale: Float, offsetX: Float) {
        scale = visualScale
        x = offsetX
        taskbar.update(visualScale, offsetX)
        if (!updatePosted) {
            updatePosted = true
            handler.post(updateViews)
        }
    }

    fun reset() {
        update(1f, 0f)
    }

    private fun apply(view: View) {
        if (scale == 1f && x == 0f) {
            view.setAnimationMatrix(null)
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            // Cache the navigation contents; only its RenderNode matrix changes during a drag.
            if (view.layerType != View.LAYER_TYPE_HARDWARE) view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            animationMatrix.setScale(scale, scale)
            animationMatrix.postTranslate(x, view.height * (1f - scale))
            view.setAnimationMatrix(animationMatrix)
        }
    }
}
