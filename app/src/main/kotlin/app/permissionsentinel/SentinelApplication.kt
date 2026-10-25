package app.permissionsentinel

import android.app.Application
import app.permissionsentinel.data.SentinelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SentinelApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: SentinelRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SentinelRepository(this, appScope)
    }
}
