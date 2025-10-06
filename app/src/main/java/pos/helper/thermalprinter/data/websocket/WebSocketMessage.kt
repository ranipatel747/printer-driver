package pos.helper.thermalprinter.data.websocket

import com.google.gson.annotations.SerializedName

data class WebSocketMessage(
    val type: String,
    val token: String? = null,
    val id: String? = null,
    @SerializedName("printer_width")
    val printerWidth: String? = null,
    val data: OrderData? = null,
    val printer: PrinterStatus? = null,
    @SerializedName("app_status")
    val appStatus: String? = null
)

data class OrderData(
    @SerializedName("order_id")
    val orderId: String,
    val customer: String,
    val items: List<OrderItem>,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    @SerializedName("payment_method")
    val paymentMethod: String,
    val timestamp: String
)

data class OrderItem(
    val name: String,
    val qty: Int,
    val price: Double
)

data class PrinterStatus(
    val connected: Boolean,
    val type: String,
    val name: String,
    @SerializedName("paper_status")
    val paperStatus: String,
    @SerializedName("ink_status")
    val inkStatus: String
)