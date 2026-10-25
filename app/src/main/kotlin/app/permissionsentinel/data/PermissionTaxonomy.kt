package app.permissionsentinel.data

/**
 * Maps platform permissions to the sensitive capability taxonomy.
 * Pure logic — unit tested.
 */
object PermissionTaxonomy {

    private val MAP: Map<String, Capability> = buildMap {
        put("android.permission.RECORD_AUDIO", Capability.MIC)
        put("android.permission.CAMERA", Capability.CAMERA)
        put("android.permission.ACCESS_FINE_LOCATION", Capability.LOCATION)
        put("android.permission.ACCESS_COARSE_LOCATION", Capability.LOCATION)
        put("android.permission.ACCESS_BACKGROUND_LOCATION", Capability.LOCATION)
        put("android.permission.READ_CONTACTS", Capability.CONTACTS)
        put("android.permission.WRITE_CONTACTS", Capability.CONTACTS)
    }

    /** PackageInfo.REQUESTED_PERMISSION_GRANTED */
    const val FLAG_GRANTED = 0x00000002

    fun capabilityOf(permission: String): Capability? = MAP[permission]

    fun sensitiveCapabilities(requestedPermissions: List<String>): Set<Capability> =
        requestedPermissions.mapNotNull { MAP[it] }.toSet()

    /**
     * Splits requested/flags arrays (same-length arrays from PackageInfo) into per-capability state.
     * Returns null for a capability that is not requested at all.
     */
    fun capabilityStates(
        requestedPermissions: Array<out String>,
        requestedPermissionsFlags: IntArray?,
    ): Map<Capability, PermState> {
        val result = mutableMapOf<Capability, PermState>()
        val flags = requestedPermissionsFlags
        val anyFlagSet = flags != null && flags.any { it and FLAG_GRANTED != 0 }
        for (i in requestedPermissions.indices) {
            val cap = MAP[requestedPermissions[i]] ?: continue
            val prev = result[cap]
            if (flags == null) {
                result[cap] = PermState.REQUESTED_ONLY
                continue
            }
            val granted = flags[i] and FLAG_GRANTED != 0
            if (granted) {
                result[cap] = PermState.GRANTED
            } else if (prev != PermState.GRANTED) {
                // Distinguish "system masks flags for foreign apps" from an observed deny:
                // if no flag at all is ever set, treat everything as requested-only.
                result[cap] =
                    if (anyFlagSet) PermState.DENIED else PermState.REQUESTED_ONLY
            }
        }
        return result
    }
}
