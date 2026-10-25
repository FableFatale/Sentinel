package app.permissionsentinel.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Reads usage-access state and foreground sessions. Real device data only. */
object UsageSource {

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Pairs lifecycle events in [from, to] into foreground sessions. */
    fun querySessions(
        context: Context,
        from: Long,
        to: Long,
        excludeSelf: Boolean = true,
    ): List<ActivitySession> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val raw = mutableListOf<UsageLogic.RawEvent>()
        val events = usm.queryEvents(from, to)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val kind = when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageLogic.RawEvent.ACTIVITY_RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageLogic.RawEvent.ACTIVITY_PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> UsageLogic.RawEvent.ACTIVITY_STOPPED
                else -> 0
            }
            if (kind != 0 && !(excludeSelf && ev.packageName == context.packageName)) {
                raw += UsageLogic.RawEvent(ev.packageName, ev.timeStamp, kind)
            }
        }
        return UsageLogic.pairSessions(raw, endAt = to)
    }
}
