package com.android.wm.shell.multitasking.common

/** Class detection must not initialize vendor code. */
class MultiTaskingSettingsObserver {
    companion object {
        init { error("ABI detection initialized the multitasking settings owner") }
    }
}
