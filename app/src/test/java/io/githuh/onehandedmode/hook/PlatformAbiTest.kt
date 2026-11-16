package io.githuh.onehandedmode.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformAbiTest {
    private class ShellLoader(private val available: Set<String>) : ClassLoader(PlatformAbiTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.startsWith("com.android.wm.shell.") && name !in available) throw ClassNotFoundException(name)
            return super.loadClass(name, resolve)
        }
    }

    @Test fun oldShellIsDetectedWithoutTheMultitaskingNamespace() {
        val abi = XiaomiShellAbi.LEGACY
        assertEquals(abi, XiaomiShellAbi.detect(ShellLoader(setOf(abi.settingsOwner))))
        assertEquals("com.android.wm.shell.miuifreeform.MiuiFreeformModeObserver\$1", abi.settingsCallbackOwner)
        assertEquals("com.android.wm.shell.miuimultiwinswitch.miuiwindowdecor.MiuiWindowDecorViewModel\$EventReceiver",
            abi.receiverOwner)
        assertEquals(abi.inputHandlerOwner, abi.exclusionOwners.last())
    }

    @Test fun newShellKeepsTheExistingMultitaskingAdapter() {
        val abi = XiaomiShellAbi.MULTITASKING
        assertEquals(abi, XiaomiShellAbi.detect(ShellLoader(setOf(abi.settingsOwner))))
        assertEquals("com.android.wm.shell.multitasking.common.MultiTaskingSettingsObserver\$3",
            abi.settingsCallbackOwner)
    }

    @Test fun newSettingsOwnerWinsWhenBothLayoutsArePresent() {
        assertEquals(XiaomiShellAbi.MULTITASKING,
            XiaomiShellAbi.detect(ShellLoader(XiaomiShellAbi.entries.map { it.settingsOwner }.toSet())))
    }

    @Test fun unrelatedSystemDoesNotEnableTheXiaomiAdapter() {
        assertEquals(null, XiaomiShellAbi.detect(ShellLoader(emptySet())))
    }

    private interface StateCallback
    private class Callback : StateCallback
    private class SecondCallback : StateCallback
    private class State(val mCallbacks: List<Any?>) {
        val mFlags = 1 shl 16
    }
    private class LegacyService(val mSysUiState: State)
    private class RetainedFieldService {
        val mSysUiStateCallback: Callback? = null
    }
    private class InterfaceFieldService(val mSysUiStateCallback: StateCallback)
    private class MixedService(val mSysUiStateCallback: Callback, val mSysUiState: State)
    private class NoCallbackService

    @Test fun absentCallbackFieldUsesTheRegisteredLambdaTypes() {
        val state = State(listOf(Callback(), SecondCallback(), null))
        val service = LegacyService(state)
        assertEquals(emptySet<Class<*>>(), launcherStateCallbackTypes(service.javaClass))
        assertEquals(setOf(Callback::class.java, SecondCallback::class.java),
            launcherStateCallbackTypes(service.javaClass, service))
        // Discovery must not clear the actual one-handed flag or replace system callbacks.
        assertEquals(1 shl 16, state.mFlags)
        assertEquals(3, state.mCallbacks.size)
    }

    @Test fun concreteCallbackTypeSurvivesAnR8RemovedAssignment() {
        val service = RetainedFieldService()
        assertEquals(setOf(Callback::class.java), launcherStateCallbackTypes(service.javaClass))
        assertEquals(setOf(Callback::class.java), launcherStateCallbackTypes(service.javaClass, service))
    }

    @Test fun interfaceCallbackFieldUsesTheRuntimeImplementation() {
        val service = InterfaceFieldService(Callback())
        assertEquals(emptySet<Class<*>>(), launcherStateCallbackTypes(service.javaClass))
        assertEquals(setOf(Callback::class.java), launcherStateCallbackTypes(service.javaClass, service))
    }

    @Test fun registeredAndFieldCallbacksAreDeduplicated() {
        val callback = Callback()
        val service = MixedService(callback, State(listOf(callback, callback)))
        assertEquals(setOf(Callback::class.java), launcherStateCallbackTypes(service.javaClass, service))
    }

    @Test fun missingOptionalCallbackStorageDoesNotAbortInstallation() {
        val service = NoCallbackService()
        assertEquals(emptySet<Class<*>>(), launcherStateCallbackTypes(service.javaClass, service))
    }
}
