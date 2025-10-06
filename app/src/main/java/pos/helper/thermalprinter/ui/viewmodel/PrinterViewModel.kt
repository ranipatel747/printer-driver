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

            bluetoothScanner.startScan()

            // Collect discovered printers
            bluetoothScanner.discoveredPrinters.collect { printers ->
                _discoveredPrinters.value = printers
            }
        }
    }

    fun stopScan() {
        bluetoothScanner.stopScan()
        _isScanning.value = false
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothScanner.isBluetoothEnabled()
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

                // First connect via PrinterManager
                val success = when (savedPrinter.type) {
                    pos.helper.thermalprinter.data.printer.PrinterType.BLUETOOTH -> {
                        printerManager.setConnectionType("bluetooth")
                        printerManager.connect()
                    }
                    else -> false
                }

                if (success) {
                    _connectionStatus.value = "Connected to ${savedPrinter.name}"
                } else {
                    _connectionStatus.value = "Connection failed"
                }
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
            val success = printerManager.testPrint()
            _connectionStatus.value = if (success) "Test printed" else "Test print failed"
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothScanner.stopScan()
    }
}