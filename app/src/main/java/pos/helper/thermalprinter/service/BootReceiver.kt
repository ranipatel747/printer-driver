package pos.helper.thermalprinter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed received, starting WebSocket service")

            // Delay start to ensure system is ready
            Thread.sleep(5000)

            try {
                WebSocketService.startService(context)
                Log.i(TAG, "WebSocket service started on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WebSocket service on boot", e)
            }
        }
    }
}