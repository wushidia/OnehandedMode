package io.githuh.onehandedmode.hook

/** Known Xiaomi shell layouts, selected by their settings owner rather than SDK or brand. */
internal enum class XiaomiShellAbi(
    val settingsOwner: String,
    val settingsCallbackOwner: String,
    val receiverOwner: String,
    val gestureOwner: String,
    val inputHandlerOwner: String,
    val exclusionOwners: List<String>,
) {
    MULTITASKING(
        "com.android.wm.shell.multitasking.common.MultiTaskingSettingsObserver",
        "com.android.wm.shell.multitasking.common.MultiTaskingSettingsObserver\$3",
        "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventController\$EventReceiver",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeGestureHandler",
        "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventHandler",
        listOf(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeGestureHandler",
            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchAnimStatus",
        ),
    ),
    LEGACY(
        "com.android.wm.shell.miuifreeform.MiuiFreeformModeObserver",
        "com.android.wm.shell.miuifreeform.MiuiFreeformModeObserver\$1",
        "com.android.wm.shell.miuimultiwinswitch.miuiwindowdecor.MiuiWindowDecorViewModel\$EventReceiver",
        "com.android.wm.shell.miuifreeform.MiuiFreeformModeGestureHandler",
        "com.android.wm.shell.miuimultiwinswitch.miuiwindowdecor.MiuiWindowDecorViewModel",
        listOf(
            "com.android.wm.shell.miuifreeform.MiuiFreeformModeGestureHandler",
            "com.android.wm.shell.miuimultiwinswitch.miuiwindowdecor.MiuiWindowDecorViewModel",
        ),
    );

    companion object {
        fun detect(loader: ClassLoader): XiaomiShellAbi? = entries.firstOrNull { abi ->
            try {
                Class.forName(abi.settingsOwner, false, loader)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }
}

/** R8 can retain the callback field, or register a lambda directly in SysUiState.mCallbacks. */
internal fun launcherStateCallbackTypes(serviceType: Class<*>, service: Any? = null): Set<Class<*>> {
    val types = mutableSetOf<Class<*>>()
    serviceType.declaredFields.firstOrNull { it.name == "mSysUiStateCallback" }?.let { field ->
        // New Xiaomi builds can retain the concrete type but remove its assignment.
        if (!field.type.isInterface) types.add(field.type)
        if (service != null) {
            field.isAccessible = true
            field.get(service)?.let { types.add(it.javaClass) }
        }
    }
    if (service != null) {
        serviceType.declaredFields.firstOrNull { it.name == "mSysUiState" }?.let { field ->
            field.isAccessible = true
            val state = field.get(service)
            if (state != null) {
                state.javaClass.declaredFields.firstOrNull { it.name == "mCallbacks" }?.let { callbacks ->
                    callbacks.isAccessible = true
                    (callbacks.get(state) as? Iterable<*>)?.filterNotNull()?.forEach {
                        types.add(it.javaClass)
                    }
                }
            }
        }
    }
    return types
}
