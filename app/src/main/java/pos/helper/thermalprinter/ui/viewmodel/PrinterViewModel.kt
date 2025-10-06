package pos.helper.thermalprinter.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pos.helper.thermalprinter.bluetooth.BluetoothScanner
import pos.helper.thermalprinter.data.preferences.PrinterPreferences
import pos.helper.thermalprinter.data.printer.PrinterInfo
import pos.helper.thermalprinter.data.printer.SavedPrinter
import pos.helper.thermalprinter.printer.PrinterManager

class PrinterViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "PrinterViewModel"
    }

    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext

    private val bluetoothScanner = BluetoothScanner(context)
    private val printerPreferences = PrinterPreferences(context)
    private val printerManager = PrinterManager.getInstance(context)

    private val _discoveredPrinters = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val discoveredPrinters: StateFlow<List<PrinterInfo>> = _discoveredPrinters.asStateFlow()

    private val _savedPrinters = MutableStateFlow<List<SavedPrinter>>(emptyList())
    val savedPrinters: StateFlow<List<SavedPrinter>> = _savedPrinters.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _defaultPrinter = MutableStateFlow<SavedPrinter?>(null)
    val defaultPrinter: StateFlow<SavedPrinter?> = _defaultPrinter.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Not connected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // New categorized printer lists
    private val _virtualPrinters = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val virtualPrinters: StateFlow<List<PrinterInfo>> = _virtualPrinters.asStateFlow()

    private val _pairedPrinters = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val pairedPrinters: StateFlow<List<PrinterInfo>> = _pairedPrinters.asStateFlow()

    private val _nearbyPrinters = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val nearbyPrinters: StateFlow<List<PrinterInfo>> = _nearbyPrinters.asStateFlow()

    init {
        loadSavedPrinters()
        loadDefaultPrinter()
    }

    fun startScan() {
        viewModelScope.launch {
            if (!bluetoothScanner.isBluetoothEnabled()) {
                _connectionStatus.value = "Bluetooth disabled"
                return@launch
            }

            _isScanning.value = true
            bluetoothScanner.startScan()

            // Collect discovered printers in a separate coroutine
            launch {
                bluetoothScanner.discoveredPrinters.collect { printers ->
                    _discoveredPrinters.value = printers
                    categorizePrinters(printers)
                }
            }

            // Update scanning state when scan completes in a separate coroutine
            launch {
                bluetoothScanner.scanState.collect { isScanning ->
                    _isScanning.value = isScanning
                }
            }
        }
    }

    private fun categorizePrinters(printers: List<PrinterInfo>) {
        val virtual = printers.filter { bluetoothScanner.isVirtualPrinter(it.address) }
        val paired = printers.filter { it.isPaired && !bluetoothScanner.isVirtualPrinter(it.address) }
        val nearby = printers.filter { !it.isPaired && !bluetoothScanner.isVirtualPrinter(it.address) }

        _virtualPrinters.value = virtual
        _pairedPrinters.value = paired
        _nearbyPrinters.value = nearby

        Log.d(TAG, "Categorized printers: ${virtual.size} virtual, ${paired.size} paired, ${nearby.size} nearby")
    }

    fun stopScan() {
        bluetoothScanner.stopScan()
        _isScanning.value = false
        // Clear nearby devices when scanning stops, but keep virtual and paired
        _nearbyPrinters.value = emptyList()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothScanner.isBluetoothEnabled()
    }

    fun isVirtualPrinter(address: String): Boolean {
        return bluetoothScanner.isVirtualPrinter(address)
    }

    fun pairPrinter(address: String) {
        viewModelScope.launch {
            _connectionStatus.value = "Pairing..."
            val success = bluetoothScanner.pairDevice(address)
            if (success) {
                _connectionStatus.value = "Paired successfully"
                // Refresh the list
                startScan()
            } else {
                _connectionStatus.value = "Pairing failed"
            }
        }
    }

    fun savePrinter(printerInfo: PrinterInfo, makeDefault: Boolean = false) {
        val savedPrinter = SavedPrinter(
            name = printerInfo.name,
            address = printerInfo.address,
            type = printerInfo.type,
            isDefault = makeDefault || _defaultPrinter.value == null
        )

        printerPreferences.savePrinter(savedPrinter)
        loadSavedPrinters()

        if (makeDefault) {
            _defaultPrinter.value = savedPrinter
        }

        _connectionStatus.value = "Printer saved"
    }

    fun removePrinter(address: String) {
        printerPreferences.removePrinter(address)
        loadSavedPrinters()

        if (_defaultPrinter.value?.address == address) {
            _defaultPrinter.value = printerPreferences.getDefaultPrinter()
        }

        _connectionStatus.value = "Printer removed"
    }

    fun setDefaultPrinter(address: String) {
        printerPreferences.setDefaultPrinter(address)
        _defaultPrinter.value = printerPreferences.getDefaultPrinter()
        _connectionStatus.value = "Default printer updated"
    }

    fun connectToPrinter(address: String) {
        viewModelScope.launch {
            val savedPrinter = _savedPrinters.value.find { it.address == address }
            if (savedPrinter != null) {
                _connectionStatus.value = "Connecting..."

                try {
                    // First connect via PrinterManager
                    val success = when (savedPrinter.type) {
                        pos.helper.thermalprinter.data.printer.PrinterType.BLUETOOTH -> {
                            printerManager.setConnectionType("bluetooth")
                            printerManager.setBluetoothAddress(address) // Set the address!
                            printerManager.connect()
                        }
                        else -> false
                    }

                    if (success) {
                        _connectionStatus.value = "Connected to ${savedPrinter.name}"
                    } else {
                        _connectionStatus.value = "Connection failed"
                    }
                } catch (e: Exception) {
                    Log.e("PrinterViewModel", "Connection error", e)
                    _connectionStatus.value = "Connection error: ${e.message}"
                }
            } else {
                _connectionStatus.value = "Printer not found in saved list"
            }
        }
    }

    fun connectToDiscoveredPrinter(address: String) {
        viewModelScope.launch {
            val discoveredPrinter = _discoveredPrinters.value.find { it.address == address }
            if (discoveredPrinter != null) {
                _connectionStatus.value = "Connecting..."

                try {
                    // Check if it's a virtual printer
                    if (bluetoothScanner.isVirtualPrinter(address)) {
                        // Connect to virtual printer
                        val success = bluetoothScanner.connectVirtualPrinter(address)
                        if (success) {
                            printerManager.setConnectionType("bluetooth")
                            printerManager.setBluetoothAddress(address)
                            _connectionStatus.value = "Connected to virtual printer: ${discoveredPrinter.name}"
                        } else {
                            _connectionStatus.value = "Failed to connect to virtual printer"
                        }
                    } else {
                        // Connect to regular Bluetooth printer
                        printerManager.setConnectionType("bluetooth")
                        printerManager.setBluetoothAddress(address)
                        val success = printerManager.connect()
                        if (success) {
                            _connectionStatus.value = "Connected to ${discoveredPrinter.name}"
                        } else {
                            _connectionStatus.value = "Connection failed"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PrinterViewModel", "Connection error", e)
                    _connectionStatus.value = "Connection error: ${e.message}"
                }
            } else {
                _connectionStatus.value = "Printer not found in discovered list"
            }
        }
    }

    fun disconnectPrinter() {
        viewModelScope.launch {
            printerManager.disconnect()
            _connectionStatus.value = "Disconnected"
        }
    }

    private fun loadSavedPrinters() {
        _savedPrinters.value = printerPreferences.getSavedPrinters()
    }

    private fun loadDefaultPrinter() {
        _defaultPrinter.value = printerPreferences.getDefaultPrinter()
    }

    fun testPrint() {
        viewModelScope.launch {
            try {
                if (!printerManager.isConnected()) {
                    _connectionStatus.value = "No printer connected - please connect first"
                    return@launch
                }

                val success = printerManager.testPrint()
                _connectionStatus.value = if (success) "Test printed successfully" else "Test print failed"
            } catch (e: Exception) {
                Log.e("PrinterViewModel", "Test print error", e)
                _connectionStatus.value = "Test print error: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothScanner.stopScan()
    }
}