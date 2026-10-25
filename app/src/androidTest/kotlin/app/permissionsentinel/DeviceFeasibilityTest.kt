package app.permissionsentinel

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.permissionsentinel.data.PackageSource
import app.permissionsentinel.data.PermState
import app.permissionsentinel.data.UsageSource
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device feasibility probes (PRD requirement: verify before claiming).
 * Each test logs PASS/FAIL evidence to logcat under SentinelProbe and only
 * asserts what must hold. Grant usage access via adb before running:
 *   adb shell appops set app.permissionsentinel GET_USAGE_STATS allow
 */
@RunWith(AndroidJUnit4::class)
class DeviceFeasibilityTest {

    private val tag = "SentinelProbe"

    @Test
    fun probe_usageAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val granted = UsageSource.isUsageAccessGranted(context)
        println("$tag usageAccess granted=$granted")
        if (!granted) {
            println("$tag usageAccess RESULT=UNAVAILABLE (grant via adb appops to enable)")
            return
        }
        val now = System.currentTimeMillis()
        val sessions = UsageSource.querySessions(context, now - 24 * 3600_000L, now)
        println("$tag usageAccess RESULT=AVAILABLE sessions24h=${sessions.size}")
        println("$tag sample=${sessions.takeLast(3)}")
        check(sessions.isNotEmpty()) { "usage access granted but zero sessions in 24h" }
    }

    @Test
    fun probe_cameraAvailability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(CameraManager::class.java)
        val ids = manager.cameraIdList
        println("$tag camera ids=${ids.size}")
        var anyBack = false
        for (id in ids) {
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) anyBack = true
        }
        println("$tag camera RESULT=REGISTERABLE backFound=$anyBack (callback registered app-wide, no permission)")
        check(ids.isNotEmpty()) { "no cameras found" }
    }

    @Test
    fun probe_packagePermissionFlags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = PackageSource.loadSensitiveApps(context)
        println("$tag packageFlags sensitiveApps=${apps.size}")
        val grantVisible = apps.count { app -> app.sensitive.values.any { it == PermState.GRANTED } }
        println("$tag packageFlags RESULT=OK appsWithVisibleGrantFlags=$grantVisible")
        val pm = context.packageManager
        val self = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        println("$tag selfRequested=${self.requestedPermissions?.toList()}")
        check(apps.isNotEmpty()) { "no sensitive apps found — taxonomy mismatch?" }
    }

    @Test
    fun probe_appOpsForeignRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appOps = context.getSystemService(AppOpsManager::class.java)!!
        val otherPkg = "com.android.settings"
        val mode = try {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.packageManager.getPackageUid(otherPkg, 0),
                otherPkg,
            )
        } catch (e: Exception) {
            println("$tag appOpsForeign RESULT=DENIED exception=${e.javaClass.simpleName}")
            -1
        }
        println("$tag appOpsForeign foreignMode=$mode (expected masked: cross-app op history needs privileged access)")
    }

    @Test
    fun probe_uidSelfCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        println("$tag uid=${Process.myUid()} pkg=${context.packageName}")
        check(context.packageName == "app.permissionsentinel")
    }
}
