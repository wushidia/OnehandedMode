package com.android.wm.shell.miuifreeform

/** Class detection must not initialize vendor code. */
class MiuiFreeformModeObserver {
    companion object {
        init { error("ABI detection initialized the legacy settings owner") }
    }
}
