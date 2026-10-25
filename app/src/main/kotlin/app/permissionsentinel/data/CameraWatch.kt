package app.permissionsentinel.data

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live cross-app camera-in-use detection via the public CameraManager
 * availability callback. No permission required; this is the only honest
 * device-local signal for camera activity. It does not identify the using app.
 */
class CameraWatch(context: Context) {

    private val manager = context.getSystemService(CameraManager::class.java)
    private val busyIds = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            synchronized(busyIds) {
                busyIds -= cameraId
                publish()
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            synchronized(busyIds) {
                busyIds += cameraId
                publish()
            }
        }
    }

    private fun publish() {
        val nowBusy = synchronized(busyIds) { busyIds.isNotEmpty() }
        _busy.value = nowBusy
    }

    fun start() {
        manager?.registerAvailabilityCallback(callback, handler)
    }

    fun stop() {
        manager?.unregisterAvailabilityCallback(callback)
    }
}
