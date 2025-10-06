package pos.helper.thermalprinter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pos.helper.thermalprinter.service.WebSocketService
import pos.helper.thermalprinter.ui.theme.ThermalPrinterTheme

class MainActivity : ComponentActivity() {

    private val bluetoothRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth required to use printers", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThermalPrinterTheme {
                MainContent()
            }
        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            delay(500) // Add delay to ensure permission checks complete
            WebSocketService.startService(this@MainActivity)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableListOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 1001)
        } else {
            enableBluetooth()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableBluetooth()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
        } else if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothRequestLauncher.launch(enableBtIntent)
        }
    }

    @Composable
    fun MainContent() {
        var wsPort by remember { mutableStateOf("8765") }
        var wsStatus by remember { mutableStateOf("Checking...") }
        var btStatus by remember { mutableStateOf("Checking...") }
        var printerStatus by remember { mutableStateOf("Unknown") }

        LaunchedEffect(Unit) {
            while (true) {
                delay(2000)
                // Update statuses
                // In a real app, you'd use ViewModel or LiveData to observe these values
                wsStatus = "Running on port $wsPort"
                btStatus = if (isBluetoothEnabled()) "Enabled" else "Disabled"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Thermal Printer Server",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "WebSocket Server",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("Status: $wsStatus")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = wsPort,
                            onValueChange = { wsPort = it },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val port = wsPort.toIntOrNull() ?: 8765
                                WebSocketService.stopService(this@MainActivity)
                                WebSocketService.startService(this@MainActivity, port)
                                Toast.makeText(this@MainActivity, "Server restarted on port $port", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Restart")
                        }
                    }

                    Text(
                        text = "WebSocket URL: ws://127.0.0.1:$wsPort",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Connection Status",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("Bluetooth: $btStatus")
                    Text("Printer: $printerStatus")

                    Button(
                        onClick = {
                            Toast.makeText(this@MainActivity, "Searching for printers...", Toast.LENGTH_SHORT).show()
                            // Trigger printer search
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Search Printers")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Instructions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "1. Connect your thermal printer via Bluetooth\n" +
                        "2. Note the WebSocket URL shown above\n" +
                        "3. In your Laravel app, connect to the WebSocket\n" +
                        "4. Use token: 'thermal-printer-2024'\n" +
                        "5. Send print orders in JSON format",
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Button(
                onClick = {
                    createNotificationChannel()
                    //Toast.makeText(this, "Service started in background", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Background Service")
            }
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.isEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun createNotificationChannel() {
        // Implementation already exists in WebSocketService
    }
}