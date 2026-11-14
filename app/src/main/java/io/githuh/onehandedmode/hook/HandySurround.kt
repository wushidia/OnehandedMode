package io.githuh.onehandedmode.hook

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Rect
import android.os.Handler
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.doOnNextLayout
import io.githuh.onehandedmode.R
import io.githuh.onehandedmode.HandySettings
import io.githuh.onehandedmode.ui.MiuixSettingsButton
import kotlin.math.roundToInt
import kotlin.math.abs

/** Transparent input windows and a small, independently positioned settings surface. */
internal class HandySurround(
    context: Context,
    private val shellHandler: Handler,
    private val onExit: () -> Unit,
    private val onSettings: () -> Unit,
    private val onSwitchSide: () -> Unit,
    private val onResize: (Float, Boolean) -> Unit,
) {
    private val uiHandler = Handler(context.mainLooper)
    private val windowContext = context.createWindowContext(2024, null)
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val resources = context.createPackageContext("io.githuh.onehandedmode", 0).resources
    private val density = context.resources.displayMetrics.density
    private val buttonSize = (35f * density).roundToInt()
    private val edge = 20f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val switchDistance = 32f * density
    private var prepared = false
    private var showing = false
    private var right = true
    private var width = 0
    private var height = 0
    private var gapWidth = 0f
    private var gapHeight = 0f
    private var entryFinished = false
    private val buttonParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
        gravity = Gravity.TOP or Gravity.LEFT
    }
    private val buttonHost = FrameLayout(windowContext)
    @Volatile private var buttonX = 0f
    @Volatile private var buttonY = 0f
    @Volatile private var buttonMovePosted = false
    private val moveButton = Runnable {
        buttonMovePosted = false
        if (showing && entryFinished) {
            val x = buttonX.roundToInt()
            val y = buttonY.roundToInt()
            if (settingsParams.x != x || settingsParams.y != y) {
                settingsParams.x = x
                settingsParams.y = y
                windowManager.updateViewLayout(buttonHost, settingsParams)
            }
        }
    }
    private lateinit var settingsButton: MiuixSettingsButton
    private val topView = SurroundView()
    private val sideView = SurroundView()
    private val topParams = params("MIUI one-handed top")
    private val sideParams = params("MIUI one-handed side")
    private val settingsParams = params("MIUI one-handed settings").apply {
        width = buttonSize
        height = buttonSize
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    fun show(screen: ScreenSpec, scale: Float, anchorRight: Boolean) {
        uiHandler.post {
            width = screen.width
            height = screen.height
            layout(scale, anchorRight)
            topParams.flags = topParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            sideParams.flags = sideParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            entryFinished = false
            prepared = true
            // The background surfaces stay fixed while these windows own surround touches.
        }
    }

    fun update(scale: Float, anchorRight: Boolean) {
        uiHandler.post {
            layout(scale, anchorRight)
            if (showing) {
                windowManager.updateViewLayout(topView, topParams)
                windowManager.updateViewLayout(sideView, sideParams)
            }
        }
    }

    private fun layout(scale: Float, anchorRight: Boolean) {
        right = anchorRight
        gapHeight = (height * (1f - scale)).roundToInt().toFloat()
        gapWidth = width * gapHeight / height
        val top = gapHeight.toInt()
        val side = gapWidth.roundToInt()
        topParams.width = width
        topParams.height = top
        sideParams.x = if (right) 0 else width - side
        sideParams.y = top
        sideParams.width = side
        sideParams.height = height - top
        sideView.systemGestureExclusionRects = listOf(Rect(0, 0, side, height - top))
        val left = if (right) 0 else width - side
        topView.systemGestureExclusionRects = listOf(Rect(left, 0, left + side, top))
    }

    fun onEntryFinished() {
        uiHandler.post {
            if (!prepared) return@post
            prepared = false
            settingsButton = MiuixSettingsButton(windowContext, buttonHost, resources.getString(R.string.settings)) {
                shellHandler.post { onSettings() }
            }
            settingsButton.view.alpha = 0f
            buttonHost.addView(settingsButton.view, buttonParams)
            settingsParams.x = buttonX.roundToInt()
            settingsParams.y = buttonY.roundToInt()
            windowManager.addView(topView, topParams)
            windowManager.addView(sideView, sideParams)
            windowManager.addView(buttonHost, settingsParams)
            settingsButton.start()
            showing = true
            entryFinished = true
            buttonHost.doOnNextLayout {
                if (showing && entryFinished) {
                    settingsButton.view.animate().alpha(1f).setDuration(160L).start()
                }
            }
            settingsButton.view.requestLayout()
        }
    }

    fun moveSettings(centerX: Float, centerY: Float) {
        buttonX = centerX - buttonSize / 2f
        buttonY = centerY - buttonSize / 2f
        if (!buttonMovePosted) {
            buttonMovePosted = true
            uiHandler.post(moveButton)
        }
    }

    fun onExitStarted() {
        uiHandler.post {
            if (!showing) return@post
            entryFinished = false
            // The background surface fills the surround; stop intercepting touches immediately.
            topParams.flags = topParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            sideParams.flags = sideParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.updateViewLayout(topView, topParams)
            windowManager.updateViewLayout(sideView, sideParams)
            settingsButton.view.animate().cancel()
            settingsButton.view.animate().alpha(0f).setDuration(100L).withEndAction { detachWindows() }.start()
        }
    }

    fun hide() {
        uiHandler.post {
            prepared = false
            if (showing) detachWindows()
        }
    }

    private fun detachWindows() {
        entryFinished = false
        settingsButton.view.animate().cancel()
        settingsButton.dispose()
        windowManager.removeViewImmediate(topView)
        windowManager.removeViewImmediate(sideView)
        windowManager.removeViewImmediate(buttonHost)
        buttonHost.removeAllViews()
        showing = false
    }

    private fun params(title: String) = WindowManager.LayoutParams(
        1, 1, 2024,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        setTitle(title)
        val privateFlags = WindowManager.LayoutParams::class.java.getDeclaredField("privateFlags")
        // SystemUI may exclude the whole blank edge from the system Back gesture.
        privateFlags.setInt(this, privateFlags.getInt(this) or 0x20000000 or 0x20)
    }

    private inner class SurroundView : FrameLayout(windowContext) {
        private var multiTouch = false
        private var startX = 0f
        private var startY = 0f
        private var startScale = 1f
        private var gesture = 0
        private var sideGesture = false

        private fun resize(event: MotionEvent, finished: Boolean) {
            val scale = (startScale - (event.rawY - startY) / this@HandySurround.height)
                .coerceIn(HandySettings.MIN_PERCENT / 100f, HandySettings.MAX_PERCENT / 100f)
            shellHandler.post { onResize(scale, finished) }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    multiTouch = false
                    startX = event.rawX
                    startY = event.rawY
                    startScale = 1f - gapHeight / this@HandySurround.height
                    gesture = 0
                    sideGesture = if (this@HandySurround.right) startX < gapWidth
                        else startX > this@HandySurround.width - gapWidth
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (gesture == 1) resize(event, true)
                    gesture = 0
                    multiTouch = true
                }
                MotionEvent.ACTION_MOVE -> if (!multiTouch) {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (gesture == 0 && maxOf(abs(dx), abs(dy)) > touchSlop) {
                        gesture = if (this !== sideView) 3 else if (abs(dy) > abs(dx)) 1 else 2
                    }
                    if (gesture == 1) resize(event, false)
                }
                MotionEvent.ACTION_UP -> if (!multiTouch) {
                    if (gesture == 1) {
                        resize(event, true)
                        return true
                    }
                    if (gesture == 2) {
                        val dx = event.rawX - startX
                        if (sideGesture && (if (this@HandySurround.right) dx > switchDistance else dx < -switchDistance)) {
                            shellHandler.post { onSwitchSide() }
                        }
                        return true
                    }
                    if (event.rawX >= buttonX && event.rawX <= buttonX + buttonSize &&
                        event.rawY >= buttonY && event.rawY <= buttonY + buttonSize) {
                        shellHandler.post { onSettings() }
                        return true
                    }
                    val scale = 1f - gapHeight / this@HandySurround.height
                    val left = if (this@HandySurround.right) gapWidth else 0f
                    val content = RectF(left, gapHeight,
                        left + this@HandySurround.width * scale, this@HandySurround.height.toFloat())
                    content.inset(-edge * scale, -edge * scale)
                    if (!content.contains(event.rawX, event.rawY)) shellHandler.post { onExit() }
                }
                MotionEvent.ACTION_CANCEL -> if (gesture == 1) resize(event, true)
            }
            return true
        }
    }
}
