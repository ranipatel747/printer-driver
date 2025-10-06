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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.PhonelinkRing
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

enum class PrinterTab {
    VIRTUAL,
    PAIRED,
    NEARBY
}

@Composable
fun PrinterListScreen(
    viewModel: PrinterViewModel = viewModel(),
    onBack: () -> Unit
) {
    val virtualPrinters by viewModel.virtualPrinters.collectAsState()
    val pairedPrinters by viewModel.pairedPrinters.collectAsState()
    val nearbyPrinters by viewModel.nearbyPrinters.collectAsState()
    val savedPrinters by viewModel.savedPrinters.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val defaultPrinter by viewModel.defaultPrinter.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    var selectedTab by remember { mutableStateOf(PrinterTab.VIRTUAL) }

    LaunchedEffect(Unit) {
        viewModel.savedPrinters.collectLatest {
            // Refresh data when saved printers change
        }
    }

    // Start scanning when the screen is opened to get initial data
    LaunchedEffect(Unit) {
        viewModel.startScan()
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

        // Tab Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == PrinterTab.VIRTUAL,
                    onClick = { selectedTab = PrinterTab.VIRTUAL },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Virtual")
                            if (virtualPrinters.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "(${virtualPrinters.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Virtual Printers"
                        )
                    }
                )
                Tab(
                    selected = selectedTab == PrinterTab.PAIRED,
                    onClick = { selectedTab = PrinterTab.PAIRED },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Paired")
                            if (pairedPrinters.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "(${pairedPrinters.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = "Paired Devices"
                        )
                    }
                )
                Tab(
                    selected = selectedTab == PrinterTab.NEARBY,
                    onClick = {
                        selectedTab = PrinterTab.NEARBY
                        if (!isScanning) {
                            viewModel.startScan()
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nearby")
                            if (nearbyPrinters.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "(${nearbyPrinters.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Outlined.PhonelinkRing,
                            contentDescription = "Nearby Devices"
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Scan control for Nearby tab
        if (selectedTab == PrinterTab.NEARBY) {
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
                    Text(if (isScanning) "Stop Scanning" else "Scan for Devices")
                }

                if (nearbyPrinters.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.startScan() },
                        modifier = Modifier.weight(0.5f)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
                when (selectedTab) {
                    PrinterTab.VIRTUAL -> {
                        // Virtual Printers Tab
                        Text(
                            text = "Virtual printers for testing purposes. These don't require physical hardware.",
                            fontSize = 12.sp,
                            color = Color.Green,
                            modifier = Modifier.padding(8.dp)
                        )

                        if (virtualPrinters.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No virtual printers available",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(virtualPrinters) { printer ->
                                    DiscoveredPrinterItem(
                                        printer = printer,
                                        onSave = { makeDefault ->
                                            viewModel.savePrinter(printer, makeDefault)
                                        },
                                        onPair = { },
                                        onConnect = { viewModel.connectToDiscoveredPrinter(printer.address) },
                                        isVirtualPrinter = true,
                                        showSaveButton = true
                                    )
                                }
                            }
                        }
                    }

                    PrinterTab.PAIRED -> {
                        // Paired Devices Tab
                        Text(
                            text = "Bluetooth devices that are already paired with your device.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(8.dp)
                        )

                        if (pairedPrinters.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No paired Bluetooth printers",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pairedPrinters) { printer ->
                                    DiscoveredPrinterItem(
                                        printer = printer,
                                        onSave = { makeDefault ->
                                            viewModel.savePrinter(printer, makeDefault)
                                        },
                                        onPair = { },
                                        onConnect = { viewModel.connectToDiscoveredPrinter(printer.address) },
                                        isVirtualPrinter = false,
                                        showSaveButton = true
                                    )
                                }
                            }
                        }
                    }

                    PrinterTab.NEARBY -> {
                        // Nearby Devices Tab
                        Text(
                            text = "Unpaired Bluetooth devices nearby. You need to pair them first before connecting.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(8.dp)
                        )

                        if (nearbyPrinters.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isScanning) "Scanning for nearby devices..." else "No nearby devices found",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(nearbyPrinters) { printer ->
                                    DiscoveredPrinterItem(
                                        printer = printer,
                                        onSave = { }, // Can't save unpaired printers
                                        onPair = { viewModel.pairPrinter(printer.address) },
                                        onConnect = { viewModel.pairPrinter(printer.address) }, // First pair then connect
                                        isVirtualPrinter = false,
                                        showSaveButton = false // Don't show save button for unpaired devices
                                    )
                                }
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
    onPair: () -> Unit,
    onConnect: () -> Unit,
    isVirtualPrinter: Boolean = false,
    showSaveButton: Boolean = true
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
                        if (isVirtualPrinter) {
                            Text(
                                text = "VIRTUAL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Green,
                                modifier = Modifier.background(Color(0x2000FF00), RoundedCornerShape(2.dp)).padding(2.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }

                        Text(
                            text = printer.type.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (printer.isLikelyPrinter) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = "Likely Printer",
                                tint = Color.Blue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                    // For virtual printers, show connect button instead of pair
                    if (isVirtualPrinter) {
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.sizeIn(minWidth = 80.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00AA00)
                            )
                        ) {
                            Text("Connect")
                        }
                    } else if (!printer.isPaired) {
                        OutlinedButton(
                            onClick = onPair,
                            modifier = Modifier.sizeIn(minWidth = 80.dp)
                        ) {
                            Text("Pair")
                        }
                    } else {
                        // For paired devices, show Connect button
                        OutlinedButton(
                            onClick = onConnect,
                            modifier = Modifier.sizeIn(minWidth = 80.dp)
                        ) {
                            Text("Connect")
                        }
                    }

                    // Only show Save button if allowed
                    if (showSaveButton) {
                        Button(
                            onClick = { onSave(false) },
                            modifier = Modifier.sizeIn(minWidth = 80.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Save as Printer")
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
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