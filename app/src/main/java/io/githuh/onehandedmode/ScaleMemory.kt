package io.githuh.onehandedmode

import java.util.UUID

/** One atomic snapshot of the saved size; unrelated preferences are intentionally excluded. */
internal data class ScaleSelection(
    val mode: Int = HandySettings.PRESET_MODE,
    val preset: Int = HandySettings.DEFAULT_PERCENT,
    val custom: Int = HandySettings.DEFAULT_PERCENT,
    val changeId: String = "",
    val stamp: ScaleWriteStamp = ScaleWriteStamp(),
) {
    val percent: Int get() = if (mode == HandySettings.CUSTOM_MODE) custom else preset
}

internal data class ScaleWriteStamp(val boot: Int = -1, val time: Long = 0L) {
    // Broadcast delivery and remote writes can lag behind the gesture that produced them.
    // Different boots are incomparable; old versions without metadata keep their write behavior.
    fun canReplace(saved: ScaleWriteStamp): Boolean =
        boot < 0 || saved.boot != boot || time >= saved.time
}

internal data class ScaleSave(val percent: Int, val changeId: String, val stamp: ScaleWriteStamp)

/** Shell-thread state. Saved settings and a live drag are separate sources of size changes. */
internal class ScaleMemory(initial: ScaleSelection, sessionId: String = UUID.randomUUID().toString()) {
    var scale = initial.percent / 100f
        private set
    private var observed = initial
    private var liveStamp = initial.stamp
    private val dragPrefix = "$sessionId:"
    private var dragSequence = 0L
    private var latestDragId: String? = null

    fun preview(value: Float) {
        scale = value
    }

    fun finishDrag(percent: Int, stamp: ScaleWriteStamp = ScaleWriteStamp()): ScaleSave {
        val changeId = "$dragPrefix${++dragSequence}"
        latestDragId = changeId
        liveStamp = stamp
        scale = percent / 100f
        return ScaleSave(percent, changeId, stamp)
    }

    /** Called on preference notifications AND before entry, so entry needs no timely callback. */
    fun synchronize(saved: ScaleSelection, dragging: Boolean = false): Boolean {
        if (saved == observed) return false
        observed = saved
        // A gesture owns its live size. Its completion will issue a new save after this edit.
        if (dragging) return false
        if (!saved.stamp.canReplace(liveStamp)) return false
        if (saved.changeId.startsWith(dragPrefix)) {
            // An older drag's delayed write must not undo a newer drag or a settings-page edit.
            if (saved.changeId != latestDragId) return false
        } else {
            // A fresh settings-page edit supersedes local drag memory, even at the same value
            // as the pre-drag snapshot. Its distinct change ID distinguishes it from stale data.
            latestDragId = null
        }
        val next = saved.percent / 100f
        liveStamp = saved.stamp
        val changed = next != scale
        scale = next
        return changed
    }
}
