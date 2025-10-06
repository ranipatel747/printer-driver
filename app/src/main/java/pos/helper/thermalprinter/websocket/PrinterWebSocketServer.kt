package pos.helper.thermalprinter.websocket

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import pos.helper.thermalprinter.data.websocket.WebSocketMessage
import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.PrinterStatus
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.let

class PrinterWebSocketServer(
    private val context: Context,
    private val port: Int = 8765,
    private val authToken: String = "thermal-printer-2024"
) : WebSocketServer(InetSocketAddress(port)) {

    private val gson = Gson()
    private val connections = CopyOnWriteArrayList<WebSocket>()
    private var printerStatusListener: PrinterStatusListener? = null

    interface PrinterStatusListener {
        fun onPrintOrder(orderData: OrderData, printerWidth: String)
        fun getStatus(): PrinterStatus
    }

    fun setPrinterStatusListener(listener: PrinterStatusListener) {
        printerStatusListener = listener
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        conn?.let {
            connections.add(it)
            Log.d(TAG, "New connection from: ${it.remoteSocketAddress}")

            // Send initial status to connected client
            val status = printerStatusListener?.getStatus()
            status?.let { s ->
                val statusMessage = WebSocketMessage(
                    type = "status_update",
                    printer = s,
                    appStatus = "ready"
                )
                it.send(gson.toJson(statusMessage))
            }
        }
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        conn?.let {
            connections.remove(it)
            Log.d(TAG, "Connection closed: ${it.remoteSocketAddress}, reason: $reason")
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        message?.let { msg ->
            try {
                Log.d(TAG, "Received message: $msg")
                val wsMessage = gson.fromJson(msg, WebSocketMessage::class.java)

                when (wsMessage.type) {
                    "auth" -> {
                        if (wsMessage.token == authToken) {
                            Log.d(TAG, "Authentication successful")
                            conn?.send(gson.toJson(WebSocketMessage(
                                type = "auth_response",
                                appStatus = "authenticated"
                            )))
                        } else {
                            Log.w(TAG, "Authentication failed")
                            conn?.close(1008, "Invalid token")
                        }
                    }

                    "print_order" -> {
                        if (wsMessage.token == authToken) {
                            printerStatusListener?.onPrintOrder(
                                wsMessage.data!!,
                                wsMessage.printerWidth ?: "58mm"
                            )
                            Log.d(TAG, "Print order received: ${wsMessage.data?.orderId}")
                        } else {
                            Log.w(TAG, "Unauthorized print attempt")
                        }
                    }

                    "status_request" -> {
                        if (wsMessage.token == authToken) {
                            val status = printerStatusListener?.getStatus()
                            status?.let { s ->
                                val statusMessage = WebSocketMessage(
                                    type = "status_update",
                                    printer = s,
                                    appStatus = "ready"
                                )
                                conn?.send(gson.toJson(statusMessage))
                            }
                        }
                    }

                    "ping" -> {
                        if (wsMessage.token == authToken) {
                            conn?.send(gson.toJson(WebSocketMessage(
                                type = "pong"
                            )))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}")
                conn?.send(gson.toJson(WebSocketMessage(
                    type = "error",
                    appStatus = "Invalid message format"
                )))
            }
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e(TAG, "WebSocket error", ex)
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket server started on port $port")
    }

    fun broadcastMessage(message: WebSocketMessage) {
        val json = gson.toJson(message)
        Log.d(TAG, "Broadcasting: $json")

        connections.forEach { conn ->
            if (conn.isOpen) {
                conn.send(json)
            }
        }
    }

    fun sendStatusUpdate(status: PrinterStatus) {
        val message = WebSocketMessage(
            type = "status_update",
            printer = status,
            appStatus = "ready"
        )
        broadcastMessage(message)
    }

    fun stopServer() {
        connections.forEach { it.close() }
        connections.clear()
        super.stop()
    }

    companion object {
        private const val TAG = "PrinterWebSocketServer"
    }
}