package pos.helper.thermalprinter.printer

import pos.helper.thermalprinter.data.websocket.OrderData

object EscPosCommands {
    // Initialize printer
    val INIT = byteArrayOf(0x1B, 0x40)

    // Text formatting
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)

    // Font styles
    val FONT_NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    val FONT_BOLD = byteArrayOf(0x1B, 0x21, 0x08)
    val FONT_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    val FONT_DOUBLE_WIDTH = byteArrayOf(0x1B, 0x21, 0x20)
    val FONT_DOUBLE_SIZE = byteArrayOf(0x1B, 0x21, 0x30)
    val FONT_BOLD_DOUBLE = byteArrayOf(0x1B, 0x21, 0x38)

    // Line spacing
    val LINE_SPACING_DEFAULT = byteArrayOf(0x1B, 0x32)
    val LINE_SPACING_24 = byteArrayOf(0x1B, 0x33, 0x18)

    // Paper cut
    val PAPER_CUT = byteArrayOf(0x1B, 0x64, 0x02)
    val PAPER_CUT_PARTIAL = byteArrayOf(0x1B, 0x6D)

    // Barcode
    val BARCODE_HEIGHT = byteArrayOf(0x1D, 0x68, 0x64) // Height = 100
    val BARCODE_WIDTH = byteArrayOf(0x1D, 0x77, 0x03) // Width = 3
    val BARCODE_TEXT_POSITION = byteArrayOf(0x1D, 0x48, 0x02) // Text below
    val BARCODE_PRINT = byteArrayOf(0x1D, 0x6B, 0x49) // Type CODE128

    // Image
    val IMAGE_START = byteArrayOf(0x1D, 0x76, 0x30, 0x00)

    // Status commands
    val STATUS_REQUEST = byteArrayOf(0x1D, 0x72, 0x01)

    // Line feed
    val LINE_FEED = byteArrayOf(0x0A)

    // Feed and cut
    val FEED_AND_CUT = byteArrayOf(0x1B, 0x64, 0x0A, 0x0A, 0x1D, 0x56, 0x00)

    // Chinese character support (if needed)
    val CHINESE_MODE_ON = byteArrayOf(0x1C, 0x26)
    val CHINESE_MODE_OFF = byteArrayOf(0x1C, 0x2E)

    fun createReceiptBytes(
        orderData: OrderData,
        printerWidth: String
    ): ByteArray {
        val charsPerLine = if (printerWidth == "80mm") 48 else 32
        val bytes = mutableListOf<Byte>()

        // Initialize
        addBytes(bytes, INIT)
        addBytes(bytes, ALIGN_CENTER)
        addBytes(bytes, FONT_BOLD_DOUBLE)

        // Store name
        var text = "YOUR STORE NAME\n"
        addBytes(bytes, text.toByteArray())
        addBytes(bytes, FONT_BOLD)
        text = "123 Main Street\n"
        addBytes(bytes, text.toByteArray())
        text = "Tel: 123-456-7890\n\n"
        addBytes(bytes, text.toByteArray())

        // Separator
        addBytes(bytes, createSeparator(charsPerLine))
        addBytes(bytes, LINE_FEED)

        // Order info
        addBytes(bytes, ALIGN_LEFT)
        addBytes(bytes, FONT_NORMAL)
        text = "Order: ${orderData.orderId}\n"
        addBytes(bytes, text.toByteArray())
        text = "Date: ${orderData.timestamp}\n"
        addBytes(bytes, text.toByteArray())
        text = "Customer: ${orderData.customer}\n"
        addBytes(bytes, text.toByteArray())
        addBytes(bytes, LINE_FEED)

        // Items header
        addBytes(bytes, createSeparator(charsPerLine))
        text = if (printerWidth == "80mm") {
            "${padText("Item", 25)}${padText("Qty", 5)}${padText("Price", 9)}${padText("Total", 9)}\n"
        } else {
            "${padText("Item", 16)}${padText("Qty", 3)}${padText("Price", 6)}${padText("Total", 7)}\n"
        }
        addBytes(bytes, text.toByteArray())
        addBytes(bytes, createSeparator(charsPerLine))

        // Items
        orderData.items.forEach { item ->
            var itemName = if (printerWidth == "80mm") {
                padText(item.name, 25)
            } else {
                padText(item.name, 16)
            }

            val qty = item.qty.toString()
            val price = String.format("%.2f", item.price)
            val total = String.format("%.2f", item.qty * item.price)

            text = if (printerWidth == "80mm") {
                "$itemName${padText(qty, 5)}${padText(price, 9)}${padText(total, 9)}\n"
            } else {
                "$itemName${padText(qty, 3)}${padText(price, 6)}${padText(total, 7)}\n"
            }
            addBytes(bytes, text.toByteArray())
        }

        addBytes(bytes, LINE_FEED)
        addBytes(bytes, createSeparator(charsPerLine))
        addBytes(bytes, LINE_FEED)

        // Totals
        addBytes(bytes, ALIGN_RIGHT)
        text = if (printerWidth == "80mm") {
            "Subtotal: ${padText("", 20)}${String.format("%.2f", orderData.subtotal)}\n"
        } else {
            "Subtotal: ${String.format("%.2f", orderData.subtotal)}\n"
        }
        addBytes(bytes, text.toByteArray())

        text = if (printerWidth == "80mm") {
            "Tax: ${padText("", 24)}${String.format("%.2f", orderData.tax)}\n"
        } else {
            "Tax: ${String.format("%.2f", orderData.tax)}\n"
        }
        addBytes(bytes, text.toByteArray())

        addBytes(bytes, FONT_BOLD)
        text = if (printerWidth == "80mm") {
            "TOTAL: ${padText("", 22)}${String.format("%.2f", orderData.total)}\n"
        } else {
            "TOTAL: ${String.format("%.2f", orderData.total)}\n"
        }
        addBytes(bytes, text.toByteArray())

        addBytes(bytes, FONT_NORMAL)
        addBytes(bytes, ALIGN_LEFT)
        addBytes(bytes, LINE_FEED)
        addBytes(bytes, LINE_FEED)

        // Payment method
        text = "Payment: ${orderData.paymentMethod}\n"
        addBytes(bytes, text.toByteArray())

        // Footer
        addBytes(bytes, LINE_FEED)
        addBytes(bytes, ALIGN_CENTER)
        text = "Thank you for your purchase!\n"
        addBytes(bytes, text.toByteArray())
        text = "Please come again\n\n\n"
        addBytes(bytes, text.toByteArray())

        // Cut paper
        addBytes(bytes, FEED_AND_CUT)

        return bytes.toByteArray()
    }

    private fun addBytes(target: MutableList<Byte>, source: ByteArray) {
        source.forEach { byte ->
            target.add(byte)
        }
    }

    private fun createSeparator(length: Int): ByteArray {
        return "=".repeat(length).toByteArray().toList().toByteArray()
    }

    private fun padText(text: String, length: Int): String {
        return text.padEnd(length, ' ').take(length)
    }
}