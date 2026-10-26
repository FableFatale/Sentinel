package app.permissionsentinel.data

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One active audio recording, as visible to a normal (unprivileged) app. */
data class RecordingClient(
    val audioSource: Int,
    /** True when the system has flagged the stream as silenced. */
    val silenced: Boolean,
)

/**
 * Live cross-app microphone detection through AudioManager's recording callback.
 * Attribution (which app records) is a @SystemApi and not visible here —
 * verified by probes — so clients are reported without a package name.
 */
class MicWatch(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private val _clients = MutableStateFlow<List<RecordingClient>>(emptyList())
    val clients: StateFlow<List<RecordingClient>> = _clients

    private val callback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            _clients.value = configs.map {
                RecordingClient(audioSource = it.clientAudioSource, silenced = it.isClientSilenced)
            }
        }
    }

    fun start() {
        audioManager?.registerAudioRecordingCallback(callback, null)
    }

    fun stop() {
        audioManager?.unregisterAudioRecordingCallback(callback)
    }
}
