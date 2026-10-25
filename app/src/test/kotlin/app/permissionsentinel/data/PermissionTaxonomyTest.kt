package app.permissionsentinel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTaxonomyTest {

    @Test
    fun `maps known sensitive permissions`() {
        val caps = PermissionTaxonomy.sensitiveCapabilities(
            listOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.INTERNET", // not sensitive
            )
        )
        assertEquals(
            setOf(Capability.MIC, Capability.CAMERA, Capability.LOCATION, Capability.CONTACTS),
            caps,
        )
    }

    @Test
    fun `location variants collapse into one capability`() {
        val caps = PermissionTaxonomy.sensitiveCapabilities(
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
            )
        )
        assertEquals(setOf(Capability.LOCATION), caps)
    }

    @Test
    fun `granted flag produces GRANTED state`() {
        val states = PermissionTaxonomy.capabilityStates(
            arrayOf(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.INTERNET",
            ),
            intArrayOf(
                0x2, // granted
                0x0, // denied
                0x2,
            ),
        )
        assertEquals(PermState.GRANTED, states[Capability.CAMERA])
        assertEquals(PermState.DENIED, states[Capability.MIC])
        assertEquals(null, states[Capability.CONTACTS])
    }

    @Test
    fun `all zero flags on foreign package means RequestedOnly`() {
        // Typical when the system masks grant flags of other packages.
        val states = PermissionTaxonomy.capabilityStates(
            arrayOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            intArrayOf(0, 0),
        )
        assertEquals(PermState.REQUESTED_ONLY, states[Capability.CAMERA])
        assertEquals(PermState.REQUESTED_ONLY, states[Capability.MIC])
    }

    @Test
    fun `null flags means RequestedOnly`() {
        val states = PermissionTaxonomy.capabilityStates(
            arrayOf("android.permission.CAMERA"),
            null,
        )
        assertEquals(PermState.REQUESTED_ONLY, states[Capability.CAMERA])
    }

    @Test
    fun `duplicate capability keeps granted if any permission granted`() {
        val states = PermissionTaxonomy.capabilityStates(
            arrayOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
            ),
            intArrayOf(0x0, 0x2),
        )
        assertEquals(PermState.GRANTED, states[Capability.LOCATION])
        assertTrue(states.keys.all { it in Capability.entries.toSet() })
    }
}
