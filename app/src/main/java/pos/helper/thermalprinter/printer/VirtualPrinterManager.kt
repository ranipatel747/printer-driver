package pos.helper.thermalprinter.printer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pos.helper.thermalprinter.data.printer.PrinterInfo
import pos.helper.thermalprinter.data.printer.PrinterType
import pos.helper.thermalprinter.data.websocket.PrinterStatus
import java.util.*

class VirtualPrinterManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VirtualPrinterManager"

        @Volatile
        private var instance: VirtualPrinterManager? = null

        fun getInstance(context: Context): VirtualPrinterManager {
            return instance ?: synchronized(this) {
                instance ?: VirtualPrinterManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var virtualPrinters = mutableMapOf<String, VirtualBluetoothPrinter>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _virtualPrintersList = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val virtualPrintersList: StateFlow<List<PrinterInfo>> = _virtualPrintersList

    init {
        createDefaultVirtualPrinters()
    }

    private fun createDefaultVirtualPrinters() {
        val defaultPrinters = listOf(
            VirtualBluetoothPrinter(
                context = context,
                deviceName = "Virtual TP-Printer",
                deviceAddress = "00:11:22:33:44:55"
            ),
            VirtualBluetoothPrinter(
                context = context,
                deviceName = "Virtual Thermal Printer Pro",
                deviceAddress = "00:11:22:33:44:66"
            ),
            VirtualBluetoothPrinter(
                context = context,
                deviceName = "Virtual POS Printer",
                deviceAddress = "00:11:22:33:44:77"
            )
        )

        defaultPrinters.forEach { printer ->
            virtualPrinters[printer.getDeviceAddress()] = printer
        }

        updateVirtualPrintersList()
        Log.i(TAG, "Created ${defaultPrinters.size} default virtual printers")
    }

    fun createVirtualPrinter(name: String, address: String? = null): String {
        val deviceAddress = address ?: generateRandomAddress()
        val printer = VirtualBluetoothPrinter(context, name, deviceAddress)
        virtualPrinters[deviceAddress] = printer
        updateVirtualPrintersList()

        Log.i(TAG, "Created virtual printer: $name ($deviceAddress)")
        return deviceAddress
    }

    fun getVirtualPrinter(address: String): VirtualBluetoothPrinter? {
        return virtualPrinters[address]
    }

    fun getAllVirtualPrinters(): List<VirtualBluetoothPrinter> {
        return virtualPrinters.values.toList()
    }

    fun removeVirtualPrinter(address: String) {
        virtualPrinters.remove(address)?.let { printer ->
            scope.launch {
                if (printer.isConnected()) {
                    printer.disconnect()
                }
            }
            updateVirtualPrintersList()
            Log.i(TAG, "Removed virtual printer: $address")
        }
    }

    fun getPrinterInfo(address: String): PrinterInfo? {
        val printer = virtualPrinters[address] ?: return null
        return PrinterInfo(
            name = printer.getPrinterName(),
            address = printer.getDeviceAddress(),
            type = PrinterType.BLUETOOTH,
            isPaired = true,  // Virtual printers are always "paired"
            isConnected = printer.isConnected(),
            isDefault = false,
            isLikelyPrinter = true
        )
    }

    suspend fun connectToPrinter(address: String): Boolean {
        val printer = virtualPrinters[address] ?: return false
        val success = printer.connect()
        updateVirtualPrintersList()
        return success
    }

    suspend fun disconnectFromPrinter(address: String) {
        virtualPrinters[address]?.disconnect()
        updateVirtualPrintersList()
    }

    suspend fun simulatePrinterError(address: String): Boolean {
        return virtualPrinters[address]?.simulatePrinterError() ?: false
    }

    suspend fun simulatePrinterRecovery(address: String): Boolean {
        val success = virtualPrinters[address]?.simulatePrinterRecovery() ?: false
        updateVirtualPrintersList()
        return success
    }

    fun getPrinterStatus(address: String): PrinterStatus? {
        return virtualPrinters[address]?.getStatus()
    }

    fun getAllPrinterStatuses(): Map<String, PrinterStatus> {
        return virtualPrinters.mapValues { it.value.getStatus() }
    }

    fun printHistory(address: String): List<VirtualBluetoothPrinter.PrintJob> {
        return virtualPrinters[address]?.getPrintHistory() ?: emptyList()
    }

    fun clearPrintHistory(address: String) {
        virtualPrinters[address]?.clearPrintHistory()
    }

    fun clearAllPrintHistory() {
        virtualPrinters.values.forEach { it.clearPrintHistory() }
    }

    fun isVirtualPrinter(address: String): Boolean {
        return virtualPrinters.containsKey(address)
    }

    fun makeDiscoverable(count: Int = 3) {
        // Create some additional virtual printers to simulate discovery
        repeat(count) { index ->
            val virtualPrinterName = when (index) {
                0 -> "Virtual EPSON TM-T88"
                1 -> "Virtual Star TSP650"
                2 -> "Virtual Citizen CT-S310"
                else -> "Virtual Printer ${index + 1}"
            }
            createVirtualPrinter(virtualPrinterName)
        }

        Log.i(TAG, "Made $count additional virtual printers discoverable")
    }

    private fun updateVirtualPrintersList() {
        val printersList = virtualPrinters.map { (address, printer) ->
            PrinterInfo(
                name = printer.getPrinterName(),
                address = address,
                type = PrinterType.BLUETOOTH,
                isPaired = true,
                isConnected = printer.isConnected(),
                isDefault = false,
                isLikelyPrinter = true
            )
        }

        scope.launch {
            _virtualPrintersList.value = printersList
        }
    }

    private fun generateRandomAddress(): String {
        val random = Random()
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256)
        )
    }

    fun getVirtualPrinterCount(): Int {
        return virtualPrinters.size
    }

    fun getConnectedVirtualPrinters(): List<VirtualBluetoothPrinter> {
        return virtualPrinters.values.filter { it.isConnected() }
    }

    fun cleanup() {
        scope.launch {
            virtualPrinters.values.forEach { printer ->
                if (printer.isConnected()) {
                    printer.disconnect()
                }
            }
            virtualPrinters.clear()
            updateVirtualPrintersList()
            Log.i(TAG, "Cleanup completed - all virtual printers disconnected")
        }
    }
}