package pos.helper.thermalprinter.printer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pos.helper.thermalprinter.bluetooth.BluetoothScanner
import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.OrderItem

/**
 * Helper class to demonstrate and test the virtual printer functionality.
 * This class provides simple methods to create, connect to, and test virtual printers.
 */
class VirtualPrinterDemo(private val context: Context) {

    companion object {
        private const val TAG = "VirtualPrinterDemo"
    }

    private val virtualPrinterManager = VirtualPrinterManager.getInstance(context)
    private val bluetoothScanner = BluetoothScanner(context)
    private val printerManager = PrinterManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Creates a demo virtual printer and makes it available for scanning
     */
    fun createDemoPrinter(name: String = "Demo Thermal Printer"): String {
        Log.i(TAG, "Creating demo printer: $name")
        val address = virtualPrinterManager.createVirtualPrinter(name)
        Log.i(TAG, "Demo printer created with address: $address")
        return address
    }

    /**
     * Creates multiple demo printers for testing
     */
    fun createMultipleDemoPrinters(): List<String> {
        Log.i(TAG, "Creating multiple demo printers")
        val printers = listOf(
            createDemoPrinter("Demo TP-Printer"),
            createDemoPrinter("Demo POS Printer"),
            createDemoPrinter("Demo Receipt Printer")
        )
        Log.i(TAG, "Created ${printers.size} demo printers")
        return printers
    }

    /**
     * Connects to a virtual printer by address
     */
    suspend fun connectToVirtualPrinter(address: String): Boolean {
        Log.i(TAG, "Connecting to virtual printer: $address")
        val success = virtualPrinterManager.connectToPrinter(address)
        if (success) {
            // Update printer manager to use this virtual printer
            printerManager.setConnectionType("bluetooth")
            printerManager.setBluetoothAddress(address)
            Log.i(TAG, "Successfully connected to virtual printer")
        } else {
            Log.e(TAG, "Failed to connect to virtual printer")
        }
        return success
    }

