package com.example.visitingcard

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource

class CardQrScannerUI : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private var handledResult = false

    private val cameraPermissionRequester = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanning() else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val bitmap = loadBitmapFromUri(uri)
            val decoded = decodeQrFromBitmap(bitmap)
            if (decoded != null) {
                // For gallery import: no beep, just a subtle vibration
                vibrateOnce()
                navigateToPreview(decoded.text)
            } else {
                Toast.makeText(this, "No QR code found in selected image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_qr_scanner)

        // Optional toolbar was removed from the layout; no navigation view to bind here

        barcodeView = findViewById(R.id.barcodeScanner)
        findViewById<MaterialButton>(R.id.btnGallery).setOnClickListener {
            imagePicker.launch("image/*")
        }

        // Request camera permission if needed, else start scanning
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startScanning() else cameraPermissionRequester.launch(Manifest.permission.CAMERA)
    }

    private fun startScanning() {
        handledResult = false
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val text = result?.text ?: return
                if (handledResult) return
                handledResult = true
                // Play feedback on successful live scan
                playBeepAndVibrate()
                navigateToPreview(text)
            }
        })
        barcodeView.resume()
    }

    private fun navigateToPreview(text: String) {
        barcodeView.pause()
        val intent = Intent(this, ScannedPreviewUI::class.java)
        intent.putExtra("cardData", text)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!handledResult) barcodeView.resume()
    }

    override fun onPause() {
        barcodeView.pause()
        super.onPause()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // Force software allocator to avoid HARDWARE bitmaps
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
            // Ensure ARGB_8888 config for getPixels()
            if (decoded.config != Bitmap.Config.ARGB_8888) {
                decoded.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                decoded
            }
        } catch (e: NoClassDefFoundError) {
            @Suppress("DEPRECATION")
            val legacy = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            if (legacy.config != Bitmap.Config.ARGB_8888) legacy.copy(Bitmap.Config.ARGB_8888, false) else legacy
        }
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): Result? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            MultiFormatReader().decode(binaryBitmap)
        } catch (e: NotFoundException) {
            null
        }
    }

    private fun playBeepAndVibrate() {
        // Beep
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
        // Vibrate
        vibrateOnce()
    }

    private fun vibrateOnce() {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (_: Exception) {}
    }
}
