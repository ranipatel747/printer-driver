package pos.helper.thermalprinter.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pos.helper.thermalprinter.data.websocket.WebSocketMessage
import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.PrinterStatus
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.UUID

class PrinterManager private constructor(private val context: Context) : PrinterInterface {

    companion object {
        private const val TAG = "PrinterManager"
        private const val BLUETOOTH_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

        @Volatile
        private var instance: PrinterManager? = null

        fun getInstance(context: Context): PrinterManager {
            return instance ?: synchronized(this) {
                instance ?: PrinterManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var outputStream: OutputStream? = null
    private var connectionType: String = "none"
    private var printerName: String = "Unknown"
    private var isConnected: Boolean = false

    private var bluetoothSocket: BluetoothSocket? = null
    private var networkSocket: Socket? = null
    private var usbDevice: UsbDevice? = null

    override suspend fun connect(): Boolean {
        return try {
            when (connectionType) {
                "bluetooth" -> connectBluetooth()
                "usb" -> connectUSB()
                "network" -> connectNetwork()
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            false
        }
    }

    private suspend fun connectBluetooth(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth not available or enabled")
                return false
            }

            // Get paired devices
            val pairedDevices = adapter.bondedDevices

            // Try to find the last connected device or first printer
            val device = pairedDevices.find {
                it.name.contains("Printer", ignoreCase = true) ||
                it.name.contains("Thermal", ignoreCase = true) ||
                it.name.contains("TP-", ignoreCase = true)
            } ?: pairedDevices.firstOrNull()

            if (device != null) {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(BLUETOOTH_SPP_UUID))
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                printerName = device.name
                isConnected = true
                Log.i(TAG, "Connected to Bluetooth printer: ${device.name}")
                true
            } else {
                Log.w(TAG, "No paired Bluetooth printer found")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth connection error", e)
            false
        }
    }

    private suspend fun connectUSB(): Boolean {
        // USB printer implementation placeholder
        // In a real implementation, you would need to handle USB permissions
        // and find the appropriate USB vendor/product IDs for thermal printers
        isConnected = false
        printerName = "USB Printer"
        return false
    }

    private suspend fun connectNetwork(): Boolean {
        // Network printer implementation placeholder
        // For now, returns false as implementation needs IP address configuration
        isConnected = false
        printerName = "Network Printer"
        return false
    }

    override suspend fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            networkSocket?.close()
            isConnected = false
            Log.i(TAG, "Disconnected from printer")
        } catch (e: IOException) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }

    override suspend fun printReceipt(orderData: OrderData, printerWidth: String): Boolean {
        return try {
            if (!isConnected) {
                connect()
            }

            if (isConnected) {
                val receiptData = EscPosCommands.createReceiptBytes(orderData, printerWidth)
                outputStream?.write(receiptData)
                outputStream?.flush()
                Log.i(TAG, "Receipt printed successfully for order: ${orderData.orderId}")
                true
            } else {
                Log.w(TAG, "Printer not connected")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing receipt", e)
            false
        }
    }

    override fun isConnected(): Boolean {
        return isConnected
    }

    override fun getStatus(): PrinterStatus {
        return PrinterStatus(
            connected = isConnected,
            type = connectionType,
            name = printerName,
            paperStatus = if (isConnected) "ok" else "unknown",
            inkStatus = if (isConnected) "ok" else "unknown"
        )
    }

    override fun getPrinterName(): String {
        return printerName
    }

    fun setConnectionType(type: String) {
        connectionType = type
    }

    fun setBluetoothAddress(address: String) {
        // For future use when allowing specific device selection
    }

    fun setNetworkPrinter(ip: String, port: Int) {
        // For future use when allowing network printer configuration
    }

    // Test print function
    suspend fun testPrint(): Boolean {
        return try {
            if (!isConnected) {
                connect()
            }

            if (isConnected) {
                val testData = "TEST PRINT\n".toByteArray() +
                               "This is a test\n".toByteArray() +
                               "Printer: $printerName\n".toByteArray() +
                               "\n\n\n".toByteArray()

                outputStream?.write(testData)
                outputStream?.flush()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test print failed", e)
            false
        }
    }
}