    /**
     * Performs a test print on the connected virtual printer
     */
    suspend fun performTestPrint(): Boolean {
        return try {
            Log.i(TAG, "Performing test print")
            val success = printerManager.testPrint()
            if (success) {
                Log.i(TAG, "Test print completed successfully")
            } else {
                Log.e(TAG, "Test print failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error during test print", e)
            false
        }
    }

    /**
     * Performs a receipt print with sample order data
     */
    suspend fun printSampleReceipt(orderId: String? = null): Boolean {
        return try {
            val items = listOf(
                OrderItem(name = "Coffee", qty = 2, price = 3.50),
                OrderItem(name = "Sandwich", qty = 1, price = 8.99),
                OrderItem(name = "Chips", qty = 1, price = 2.50),
                OrderItem(name = "Soda", qty = 3, price = 1.99)
            )

            val subtotal = items.sumOf { it.qty * it.price }
            val tax = subtotal * 0.08 // 8% tax
            val total = subtotal + tax

            val sampleOrder = OrderData(
                orderId = orderId ?: "SAMPLE_${System.currentTimeMillis()}",
                customer = "Demo Customer",
                items = items,
                subtotal = subtotal,
                tax = tax,
                total = total,
                paymentMethod = "Credit Card",
                timestamp = java.util.Date().toString()
            )

            Log.i(TAG, "Printing sample receipt for order: ${sampleOrder.orderId}")
            val success = printerManager.printReceipt(sampleOrder, "58mm")

            if (success) {
                Log.i(TAG, "Sample receipt printed successfully")
            } else {
                Log.e(TAG, "Sample receipt print failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error printing sample receipt", e)
            false
        }
    }

    /**
     * Starts a Bluetooth scan which will include virtual printers
     */
    fun startScan() {
        Log.i(TAG, "Starting Bluetooth scan (will include virtual printers)")
        bluetoothScanner.startScan()
    }

    /**
     * Gets the current list of discovered printers
     */
    fun getDiscoveredPrinters() = bluetoothScanner.discoveredPrinters

    /**
     * Gets the scan state
     */
    fun getScanState() = bluetoothScanner.scanState

    /**
     * Stops the Bluetooth scan
     */
    fun stopScan() {
        Log.i(TAG, "Stopping Bluetooth scan")
        bluetoothScanner.stopScan()
    }

    /**
     * Gets the current printer status
     */
    fun getPrinterStatus() = printerManager.getStatus()

    /**
     * Checks if any virtual printer is connected
     */
    fun isVirtualPrinterConnected(): Boolean {
        return printerManager.isVirtualPrinterConnected()
    }

    /**
     * Gets print history from the connected virtual printer
     */
    fun getPrintHistory() = printerManager.getVirtualPrintHistory()

    /**
     * Clears print history from the connected virtual printer
     */
    fun clearPrintHistory() {
        printerManager.clearVirtualPrintHistory()
        Log.i(TAG, "Print history cleared")
    }

    /**
     * Simulates a printer error for testing error handling
     */
    suspend fun simulatePrinterError(): Boolean {
        return if (printerManager.isVirtualPrinterConnected()) {
            Log.i(TAG, "Simulating printer error")
            // This would need to be extended to work with specific virtual printer
            true
        } else {
            Log.w(TAG, "No virtual printer connected to simulate error")
            false
        }
    }

    /**
     * Demonstrates a complete workflow
     */
    fun runCompleteDemo() {
        scope.launch {
            try {
                Log.i(TAG, "Starting complete virtual printer demo")

                // Step 1: Create demo printers
                createMultipleDemoPrinters()

                // Step 2: Start scan
                startScan()

                // Wait a moment for scan to complete
                kotlinx.coroutines.delay(2000)

                // Step 3: Get available printers
                val printers = getDiscoveredPrinters().value
                Log.i(TAG, "Found ${printers.size} printers")

                val virtualPrinter = printers.find {
                    virtualPrinterManager.isVirtualPrinter(it.address)
                }

                if (virtualPrinter != null) {
                    Log.i(TAG, "Connecting to virtual printer: ${virtualPrinter.name}")

                    // Step 4: Connect to virtual printer
                    if (connectToVirtualPrinter(virtualPrinter.address)) {
                        Log.i(TAG, "Successfully connected to virtual printer")

                        // Step 5: Perform test print
                        if (performTestPrint()) {
                            Log.i(TAG, "Test print successful")
                        }

                        // Step 6: Print sample receipt
                        if (printSampleReceipt("DEMO_ORDER_001")) {
                            Log.i(TAG, "Sample receipt printed successfully")
                        }

                        // Step 7: Show results
                        val status = getPrinterStatus()
                        Log.i(TAG, "Printer status: $status")

                        val history = getPrintHistory()
                        Log.i(TAG, "Print history contains ${history.size} items")

                    } else {
                        Log.e(TAG, "Failed to connect to virtual printer")
                    }
                } else {
                    Log.w(TAG, "No virtual printer found in scan results")
                }

                // Step 8: Stop scan
                stopScan()

                Log.i(TAG, "Complete virtual printer demo finished")

            } catch (e: Exception) {
                Log.e(TAG, "Error during demo", e)
            }
        }
    }

    /**
     * Cleanup method to disconnect and clean up resources
     */
    fun cleanup() {
        scope.launch {
            try {
                printerManager.disconnect()
                virtualPrinterManager.cleanup()
                Log.i(TAG, "Demo cleanup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }

    /**
     * Gets demo information and usage tips
     */
    fun getDemoInfo(): String {
        return buildString {
            appendLine("Virtual Printer Demo Information")
            appendLine("=============================")
            appendLine()
            appendLine("Available Methods:")
            appendLine("• createDemoPrinter() - Create a single demo printer")
            appendLine("• createMultipleDemoPrinters() - Create 3 demo printers")
            appendLine("• connectToVirtualPrinter(address) - Connect to a virtual printer")
            appendLine("• performTestPrint() - Print a test receipt")
            appendLine("• printSampleReceipt() - Print a sample order receipt")
            appendLine("• startScan() - Start Bluetooth scan (includes virtual printers)")
            appendLine("• getDiscoveredPrinters() - Get list of found printers")
            appendLine("• getPrinterStatus() - Get current printer status")
            appendLine("• getPrintHistory() - Get print job history")
            appendLine("• runCompleteDemo() - Run full automated demo")
            appendLine("• cleanup() - Disconnect and clean up")
            appendLine()
            appendLine("Features:")
            appendLine("• Virtual printers appear in Bluetooth scans")
            appendLine("• Print receipts are saved to device gallery")
            appendLine("• Supports connection/disconnection simulation")
            appendLine("• Maintains print history for review")
            appendLine("• Works with existing WebSocket printer service")
            appendLine()
            appendLine("Printed receipts are saved to: Pictures/ThermalPrinter/")
        }
    }
}