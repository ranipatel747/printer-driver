package pos.helper.thermalprinter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import pos.helper.thermalprinter.R
import pos.helper.thermalprinter.data.websocket.WebSocketMessage
import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.PrinterStatus
import pos.helper.thermalprinter.printer.PrinterManager
import pos.helper.thermalprinter.websocket.PrinterWebSocketServer
import java.util.concurrent.Executors

class WebSocketService : Service(), PrinterWebSocketServer.PrinterStatusListener {

    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ThermalPrinterChannel"
        private const val DEFAULT_PORT = 8765

        fun startService(context: android.content.Context, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                putExtra("port", port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: android.content.Context) {
            val intent = Intent(context, WebSocketService::class.java)
            context.stopService(intent)
        }
    }

    private var webSocketServer: PrinterWebSocketServer? = null
    private val printerManager: PrinterManager by lazy {
        PrinterManager.getInstance(this)
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentPort = DEFAULT_PORT

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize printer manager to use Bluetooth
        printerManager.setConnectionType("bluetooth")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getIntExtra("port", DEFAULT_PORT)?.let { port ->
            if (currentPort != port) {
                currentPort = port
                restartWebSocketServer(port)
            }
        }

        if (webSocketServer == null) {
            startWebSocketServer(currentPort)
        }

        return START_STICKY
    }

    private fun startWebSocketServer(port: Int) {
        webSocketServer?.stopServer()

        webSocketServer = PrinterWebSocketServer(this, port).apply {
            setPrinterStatusListener(this@WebSocketService)
            Executors.newSingleThreadExecutor().execute {
                try {
                    start()
                    Log.i(TAG, "WebSocket server started on port $port")
                    updateNotification("WebSocket Server Running - Port $port")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start WebSocket server", e)
                    updateNotification("WebSocket Server Failed")
                }
            }
        }
    }

    private fun restartWebSocketServer(port: Int) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                startWebSocketServer(port)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketServer?.stopServer()
        serviceScope.cancel()
        Log.i(TAG, "WebSocket service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onPrintOrder(orderData: OrderData, printerWidth: String) {
        serviceScope.launch {
            updateNotification("Printing: ${orderData.orderId}")

            val success = withContext(Dispatchers.IO) {
                try {
                    printerManager.printReceipt(orderData, printerWidth)
                } catch (e: Exception) {
                    Log.e(TAG, "Error printing receipt", e)
                    false
                }
            }

            if (success) {
                Log.i(TAG, "Receipt printed successfully")
                // Broadcast success status
                webSocketServer?.broadcastMessage(
                    WebSocketMessage(
                        type = "print_complete",
                        id = orderData.orderId,
                        appStatus = "success"
                    )
                )
            } else {
                Log.w(TAG, "Receipt printing failed")
                // Broadcast failure status
                webSocketServer?.broadcastMessage(
                    WebSocketMessage(
                        type = "print_complete",
                        id = orderData.orderId,
                        appStatus = "failed"
                    )
                )
            }

            updateNotification("WebSocket Server Running - Port $currentPort")

            // Send updated printer status
            webSocketServer?.sendStatusUpdate(getStatus())
        }
    }

    override fun getStatus(): PrinterStatus {
        val printerStatus = printerManager.getStatus()
        val printerName = printerManager.getPrinterName()
        return PrinterStatus(
            connected = printerManager.isConnected(),
            type = printerStatus.type,
            name = printerName,
            paperStatus = printerStatus.paperStatus,
            inkStatus = printerStatus.inkStatus
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Thermal Printer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WebSocket server for thermal printer"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Thermal Printer Service")
            .setContentText("WebSocket Server Running - Port $currentPort")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Thermal Printer Service")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}