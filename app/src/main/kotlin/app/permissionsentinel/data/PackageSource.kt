package app.permissionsentinel.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Enumerates launchable apps that declare sensitive permissions and builds
 * the coverage summary. Grant flags of foreign packages may be masked by the
 * system; the taxonomy reflects exactly what is visible to us.
 */
object PackageSource {

    fun loadSensitiveApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val pkgs = pm.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        )
        val out = mutableListOf<AppInfo>()
        for (info in pkgs) {
            val requested = info.requestedPermissions ?: continue
            val appInfo = info.applicationInfo ?: continue
            val sensitive = PermissionTaxonomy.capabilityStates(requested, info.requestedPermissionsFlags)
            if (sensitive.isEmpty()) continue
            if (pm.getLaunchIntentForPackage(info.packageName) == null) continue
            out += AppInfo(
                packageName = info.packageName,
                label = appInfo.loadLabel(pm).toString(),
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                sensitive = sensitive,
                requestedSensitiveCount = sensitive.size,
            )
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (_: Exception) {
        pkg
    }

    fun coverage(apps: List<AppInfo>): List<CapabilityCoverage> =
        Capability.entries.map { cap ->
            val requesting = apps.filter { it.sensitive.containsKey(cap) }
            CapabilityCoverage(
                capability = cap,
                requestingApps = requesting.size,
                grantedApps = requesting.count { it.sensitive[cap] == PermState.GRANTED },
                totalApps = apps.size,
            )
        }
}
