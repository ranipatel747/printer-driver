package pos.helper.thermalprinter.data.printer

data class PrinterInfo(
    val name: String,
    val address: String,
    val type: PrinterType,
    var isPaired: Boolean = false,
    var isConnected: Boolean = false,
    var isDefault: Boolean = false
)

enum class PrinterType {
    BLUETOOTH,
    USB,
    NETWORK
}

data class SavedPrinter(
    val name: String,
    val address: String,
    val type: PrinterType,
    val isDefault: Boolean = false
)