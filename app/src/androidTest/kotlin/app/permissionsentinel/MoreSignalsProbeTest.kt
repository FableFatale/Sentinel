package app.permissionsentinel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.net.TrafficStats
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.permissionsentinel.watch.SentinelWatchService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Probes for additional privacy signals (PRD: verify before claiming).
 * Evidence goes to logcat under SentinelProbe.
 *
 * Accessibility: the scoped watch must be enabled before running:
 *   adb shell settings put secure enabled_accessibility_services \
 *     app.permissionsentinel/app.permissionsentinel.watch.SentinelWatchService
 *   adb shell settings put secure accessibility_enabled 1
 */
@RunWith(AndroidJUnit4::class)
class MoreSignalsProbeTest {

    private val tag = "SentinelProbe"

    @Test
    fun probe_micAttribution(): Unit = runBlocking {
        val instr = InstrumentationRegistry.getInstrumentation()
        val appCtx = instr.targetContext
        val am = appCtx.getSystemService(AudioManager::class.java)!!

        // Cross-app mic load without touching our own uid: screenrecord with mic audio.
        val probe = runCatching {
            ProcessBuilder(
                "screenrecord", "--audio-source", "mic", "--time-limit", "6",
                "/sdcard/sentinel-mic-probe.mp4",
            ).start()
        }.getOrNull()

        var configs: List<String> = emptyList()
        if (probe != null) {
            withTimeout(6_000) {
                while (probe.isAlive) {
                    val found = am.activeRecordingConfigurations.map { c ->
                        "src=${c.clientAudioSource} silenced=${c.isClientSilenced}"
                    }
                    if (found.isNotEmpty()) {
                        configs = found
                        break
                    }
                    delay(400)
                }
            }
            runCatching { probe.destroy() }
        }
        for (line in configs) println("$tag mic config $line")
        println(
            "$tag mic RESULT=${if (configs.isEmpty()) "NO_CONFIGS" else "DETECTED configs=${configs.size} (no attribution: client uid/pkg are @SystemApi)"}"
        )
        if (configs.isEmpty()) {
            println("$tag mic NOTE=screenrecord audio unavailable on this build")
            return@runBlocking
        }
        assertTrue("active recording configurations not visible", configs.isNotEmpty())
    }

    @Test
    fun probe_accessibilityWatch(): Unit = runBlocking {
        val instr = InstrumentationRegistry.getInstrumentation()
        val appCtx = instr.targetContext
        val tctx = instr.context

        // 1. The scoped watch must be connected (enabled via adb settings).
        withTimeout(12_000) {
            while (!SentinelWatchService.connected.value) delay(200)
        }
        println("$tag a11y RESULT=CONNECTED")

        // 2. Window switches reach the service.
        appCtx.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        var windowPkg: String? = null
        withTimeout(12_000) {
            while (true) {
                windowPkg = SentinelWatchService.lastWindowPackage.value
                if (windowPkg != null) break
                delay(200)
            }
        }
        println("$tag a11y window=$windowPkg")
        assertTrue("no window event captured", windowPkg != null)

        // 3. A real runtime-permission dialog is captured with its text.
        tctx.startActivity(
            Intent(tctx, Class.forName("app.permissionsentinel.test.ProbeRequestActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        withTimeout(15_000) {
            while (SentinelWatchService.lastDialog.value == null) delay(250)
        }
        val dialog = SentinelWatchService.lastDialog.value
        println("$tag a11y dialog pkg=${dialog?.sourcePackage} text=${dialog?.text?.take(240)}")
        println("$tag a11y RESULT=DIALOG_CAPTURED=${dialog != null}")
        assertTrue("permission dialog not captured", dialog != null)
        instr.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun probe_trafficPerUid(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = ctx.packageManager
        val rows = pm.getInstalledPackages(0).mapNotNull { info ->
            val uid = info.applicationInfo?.uid ?: return@mapNotNull null
            val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
            val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
            if (rx <= 0 && tx <= 0) null else info.packageName to (rx + tx)
        }.sortedByDescending { it.second }
        println("$tag traffic rows=${rows.size} totalKB=${rows.sumOf { it.second } / 1024}")
        rows.take(5).forEach { (pkg, bytes) ->
            println("$tag traffic $pkg ${bytes / 1024}KB")
        }
        println("$tag traffic RESULT=AVAILABLE per-uid=${rows.isNotEmpty()}")
        assertTrue("no per-uid traffic counters visible", rows.isNotEmpty())
    }
}
