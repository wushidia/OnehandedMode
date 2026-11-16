package io.githuh.onehandedmode

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleMemoryTest {
    private val initial = ScaleSelection()
    private fun saved(drag: ScaleSave) = initial.copy(
        mode = HandySettings.CUSTOM_MODE, custom = drag.percent, changeId = drag.changeId, stamp = drag.stamp,
    )
    private fun assertScale(percent: Int, memory: ScaleMemory) {
        assertEquals(percent / 100f, memory.scale, 0.00001f)
    }

    @Test fun allPresetsAreAppliedFromSavedSnapshotsWithoutAKeyNotification() {
        val memory = ScaleMemory(initial)
        for (percent in listOf(60, 80, 70)) {
            assertTrue(memory.synchronize(initial.copy(preset = percent, changeId = "menu-$percent")))
            assertScale(percent, memory)
        }
    }

    @Test fun customModeAndCustomValueBothTakeEffect() {
        val memory = ScaleMemory(initial)
        val custom = initial.copy(mode = HandySettings.CUSTOM_MODE, custom = 43, changeId = "custom-mode")
        assertTrue(memory.synchronize(custom))
        assertScale(43, memory)
        assertTrue(memory.synchronize(custom.copy(custom = 57, changeId = "custom-slider")))
        assertScale(57, memory)
    }

    @Test fun entryRefreshesSavedSettingsEvenIfTheListenerHasNotRun() {
        val memory = ScaleMemory(initial)
        // No notification is delivered. The entry path reads the current remote snapshot itself.
        assertTrue(memory.synchronize(initial.copy(preset = 80)))
        assertScale(80, memory)
    }

    @Test fun reentryBeforeTheDragBroadcastIsSavedKeepsTheDraggedSize() {
        val memory = ScaleMemory(initial)
        memory.preview(.612f)
        val drag = memory.finishDrag(61)
        repeat(3) {
            assertFalse(memory.synchronize(initial))
            assertScale(61, memory)
        }
        assertFalse(memory.synchronize(saved(drag)))
        assertScale(61, memory)
    }

    @Test fun unrelatedSettingsNotificationsCannotRestoreTheOldPreset() {
        val memory = ScaleMemory(initial)
        memory.finishDrag(62)
        // Blur/navigation changes do not change the size snapshot or its change ID.
        assertFalse(memory.synchronize(initial))
        assertScale(62, memory)
    }

    @Test fun oldDragAcknowledgementDoesNotUndoANewerDrag() {
        val memory = ScaleMemory(initial, "shell")
        val first = memory.finishDrag(61)
        val second = memory.finishDrag(54)
        assertFalse(memory.synchronize(saved(first)))
        assertScale(54, memory)
        assertFalse(memory.synchronize(saved(second)))
        assertScale(54, memory)
    }

    @Test fun menuCanReselectTheOldPresetWhileADragSaveIsPending() {
        val memory = ScaleMemory(initial)
        memory.finishDrag(61)
        // Values equal the pre-drag snapshot, but this is a new user action, not stale data.
        assertTrue(memory.synchronize(initial.copy(changeId = "menu-reselect")))
        assertScale(70, memory)
    }

    @Test fun lateDragSaveCannotUndoANewerMenuChoice() {
        val memory = ScaleMemory(initial)
        val drag = memory.finishDrag(61)
        assertTrue(memory.synchronize(initial.copy(preset = 80, changeId = "menu-new")))
        assertFalse(memory.synchronize(saved(drag)))
        assertScale(80, memory)
        assertFalse(memory.synchronize(saved(drag)))
        assertScale(80, memory)
    }

    @Test fun activeDragOwnsTheLiveSizeUntilCompletion() {
        val memory = ScaleMemory(initial)
        memory.preview(.623f)
        val menu = initial.copy(preset = 80, changeId = "menu-during-drag")
        assertFalse(memory.synchronize(menu, dragging = true))
        assertEquals(.623f, memory.scale, 0f)
        val drag = memory.finishDrag(62)
        assertFalse(memory.synchronize(menu))
        assertScale(62, memory)
        assertFalse(memory.synchronize(saved(drag)))
        assertScale(62, memory)
    }

    @Test fun cancelledFrameStillSavesTheLatestGestureValue() {
        val memory = ScaleMemory(initial)
        // No preview/frame was applied: gesture completion must save the pending endpoint.
        val drag = memory.finishDrag(49)
        assertScale(49, memory)
        assertEquals(49, drag.percent)
        assertFalse(memory.synchronize(initial))
        assertScale(49, memory)
    }

    @Test fun newShellSessionRestoresAPreviouslySavedDrag() {
        val first = ScaleMemory(initial, "old-shell")
        val drag = first.finishDrag(61)
        val restored = saved(drag)
        val restarted = ScaleMemory(restored, "new-shell")
        assertScale(61, restarted)
        assertTrue(restarted.synchronize(restored.copy(custom = 53, changeId = "menu-after-reboot")))
        assertScale(53, restarted)
    }

    @Test fun oldVersionPreferencesWithoutOrderingMetadataStillWork() {
        val memory = ScaleMemory(initial)
        assertTrue(memory.synchronize(initial.copy(mode = HandySettings.CUSTOM_MODE, custom = 47)))
        assertScale(47, memory)
        assertTrue(memory.synchronize(initial.copy(preset = 60)))
        assertScale(60, memory)
    }

    @Test fun delayedDragWriteCannotReplaceANewerSavedMenuChoice() {
        val memory = ScaleMemory(initial)
        val drag = memory.finishDrag(61, ScaleWriteStamp(8, 100))
        val menu = initial.copy(preset = 80, changeId = "menu", stamp = ScaleWriteStamp(8, 200))
        memory.synchronize(menu)
        var disk = menu
        if (drag.stamp.canReplace(disk.stamp)) disk = saved(drag)
        assertEquals(menu, disk)
        assertScale(80, ScaleMemory(disk))
    }

    @Test fun outOfOrderDragBroadcastsPersistOnlyTheNewestEndpoint() {
        val memory = ScaleMemory(initial)
        val first = memory.finishDrag(61, ScaleWriteStamp(8, 100))
        val second = memory.finishDrag(49, ScaleWriteStamp(8, 200))
        var disk = initial
        for (drag in listOf(second, first)) {
            if (drag.stamp.canReplace(disk.stamp)) disk = saved(drag)
        }
        assertEquals(second.changeId, disk.changeId)
        assertScale(49, ScaleMemory(disk))
    }

    @Test fun dragAfterAMenuEditCanStillSaveAndRestoreItsSize() {
        val menu = initial.copy(preset = 80, changeId = "menu", stamp = ScaleWriteStamp(8, 100))
        val memory = ScaleMemory(menu)
        val drag = memory.finishDrag(51, ScaleWriteStamp(8, 200))
        assertTrue(drag.stamp.canReplace(menu.stamp))
        assertScale(51, ScaleMemory(saved(drag)))
    }

    @Test fun previousShellsLateSaveCannotUndoANewerLiveDrag() {
        val previous = ScaleMemory(initial, "old-shell").finishDrag(61, ScaleWriteStamp(8, 100))
        val current = ScaleMemory(initial, "new-shell")
        current.finishDrag(49, ScaleWriteStamp(8, 200))
        assertFalse(current.synchronize(saved(previous)))
        assertScale(49, current)
    }

    @Test fun monotonicOrderingDoesNotRejectEditsAfterAReboot() {
        assertTrue(ScaleWriteStamp(9, 10).canReplace(ScaleWriteStamp(8, 1_000_000)))
    }

    @Test fun oldVersionsWithoutAWriteStampRetainTheirSaveBehavior() {
        assertTrue(ScaleWriteStamp().canReplace(ScaleWriteStamp(8, 200)))
        assertTrue(ScaleWriteStamp(8, 100).canReplace(ScaleWriteStamp()))
    }

    @Test fun sizeReadsUseOneAtomicPreferenceMapInsteadOfSeparateGetters() {
        var reads = 0
        val preferences = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            check(method.name == "getAll") { "Non-atomic preference read: ${method.name}" }
            reads++
            mapOf(HandySettings.MODE to HandySettings.CUSTOM_MODE,
                HandySettings.PRESET to 80, HandySettings.CUSTOM to 51,
                HandySettings.SCALE_CHANGE_ID to "edit",
                HandySettings.SCALE_CHANGE_BOOT to 8, HandySettings.SCALE_CHANGE_TIME to 100L)
        } as SharedPreferences
        assertEquals(ScaleSelection(HandySettings.CUSTOM_MODE, 80, 51, "edit", ScaleWriteStamp(8, 100)),
            HandySettings.selection(preferences))
        assertEquals(1, reads)
    }
}
