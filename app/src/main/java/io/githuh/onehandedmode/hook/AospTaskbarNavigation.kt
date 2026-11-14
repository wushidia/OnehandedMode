package io.githuh.onehandedmode.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Matrix
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule

private const val OFFER = "io.githuh.onehandedmode.action.TASKBAR_CONTROLLER"
private const val REQUEST = "io.githuh.onehandedmode.action.REQUEST_TASKBAR_CONTROLLER"
private const val DESCRIPTOR = "io.githuh.onehandedmode.TaskbarGeometry"
private const val UPDATE = IBinder.FIRST_CALL_TRANSACTION
private const val ONE_HANDED_ACTIVE = 1L shl 16
internal val taskbarPackages = setOf("com.android.launcher3", "com.google.android.apps.nexuslauncher")

/** One-way geometry updates avoid broadcasts and preference writes on animation frames. */
internal class TaskbarGeometryServer {
    @Volatile private var peer: IBinder? = null
    @Volatile private var scale = 1f
    @Volatile private var x = 0f

    fun attach(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                peer = intent.getBundleExtra("controller")!!.getBinder("binder")!!
                update(scale, x)
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, IntentFilter(OFFER), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(OFFER))
        }
        taskbarPackages.forEach { context.sendBroadcast(Intent(REQUEST).setPackage(it)) }
    }

    fun update(visualScale: Float, offsetX: Float) {
        scale = visualScale
        x = offsetX
        val target = peer ?: return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeFloat(visualScale)
            data.writeFloat(offsetX)
            target.transact(UPDATE, data, null, IBinder.FLAG_ONEWAY)
        } catch (_: RemoteException) {
            // The launcher can be recreated independently of SystemUI.
            if (peer === target) peer = null
        } finally {
            data.recycle()
        }
    }
}

/** AOSP phone taskbars render the gesture handle in Launcher3 rather than SystemUI. */
internal class AospTaskbarNavigation(private val module: XposedModule, private val loader: ClassLoader) {
    private fun offer(view: View, controller: IBinder) {
        val bundle = Bundle().apply { putBinder("binder", controller) }
        view.context.sendBroadcast(Intent(OFFER).setPackage("com.android.systemui")
            .putExtra("controller", bundle))
    }

    fun install() {
        val dragLayer = runCatching {
            Class.forName("com.android.launcher3.taskbar.TaskbarDragLayer", false, loader)
        }.getOrNull() ?: return
        val manager = Class.forName("com.android.launcher3.taskbar.TaskbarManager", false, loader)
        val implementation = if (manager.isInterface) {
            Class.forName("com.android.launcher3.taskbar.TaskbarManagerImpl", false, loader)
        } else manager
        implementation.declaredMethods.forEach { module.deoptimize(it) }
        val flagsChanged = implementation.declaredMethods.single { it.name == "onSystemUiFlagsChanged" }
        val flagsIndex = flagsChanged.parameterTypes.indexOf(Long::class.javaPrimitiveType).let {
            if (it >= 0) it else 0
        }
        module.hook(flagsChanged).intercept { chain ->
            val args = Array<Any?>(flagsChanged.parameterCount) { chain.getArg(it) }
            val visibleFlags = (args[flagsIndex] as Number).toLong() and ONE_HANDED_ACTIVE.inv()
            args[flagsIndex] = if (flagsChanged.parameterTypes[flagsIndex] == Long::class.javaPrimitiveType)
                visibleFlags else visibleFlags.toInt()
            // Only taskbar rendering sees this mask; the launcher's gesture state stays intact.
            chain.proceed(args)
        }
        val attached = dragLayer.getDeclaredMethod("onAttachedToWindow")
        module.deoptimize(attached)
        module.hook(attached).intercept { chain ->
            val result = chain.proceed()
            val view = chain.thisObject as View
            if (view.display.displayId == 0) Client(view).attach()
            result
        }
        module.log(Log.INFO, "OneHandedMode", "AOSP taskbar navigation adapter installed")
    }

    private inner class Client(private val view: View) {
        private val handler = Handler(view.context.mainLooper)
        private val matrix = Matrix()
        @Volatile private var scale = 1f
        @Volatile private var x = 0f
        @Volatile private var posted = false
        private val applyGeometry = Runnable {
            posted = false
            if (!view.isAttachedToWindow) return@Runnable
            if (scale == 1f && x == 0f) {
                view.setAnimationMatrix(null)
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            } else {
                if (view.layerType != View.LAYER_TYPE_HARDWARE) view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                matrix.setScale(scale, scale)
                matrix.postTranslate(x, view.height * (1f - scale))
                view.setAnimationMatrix(matrix)
            }
        }
        private val controller = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != UPDATE) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(DESCRIPTOR)
                scale = data.readFloat()
                x = data.readFloat()
                if (!posted) {
                    posted = true
                    handler.post(applyGeometry)
                }
                return true
            }
        }
        private val requests = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { offer(view, controller) }
        }

        fun attach() {
            val context = view.context
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(requests, IntentFilter(REQUEST), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(requests, IntentFilter(REQUEST))
            }
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGeometry.run() }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) {
                    context.unregisterReceiver(requests)
                    view.removeOnAttachStateChangeListener(this)
                }
            })
            offer(view, controller)
        }
    }
}
