package pos.helper.thermalprinter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RestartReceiver : BroadcastReceiver() {

    companion object {
        const val REQUEST_CODE = 1001
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Restart receiver triggered")

        try {
            val serviceIntent = Intent(context, WebSocketService::class.java)
            if (intent?.hasExtra("port") == true) {
                serviceIntent.putExtra("port", intent.getIntExtra("port", 8765))
            }

            context.startForegroundService(serviceIntent)

            Log.i(TAG, "WebSocket service restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart WebSocket service", e)
        }
    }
}