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
import pos.helper.thermalprinter.data.websocket.OrderItem
import pos.helper.thermalprinter.printer.VirtualBluetoothPrinter
import pos.helper.thermalprinter.printer.VirtualPrinterManager
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
    private var currentDeviceAddress: String? = null

    private var bluetoothSocket: BluetoothSocket? = null
    private var networkSocket: Socket? = null
    private var usbDevice: UsbDevice? = null
    private var virtualPrinter: VirtualBluetoothPrinter? = null

    private val virtualPrinterManager: VirtualPrinterManager by lazy {
        VirtualPrinterManager.getInstance(context)
    }

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
            // Disconnect virtual printer if connected
            virtualPrinter?.disconnect()
            virtualPrinter = null

            // Disconnect real connections
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
                // Use virtual printer if connected
                virtualPrinter?.let {
                    Log.i(TAG, "Printing to virtual printer: ${it.getPrinterName()}")
                    val result = it.printReceipt(orderData, printerWidth)
                    Log.i(TAG, "Virtual print result: $result")
                    return result
                }

                // Use real printer if no virtual printer
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
                        timestamp = java.util.Date().toString()
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