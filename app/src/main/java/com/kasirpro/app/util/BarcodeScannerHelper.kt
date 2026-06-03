package com.kasirpro.app.util

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

object BarcodeScannerHelper {
    fun startScan(context: Context, onScanSuccess: (String) -> Unit) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)

            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        onScanSuccess(rawValue)
                    } else {
                        Toast.makeText(context, "Barcode kosong atau tidak terbaca.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Scan dibatalkan: ${e.localizedMessage ?: "User cancel"}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "GMS Code Scanner tidak tersedia. Pastikan Google Play Services terpasang.", Toast.LENGTH_LONG).show()
        }
    }
}
