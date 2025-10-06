package pos.helper.thermalprinter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import pos.helper.thermalprinter.data.printer.PrinterType
import pos.helper.thermalprinter.ui.viewmodel.PrinterViewModel

@Composable
fun PrinterListScreen(
    viewModel: PrinterViewModel = viewModel(),
    onBack: () -> Unit
) {
    val discoveredPrinters by viewModel.discoveredPrinters.collectAsState()
    val savedPrinters by viewModel.savedPrinters.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val defaultPrinter by viewModel.defaultPrinter.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    var showSavedPrinters by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.savedPrinters.collectLatest {
            // Refresh data when saved printers change
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Printer Management",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.isBluetoothEnabled()) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth ON",
                                tint = Color.Green
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Bluetooth Enabled")
                        } else {
                            Icon(
                                Icons.Outlined.BluetoothDisabled,
                                contentDescription = "Bluetooth OFF",
                                tint = Color.Red
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Bluetooth Disabled")
                        }
                    }

                    if (defaultPrinter != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Default",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Default: ${defaultPrinter?.name}")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Connection Status
        Text(
            text = connectionStatus,
            fontSize = 14.sp,
            color = when {
                connectionStatus.contains("success") -> Color.Green
                connectionStatus.contains("fail") -> Color.Red
                connectionStatus.contains("connecting") -> Color(0xFF0066CC)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(Modifier.height(16.dp))

        // Toggle Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    showSavedPrinters = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showSavedPrinters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Saved Printers")
            }
            Button(
                onClick = {
                    showSavedPrinters = false
                    if (!isScanning) {
                        viewModel.startScan()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showSavedPrinters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Discover")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Search Button for Discover mode
        if (!showSavedPrinters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isScanning) {
                            viewModel.stopScan()
                        } else {
                            viewModel.startScan()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color.Red else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                        contentDescription = if (isScanning) "Stop" else "Search"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isScanning) "Stop Scanning" else "Start Scanning")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Printers List
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                if (showSavedPrinters) {
                    // Saved Printers
                    if (savedPrinters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No saved printers",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedPrinters) { printer ->
                                SavedPrinterItem(
                                    printer = printer,
                                    isDefault = printer.address == defaultPrinter?.address,
                                    onConnect = { viewModel.connectToPrinter(printer.address) },
                                    onSetDefault = { viewModel.setDefaultPrinter(printer.address) },
                                    onRemove = { viewModel.removePrinter(printer.address) },
                                    onTestPrint = { viewModel.testPrint() },
                                    onDisconnect = { viewModel.disconnectPrinter() }
                                )
                            }
                        }
                    }
                } else {
                    // Discovered Printers
                    if (discoveredPrinters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isScanning) "Scanning for printers..." else "No printers found",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredPrinters) { printer ->
                                DiscoveredPrinterItem(
                                    printer = printer,
                                    onSave = { makeDefault ->
                                        viewModel.savePrinter(printer, makeDefault)
                                    },
                                    onPair = { viewModel.pairPrinter(printer.address) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredPrinterItem(
    printer: pos.helper.thermalprinter.data.printer.PrinterInfo,
    onSave: (Boolean) -> Unit,
    onPair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = printer.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = printer.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = printer.type.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (printer.isPaired) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Paired",
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!printer.isPaired) {
                        OutlinedButton(
                            onClick = onPair,
                            modifier = Modifier.sizeIn(minWidth = 80.dp)
                        ) {
                            Text("Pair")
                        }
                    }
                    Button(
                        onClick = { onSave(false) },
                        modifier = Modifier.sizeIn(minWidth = 80.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Save")
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { onSave(true) },
                    modifier = Modifier.width(120.dp)
                ) {
                    Text("Save & Set Default")
                }
            }
        }
    }
}

@Composable
fun SavedPrinterItem(
    printer: pos.helper.thermalprinter.data.printer.SavedPrinter,
    isDefault: Boolean,
    onConnect: () -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
    onTestPrint: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = printer.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isDefault) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "DEFAULT",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = printer.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = printer.type.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Connect")
                }

                Button(
                    onClick = onTestPrint,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Test Print")
                }

                if (!isDefault) {
                    OutlinedButton(
                        onClick = onSetDefault,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set Default")
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}