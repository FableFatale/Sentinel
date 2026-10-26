package app.permissionsentinel.test

import android.app.Activity
import android.os.Bundle

/** Fires a real runtime-permission dialog; used by the accessibility probe. */
class ProbeRequestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1001)
    }
}
