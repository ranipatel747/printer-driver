package pos.helper.thermalprinter.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pos.helper.thermalprinter.data.printer.PrinterInfo
import pos.helper.thermalprinter.data.printer.PrinterType
import java.util.*

class BluetoothScanner(private val context: Context) {

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null

    private val _discoveredPrinters = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val discoveredPrinters: StateFlow<List<PrinterInfo>> = _discoveredPrinters

    private val _scanState = MutableStateFlow(false)
    val scanState: StateFlow<Boolean> = _scanState

    private val foundDevices = mutableSetOf<String>()

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled")
            return
        }

        _scanState.value = true
        _discoveredPrinters.value = emptyList() // Clear previous results
        foundDevices.clear()

        // First, get paired devices
        getPairedDevices()

        // Then start discovery for unpaired devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLeScan()
        } else {
            startLegacyScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDevices() {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return
        Log.d(TAG, "Found ${pairedDevices.size} paired devices")

        val devices = pairedDevices.map { device ->
                PrinterInfo(
                    name = device.name ?: "Unknown",
                    address = device.address,
                    type = PrinterType.BLUETOOTH,
                    isPaired = true,
                    isLikelyPrinter = isThermalPrinter(device)
                )
            }

        val currentList = _discoveredPrinters.value.toMutableList()
        devices.forEach { device ->
            if (!foundDevices.contains(device.address)) {
                foundDevices.add(device.address)
                currentList.add(device)
            }
        }

        _discoveredPrinters.value = currentList
    }

    @SuppressLint("MissingPermission")
    private fun startLegacyScan() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            if (!foundDevices.contains(it.address)) {
                                Log.d(TAG, "Found device: ${it.name} (${it.address})")
                                foundDevices.add(it.address)
                                val printer = PrinterInfo(
                                    name = it.name ?: "Unknown",
                                    address = it.address,
                                    type = PrinterType.BLUETOOTH,
                                    isPaired = false,
                                    isLikelyPrinter = isThermalPrinter(it)
                                )

                                val currentList = _discoveredPrinters.value.toMutableList()
                                currentList.add(printer)
                                _discoveredPrinters.value = currentList
                            }
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)

        bluetoothAdapter?.startDiscovery()

        // Stop scan after 15 seconds
        handler.postDelayed({
            bluetoothAdapter?.cancelDiscovery()
            context.unregisterReceiver(receiver)
            _scanState.value = false
        }, 15000)
    }

    @SuppressLint("MissingPermission")
    private fun startLeScan() {
        val leScanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (!foundDevices.contains(device.address)) {
                    Log.d(TAG, "Found LE device: ${device.name} (${device.address})")
                    foundDevices.add(device.address)
                    val printer = PrinterInfo(
                        name = device.name ?: "Unknown",
                        address = device.address,
                        type = PrinterType.BLUETOOTH,
                        isPaired = false,
                        isLikelyPrinter = isThermalPrinter(device)
                    )

                    val currentList = _discoveredPrinters.value.toMutableList()
                    currentList.add(printer)
                    _discoveredPrinters.value = currentList
                }
            }
        }

        val scanFilters = mutableListOf<ScanFilter>()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        leScanner.startScan(scanFilters, scanSettings, scanCallback)

        // Stop scan after 15 seconds
        handler.postDelayed({
            stopScan()
        }, 15000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _scanState.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scanCallback = null
        } else {
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun pairDevice(address: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false

        return try {
            // For Android 12 and above, use bonding method
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                device.createBond()
                true
            } else {
                // Legacy pairing
                val method = device.javaClass.getMethod("createBond")
                method.invoke(device) as Boolean
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pairing device: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(address: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false

        return try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Error unpairing device: ${e.message}")
            false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun isThermalPrinter(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase() ?: return false
        return name.contains("printer") ||
                name.contains("thermal") ||
                name.contains("pos") ||
                name.contains("receipt") ||
                name.contains("tp-") ||
                name.contains("epson") ||
                name.contains("citizen") ||
                name.contains("star") ||
                name.contains("zebra") ||
                device.bluetoothClass?.deviceClass == 224 // Major class: Imaging
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    companion object {
        private const val TAG = "BluetoothScanner"
    }
}