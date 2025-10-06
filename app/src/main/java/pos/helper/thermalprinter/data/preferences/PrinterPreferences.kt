package pos.helper.thermalprinter.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import pos.helper.thermalprinter.data.printer.PrinterType
import pos.helper.thermalprinter.data.printer.SavedPrinter
import androidx.core.content.edit

class PrinterPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "printer_preferences"
        private const val KEY_SAVED_PRINTERS = "saved_printers"
        private const val KEY_DEFAULT_PRINTER = "default_printer"
        private const val KEY_LAST_CONNECTED_TYPE = "last_connected_type"
    }

    fun savePrinter(printer: SavedPrinter) {
        val printers = getSavedPrinters().toMutableList()
        // Remove if already exists
        printers.removeAll { it.address == printer.address }

        // If setting as default, remove default from others before adding new printer
        val updatedPrinters = if (printer.isDefault) {
            printers.map { it.copy(isDefault = false) }.plus(printer)
        } else {
            printers.plus(printer)
        }

        prefs.edit {
            putString(KEY_SAVED_PRINTERS, gson.toJson(updatedPrinters))
        }
    }

    fun getSavedPrinters(): List<SavedPrinter> {
        val json = prefs.getString(KEY_SAVED_PRINTERS, null)
        return if (json != null) {
            val type = object : TypeToken<List<SavedPrinter>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun getDefaultPrinter(): SavedPrinter? {
        return getSavedPrinters().find { it.isDefault }
    }

    fun setDefaultPrinter(address: String) {
        val printers = getSavedPrinters()
        val updated = printers.map { printer ->
            if (printer.address == address) {
                printer.copy(isDefault = true)
            } else if (printer.isDefault) {
                printer.copy(isDefault = false)
            } else {
                printer
            }
        }
        prefs.edit {
            putString(KEY_SAVED_PRINTERS, gson.toJson(updated))
        }
    }

    fun removePrinter(address: String) {
        val printers = getSavedPrinters().toMutableList()
        printers.removeAll { it.address == address }
        prefs.edit {
            putString(KEY_SAVED_PRINTERS, gson.toJson(printers))
        }
    }

    fun saveLastConnectedType(type: String) {
        prefs.edit {
            putString(KEY_LAST_CONNECTED_TYPE, type)
        }
    }

    fun getLastConnectedType(): String? {
        return prefs.getString(KEY_LAST_CONNECTED_TYPE, null)
    }

    fun clearAll() {
        prefs.edit { clear() }
    }
}