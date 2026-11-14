package io.githuh.onehandedmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** SystemUI reads remote preferences; their owner writes the completed drag back. */
class ScaleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as ModuleApplication).saveScale(
            intent.getIntExtra(HandySettings.CUSTOM, HandySettings.DEFAULT_PERCENT),
            pending::finish,
        )
    }
}
