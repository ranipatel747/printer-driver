package pos.helper.thermalprinter.printer

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.delay
import pos.helper.thermalprinter.data.websocket.OrderData
import pos.helper.thermalprinter.data.websocket.PrinterStatus
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class VirtualBluetoothPrinter(
    private val context: Context,
    private val deviceName: String = "Virtual Thermal Printer",
    private val deviceAddress: String = "00:11:22:33:44:55"
) : PrinterInterface {

    companion object {
        private const val TAG = "VirtualBluetoothPrinter"
        private const val SIMULATION_DELAY_MS = 500L
    }

    private var connected = false
    private var printJobCount = 0
    private val printQueue = mutableListOf<PrintJob>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    data class PrintJob(
        val id: String,
        val orderData: OrderData,
        val printerWidth: String,
        val timestamp: Date = Date()
    )

    override suspend fun connect(): Boolean {
        return try {
            Log.i(TAG, "Simulating connection to virtual printer: $deviceName")

            // Simulate connection delay
            delay(SIMULATION_DELAY_MS)

            connected = true
            Log.i(TAG, "Virtual printer connected successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to virtual printer", e)
            false
        }
    }

    override suspend fun disconnect() {
        try {
            Log.i(TAG, "Simulating disconnection from virtual printer")
            delay(SIMULATION_DELAY_MS)
            connected = false
            Log.i(TAG, "Virtual printer disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error during virtual printer disconnection", e)
        }
    }

    override suspend fun printReceipt(orderData: OrderData, printerWidth: String): Boolean {
        return try {
            if (!connected) {
                Log.w(TAG, "Cannot print: printer not connected")
                return false
            }

            Log.i(TAG, "Simulating print job for order: ${orderData.orderId}")

            // Simulate printing delay
            delay(SIMULATION_DELAY_MS * 2)

            // Add to print queue
            val printJob = PrintJob(
                id = "PRINT_${++printJobCount}_${System.currentTimeMillis()}",
                orderData = orderData,
                printerWidth = printerWidth
            )
            printQueue.add(printJob)

            // Save receipt to gallery
            val saved = saveReceiptToGallery(printJob)

            if (saved) {
                Log.i(TAG, "Virtual print completed successfully")
                true
            } else {
                Log.e(TAG, "Failed to save receipt to gallery")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during virtual printing", e)
            false
        }
    }

    override fun isConnected(): Boolean = connected

    override fun getStatus(): PrinterStatus {
        return PrinterStatus(
            connected = connected,
            type = "virtual_bluetooth",
            name = deviceName,
            paperStatus = if (connected) "ok" else "out",
            inkStatus = if (connected) "ok" else "low"
        )
    }

    override fun getPrinterName(): String = deviceName

    fun getDeviceAddress(): String = deviceAddress

    fun getPrintHistory(): List<PrintJob> = printQueue.toList()

    fun clearPrintHistory() {
        printQueue.clear()
        printJobCount = 0
    }

    private fun saveReceiptToGallery(printJob: PrintJob): Boolean {
        return try {
            val bitmap = createReceiptBitmap(printJob)
            val filename = "Receipt_${printJob.orderData.orderId}_${dateFormat.format(printJob.timestamp)}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(bitmap, filename)
            } else {
                saveToFile(bitmap, filename)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt to gallery", e)
            false
        }
    }

    @SuppressLint("DefaultLocale")
    private fun createReceiptBitmap(printJob: PrintJob): Bitmap {
        val width = 384 // Typical thermal printer width in pixels
        var height = 100 // Base height

        // Calculate height based on content
        val orderData = printJob.orderData
        height += 50 // Header
        height += (orderData.items?.size ?: 0) * 40 // Items
        height += 80 // Footer

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // White background
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        var yPosition = 30f

        // Header
        paint.textSize = 16f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("RECEIPT", width / 2f, yPosition, paint)

        yPosition += 30f
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Order #${orderData.orderId}", width / 2f, yPosition, paint)

        yPosition += 25f
        canvas.drawText(dateFormat.format(printJob.timestamp), width / 2f, yPosition, paint)

        // Items
        yPosition += 40f
        paint.textAlign = Paint.Align.LEFT
        orderData.items.forEach { item ->
            yPosition += 20f
            canvas.drawText(item.name, 10f, yPosition, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$${String.format("%.2f", item.price)}", width - 10f, yPosition, paint)
            paint.textAlign = Paint.Align.LEFT

            if (item.qty > 1) {
                yPosition += 15f
                canvas.drawText("  x${item.qty}", 10f, yPosition, paint)
            }
        }

        // Total
        yPosition += 30f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("------------------------", 10f, yPosition, paint)

        yPosition += 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("TOTAL: $${String.format("%.2f", calculateTotal(orderData))}", 10f, yPosition, paint)

        // Footer
        yPosition += 30f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Thank you!", width / 2f, yPosition, paint)

        yPosition += 20f
        canvas.drawText("Virtual Printer Demo", width / 2f, yPosition, paint)

        return bitmap
    }

    private fun calculateTotal(orderData: OrderData): Double {
        return orderData.items?.sumOf { it.price * it.qty } ?: 0.0
    }

    private fun saveToMediaStore(bitmap: Bitmap, filename: String): Boolean {
        return try {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ThermalPrinter")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(collection, contentValues)
            uri?.let {
                val outputStream = context.contentResolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(it, contentValues, null, null)

                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore", e)
            false
        }
    }

    private fun saveToFile(bitmap: Bitmap, filename: String): Boolean {
        return try {
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ThermalPrinter")
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            val file = File(picturesDir, filename)
            val outputStream = FileOutputStream(file)

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            // Notify the gallery
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to file", e)
            false
        }
    }

    suspend fun simulatePrinterError(): Boolean {
        return try {
            if (connected) {
                Log.i(TAG, "Simulating printer error")
                connected = false
                delay(SIMULATION_DELAY_MS)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating printer error", e)
            false
        }
    }

    suspend fun simulatePrinterRecovery(): Boolean {
        return try {
            if (!connected) {
                Log.i(TAG, "Simulating printer recovery")
                delay(SIMULATION_DELAY_MS)
                connected = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating printer recovery", e)
            false
        }
    }
}