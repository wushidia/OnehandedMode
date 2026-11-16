package io.githuh.onehandedmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.UUID

/** SystemUI reads remote preferences; their owner writes the completed drag back. */
class ScaleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as ModuleApplication).saveScale(
            intent.getIntExtra(HandySettings.CUSTOM, HandySettings.DEFAULT_PERCENT),
            intent.getStringExtra(HandySettings.SCALE_CHANGE_ID) ?: UUID.randomUUID().toString(),
            ScaleWriteStamp(
                intent.getIntExtra(HandySettings.SCALE_CHANGE_BOOT, -1),
                intent.getLongExtra(HandySettings.SCALE_CHANGE_TIME, 0L),
            ),
            pending::finish,
        )
    }
}
