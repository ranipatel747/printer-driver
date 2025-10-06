package pos.helper.thermalprinter.printer

import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.PrinterStatus

interface PrinterInterface {
    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun printReceipt(orderData: OrderData, printerWidth: String): Boolean
    fun isConnected(): Boolean
    fun getStatus(): PrinterStatus
    fun getPrinterName(): String
}