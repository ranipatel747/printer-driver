package pos.helper.thermalprinter.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pos.helper.thermalprinter.data.websocket.WebSocketMessage
import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.PrinterStatus
import pos.helper.thermalprinter.data.websocket.OrderItem
import pos.helper.thermalprinter.printer.VirtualBluetoothPrinter
import pos.helper.thermalprinter.printer.VirtualPrinterManager
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.Date
import java.util.UUID

class PrinterManager private constructor(private val context: Context) : PrinterInterface {

    companion object {
        private const val TAG = "PrinterManager"
        private const val BLUETOOTH_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

        @SuppressLint("StaticFieldLeak")
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
    private var currentDeviceAddress: String? = null

    private var bluetoothSocket: BluetoothSocket? = null
    private var networkSocket: Socket? = null
    private var usbDevice: UsbDevice? = null
    private var virtualPrinter: VirtualBluetoothPrinter? = null

    private val virtualPrinterManager: VirtualPrinterManager by lazy {
        VirtualPrinterManager.getInstance(context)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect(): Boolean {
        return try {
            // First check if we should connect to a virtual printer
            if (connectionType == "bluetooth" && currentDeviceAddress != null) {
                virtualPrinter = virtualPrinterManager.getVirtualPrinter(currentDeviceAddress!!)
                if (virtualPrinter != null) {
                    Log.i(TAG, "Connecting to virtual printer: ${virtualPrinter!!.getPrinterName()}")
                    val result = virtualPrinter!!.connect()
                    if (result) {
                        isConnected = true
                        printerName = virtualPrinter!!.getPrinterName()
                        return result
                    } else {
                        Log.w(TAG, "Failed to connect to virtual printer, falling back to real Bluetooth")
                    }
                }
            }

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun connectBluetooth(): Boolean {
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

        if (device == null) {
            Log.w(TAG, "No paired Bluetooth printer found")
            return false
        }

        return connectToDeviceWithRetry(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun connectToDeviceWithRetry(device: BluetoothDevice, maxRetries: Int = 3): Boolean {
        for (attempt in 1..maxRetries) {
            try {
                Log.i(TAG, "Connection attempt $attempt/$maxRetries to ${device.name} (${device.address})")

                // Cancel discovery before connecting
                val adapter = BluetoothAdapter.getDefaultAdapter()
                adapter?.cancelDiscovery()

                // Clean up existing socket
                bluetoothSocket?.close()

                // Try different socket creation methods
                var socket: BluetoothSocket? = null

                try {
                    // Method 1: Create RFCOMM socket
                    socket = device.createRfcommSocketToServiceRecord(UUID.fromString(BLUETOOTH_SPP_UUID))
                } catch (e: Exception) {
                    Log.w(TAG, "Standard socket creation failed, trying fallback", e)
                    try {
                        // Method 2: Reflection fallback for some devices
                        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = method.invoke(device, 1) as BluetoothSocket
                    } catch (reflectException: Exception) {
                        Log.e(TAG, "Fallback socket creation also failed", reflectException)
                    }
                }

                if (socket == null) {
                    Log.e(TAG, "Could not create Bluetooth socket")
                    continue
                }

                bluetoothSocket = socket

                // Connect with timeout
                socket.connect()

                // Verify connection
                if (socket.isConnected) {
                    outputStream = socket.outputStream
                    printerName = device.name
                    isConnected = true
                    Log.i(TAG, "Successfully connected to Bluetooth printer: ${device.name}")
                    return true
                } else {
                    Log.w(TAG, "Socket connected but isConnected() returned false")
                }

            } catch (e: IOException) {
                Log.e(TAG, "Connection attempt $attempt failed: ${e.message}", e)

                // Clean up failed socket
                try {
                    bluetoothSocket?.close()
                } catch (closeException: Exception) {
                    Log.w(TAG, "Error closing failed socket", closeException)
                }
                bluetoothSocket = null

                // Wait before retry
                if (attempt < maxRetries) {
                    Log.i(TAG, "Retrying in 2 seconds...")
                    delay(2000)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception - check BLUETOOTH_CONNECT permission", e)
                break
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during connection attempt $attempt", e)
                // Wait before retry
                if (attempt < maxRetries) {
                    delay(2000)
                }
            }
        }

        Log.e(TAG, "Failed to connect to ${device.name} after $maxRetries attempts")
        isConnected = false
        return false
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
            Log.i(TAG, "Disconnecting from printer...")

            // Disconnect virtual printer if connected
            virtualPrinter?.let {
                try {
                    it.disconnect()
                    Log.i(TAG, "Virtual printer disconnected")
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting virtual printer", e)
                }
            }
            virtualPrinter = null

            // Close output stream
            outputStream?.let {
                try {
                    it.flush() // Flush any pending data
                    it.close()
                    Log.i(TAG, "Output stream closed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing output stream", e)
                }
            }
            outputStream = null

            // Close Bluetooth socket
            bluetoothSocket?.let {
                try {
                    if (it.isConnected) {
                        // Bluetooth sockets don't have shutdownInput/shutdownOutput, just close
                    }
                    it.close()
                    Log.i(TAG, "Bluetooth socket closed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing Bluetooth socket", e)
                }
            }
            bluetoothSocket = null

            // Close network socket
            networkSocket?.let {
                try {
                    it.shutdownInput()
                    it.shutdownOutput()
                    it.close()
                    Log.i(TAG, "Network socket closed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing network socket", e)
                }
            }
            networkSocket = null

            isConnected = false
            Log.i(TAG, "Successfully disconnected from printer")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during disconnect", e)
            isConnected = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun printReceipt(orderData: OrderData, printerWidth: String): Boolean {
        Log.i(TAG, "Starting print job for order: ${orderData.orderId}")

        // Check if connected, try to connect if not
        if (!isConnected) {
            Log.i(TAG, "Printer not connected, attempting to connect...")
            if (!connect()) {
                Log.e(TAG, "Failed to connect to printer")
                return false
            }
        }

        // Verify connection again
        if (!isConnected) {
            Log.e(TAG, "Printer still not connected after connection attempt")
            return false
        }

        return try {
            // Use virtual printer if connected
            virtualPrinter?.let {
                Log.i(TAG, "Printing to virtual printer: ${it.getPrinterName()}")
                val result = it.printReceipt(orderData, printerWidth)
                Log.i(TAG, "Virtual print result: $result")
                return result
            }

            // Use real printer if no virtual printer
            validatePrinterConnection()
            val receiptData = EscPosCommands.createReceiptBytes(orderData, printerWidth)

            outputStream?.let { stream ->
                try {
                    stream.write(receiptData)
                    stream.flush()
                    Log.i(TAG, "Receipt printed successfully for order: ${orderData.orderId}")
                    true
                } catch (e: java.io.IOException) {
                    Log.e(TAG, "Failed to write to printer output stream", e)
                    // Try to reconnect and retry once
                    if (reconnectAndRetryPrint(orderData, printerWidth, receiptData)) {
                        true
                    } else {
                        false
                    }
                }
            } ?: run {
                Log.e(TAG, "Output stream is null, cannot print")
                false
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during printing - check permissions", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error printing receipt", e)
            false
        }
    }

    private fun validatePrinterConnection(): Boolean {
        if (connectionType == "bluetooth") {
            val bluetoothConnected = bluetoothSocket?.isConnected == true
            if (!bluetoothConnected) {
                Log.w(TAG, "Bluetooth connection validation failed")
                isConnected = false
                return false
            }
        }
        return true
    }

    private suspend fun reconnectAndRetryPrint(orderData: OrderData, printerWidth: String, receiptData: ByteArray): Boolean {
        Log.i(TAG, "Attempting to reconnect and retry print...")

        // Disconnect first to clean up
        disconnect()
        kotlinx.coroutines.delay(1000) // Wait before reconnecting

        // Try to reconnect
        if (!connect()) {
            Log.e(TAG, "Reconnection failed")
            return false
        }

        // Try printing again
        return try {
            outputStream?.write(receiptData)
            outputStream?.flush()
            Log.i(TAG, "Receipt printed successfully after reconnect for order: ${orderData.orderId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Still failed to print after reconnect", e)
            false
        }
    }

    override fun isConnected(): Boolean {
        return isConnected
    }

    override fun getStatus(): PrinterStatus {
        return virtualPrinter?.getStatus() ?: PrinterStatus(
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
        currentDeviceAddress = address
        Log.i(TAG, "Set Bluetooth address: $address")
    }

    fun isVirtualPrinterConnected(): Boolean {
        return virtualPrinter != null && virtualPrinter?.isConnected() == true
    }

    fun getVirtualPrintHistory(): List<VirtualBluetoothPrinter.PrintJob> {
        return virtualPrinter?.getPrintHistory() ?: emptyList()
    }

    fun clearVirtualPrintHistory() {
        virtualPrinter?.clearPrintHistory()
    }

    fun setNetworkPrinter(ip: String, port: Int) {
        // For future use when allowing network printer configuration
    }

    // Test print function
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun testPrint(): Boolean {
        return try {
            if (!isConnected) {
                connect()
            }

            if (isConnected) {
                // Use virtual printer if connected
                virtualPrinter?.let {
                    val testOrderData = OrderData(
                        orderId = "TEST_${System.currentTimeMillis()}",
                        customer = "Test Customer",
                        items = listOf(
                            OrderItem(
                                name = "Test Item",
                                qty = 1,
                                price = 1.99
                            )
                        ),
                        subtotal = 1.99,
                        tax = 0.16,
                        total = 2.15,
                        paymentMethod = "Cash",
                        timestamp = Date().toString()
                    )
                    Log.i(TAG, "Test print to virtual printer: ${it.getPrinterName()}")
                    return it.printReceipt(testOrderData, "58mm")
                }

                // Use real printer if no virtual printer
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