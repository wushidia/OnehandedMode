package io.githuh.onehandedmode

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.roundToInt
import java.util.UUID

object HandySettings {
    const val GROUP = "handy_mode"
    const val MODE = "scale_mode"
    const val PRESET = "preset_percent"
    const val CUSTOM = "custom_percent"
    // Ordering metadata, not an authorization check: distinguish edits from delayed drag saves.
    const val SCALE_CHANGE_ID = "scale_change_id"
    const val SCALE_CHANGE_BOOT = "scale_change_boot"
    const val SCALE_CHANGE_TIME = "scale_change_time"
    const val KEEP_NAVIGATION_GESTURES = "keep_navigation_gestures"
    const val BLUR_WALLPAPER = "blur_wallpaper"
    const val SAVE_SCALE_ACTION = "io.githuh.onehandedmode.action.SAVE_SCALE"
    const val PRESET_MODE = 0
    const val CUSTOM_MODE = 1
    const val DEFAULT_PERCENT = 70
    const val MIN_PERCENT = 20
    const val MAX_PERCENT = 90
    // User-selected presets, based on the original MI 9 4.5 / 4.0 / 3.5-inch sizes.
    val presets = listOf(80, 70, 60)

    internal fun selection(preferences: SharedPreferences): ScaleSelection {
        // Remote preferences can change between getters. Read mode and values from one map.
        val saved = preferences.all
        return ScaleSelection(
            mode = saved[MODE] as? Int ?: PRESET_MODE,
            preset = saved[PRESET] as? Int ?: DEFAULT_PERCENT,
            custom = saved[CUSTOM] as? Int ?: DEFAULT_PERCENT,
            changeId = saved[SCALE_CHANGE_ID] as? String ?: "",
            stamp = ScaleWriteStamp(
                saved[SCALE_CHANGE_BOOT] as? Int ?: -1,
                saved[SCALE_CHANGE_TIME] as? Long ?: 0L,
            ),
        )
    }

    fun percent(preferences: SharedPreferences): Int = selection(preferences).percent

    fun scale(preferences: SharedPreferences): Float = percent(preferences) / 100f

    internal fun scaleWriteStamp(context: Context) = ScaleWriteStamp(
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
        SystemClock.elapsedRealtimeNanos(),
    )

    fun saveScaleSetting(context: Context, preferences: SharedPreferences, key: String, value: Int) {
        val stamp = scaleWriteStamp(context)
        preferences.edit().putInt(key, value)
            .putString(SCALE_CHANGE_ID, UUID.randomUUID().toString())
            .putInt(SCALE_CHANGE_BOOT, stamp.boot).putLong(SCALE_CHANGE_TIME, stamp.time)
            // Serialize with receiver commits on the app main thread; apply's separate executor
            // could otherwise deliver an older write after a newer drag save.
            .commit()
    }

    fun migrate(preferences: SharedPreferences) {
        val editor = preferences.edit()
        if (!preferences.contains(PRESET) && preferences.contains("handy_mode_size")) {
            val oldSize = preferences.getFloat("handy_mode_size", 4f)
            val ratio = (oldSize / 6.39f * 100).roundToInt()
            editor.putInt(PRESET, presets.minBy { kotlin.math.abs(it - ratio) })
        } else if (preferences.contains(PRESET)) {
            val previous = preferences.getInt(PRESET, DEFAULT_PERCENT)
            if (previous !in presets) {
                val migrated = if (previous in listOf(85, 75, 65)) previous - 5
                    else presets.minBy { kotlin.math.abs(it - previous) }
                editor.putInt(PRESET, migrated)
            }
        }
        // Complete migration before the service is exposed to settings edits or queued drag saves.
        editor.remove("handy_mode_state").remove("handy_mode_size").commit()
    }
}
