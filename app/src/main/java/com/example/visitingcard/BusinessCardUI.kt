package com.example.visitingcard

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import android.content.ContentValues
import android.content.ClipData
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.graphics.pdf.PdfDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File
import java.io.FileOutputStream
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class BusinessCardUI : AppCompatActivity() {
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private lateinit var cardStorageHelper: CardStorageHelper
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var name: TextView
    private lateinit var occ: TextView
    private lateinit var email: TextView
    private lateinit var phone: TextView
    private lateinit var instagram: TextView
    private lateinit var website: TextView
    private lateinit var address: TextView

    // --- Link sanitizers (class-level) ---
    private fun slugify(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isBlank()) return "business_card"
        val mapped = buildString(trimmed.length) {
            for (ch in trimmed) {
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '+' -> append('p') // avoid '+' in filenames
                    ch == ' ' || ch == '-' || ch == '_' -> append('_')
                    else -> { /* skip */ }
                }
            }
        }.trim('_')
        return if (mapped.isBlank()) "business_card" else mapped
    }
    private fun sanitizePhoneForTel(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        // Keep leading '+' if present, then digits only
        val cleaned = buildString {
            var i = 0
            if (trimmed.startsWith("+")) { append('+'); i = 1 }
            while (i < trimmed.length) {
                val ch = trimmed[i]
                if (ch.isDigit()) append(ch)
                i++
            }
        }
        return if (cleaned.any { it.isDigit() }) "tel:$cleaned" else null
    }

    private fun sanitizeWebsite(raw: String): String? {
        val t = raw.trim()
        if (t.isBlank()) return null
        val lower = t.lowercase()
        val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
        return if (hasScheme) t else "https://$t"
    }

    private fun sanitizeInstagram(raw: String): String? {
        var t = raw.trim()
        if (t.isBlank()) return null
        // Remove accidental variable placeholders and trailing noise
        t = t.removePrefix("@").trimEnd('/', '#')
        if (t.contains("instagram.com", ignoreCase = true)) {
            // Ensure https scheme
            return sanitizeWebsite(t)
        }
        return if (t.isNotBlank()) "https://instagram.com/$t" else null
    }

    private fun buildMapsSearchUrl(addr: String): String? {
        val t = addr.trim()
        if (t.isBlank()) return null
        val q = Uri.encode(t)
        return "https://www.google.com/maps/search/?api=1&query=$q"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light mode for the entire app regardless of system setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.business_card_ui)
        // Make system bars fit system windows (disable translucent overlay), so status bar is solid
        WindowCompat.setDecorFitsSystemWindows(window, true)
        // Force dark status bar icons (background color provided by theme)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        // Initialize PDFBox once
        PDFBoxResourceLoader.init(applicationContext)
        
        // Firebase Auth is initialized via lazy delegate
        
        // Drawer setup
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)

        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // Dark mode switch removed; app remains in light mode

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        // Initialize drawer toggle checked state
        navigationView.menu.findItem(R.id.nav_auto_sync)?.isChecked = prefs.getBoolean("auto_sync_enabled", true)

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_edit_info -> {
                    val intent = Intent(this, EditInfoUI::class.java).apply {
                        putExtra("editMode", true)
                        putExtra("Name", name.text.toString())
                        putExtra("Occupation", occ.text.toString())
                        putExtra("Email", email.text.toString())
                        putExtra("Phone", phone.text.toString())
                        putExtra("Instagram", instagram.text.toString())
                        putExtra("Website", website.text.toString())
                        putExtra("Address", address.text.toString())
                    }
                    startActivity(intent)
                    finish()
                }
                R.id.nav_logout -> {
                    signOut()
                }
                R.id.nav_auto_sync -> {
                    val newVal = !item.isChecked
                    item.isChecked = newVal
                    prefs.edit().putBoolean("auto_sync_enabled", newVal).apply()
                    Toast.makeText(this, if (newVal) "Auto Sync ON" else "Auto Sync OFF", Toast.LENGTH_SHORT).show()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not signed in, redirect to login
            startActivity(Intent(this, LoginUI::class.java))
            finish()
            return
        }

        name = findViewById(R.id.nameText)
        occ = findViewById(R.id.occupation)
        email = findViewById(R.id.email)
        phone = findViewById(R.id.phoneno)
        instagram = findViewById(R.id.instagram)
        website = findViewById(R.id.website)
        address = findViewById(R.id.address)

        // Row containers for hiding entire pill + icon when data is missing
        val phoneRow = findViewById<LinearLayout>(R.id.phoneRow)
        val addressRow = findViewById<LinearLayout>(R.id.addressRow)
        val emailRow = findViewById<LinearLayout>(R.id.emailRow)
        val websiteRow = findViewById<LinearLayout>(R.id.websiteRow)
        val instagramRow = findViewById<LinearLayout>(R.id.instagramRow)

        val btnQr = findViewById<View>(R.id.btnQr)
        val viewSavedBtn = findViewById<View>(R.id.viewSavedCardsBtn)
        val scanQrBtn = findViewById<View>(R.id.scanQrBtn)
        val shareImageBtn = findViewById<View>(R.id.shareImageBtn)
        val bottomActionBar = findViewById<LinearLayout>(R.id.bottomActionBar)

        // Ensure sticky bottom bar sits above gesture/nav bar area
        ViewCompat.setOnApplyWindowInsetsListener(bottomActionBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + systemBars.bottom)
            insets
        }

        val nameStr = intent.getStringExtra("Name") ?: ""
        val occStr = intent.getStringExtra("Occupation") ?: ""
        val emailStr = intent.getStringExtra("Email") ?: ""
        val phoneStr = intent.getStringExtra("Phone") ?: ""
        val instagramStr = intent.getStringExtra("Instagram") ?: ""
        val websiteStr = intent.getStringExtra("Website") ?: ""
        val addressStr = intent.getStringExtra("Address") ?: ""

        name.text = nameStr
        occ.text = occStr
        email.text = emailStr
        phone.text = phoneStr
        instagram.text = instagramStr
        website.text = websiteStr
        address.text = addressStr

        // Hide empty rows so pills/icons don't show without data
        emailRow.visibility = if (emailStr.isBlank()) View.GONE else View.VISIBLE
        phoneRow.visibility = if (phoneStr.isBlank()) View.GONE else View.VISIBLE
        instagramRow.visibility = if (instagramStr.isBlank()) View.GONE else View.VISIBLE
        websiteRow.visibility = if (websiteStr.isBlank()) View.GONE else View.VISIBLE
        addressRow.visibility = if (addressStr.isBlank()) View.GONE else View.VISIBLE

        phone.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.text}")))
        }

        email.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.text}"))
            startActivity(Intent.createChooser(emailIntent, "Send email"))
        }

        website.setOnClickListener {
            val url = if (websiteStr.startsWith("http")) websiteStr else "https://$websiteStr"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // Open maps for address
        address.setOnClickListener {
            val query = address.text.toString().trim()
            if (query.isNotEmpty()) {
                val encoded = Uri.encode(query)
                // Prefer Google Maps app if available
                val gmmIntentUri = Uri.parse("geo:0,0?q=$encoded")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    // Fallback to browser
                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            }
        }
        // Also make the whole row open maps
        addressRow.setOnClickListener { address.performClick() }

        instagram.setOnClickListener {
            val url = if (instagramStr.startsWith("http")) instagramStr else "https://instagram.com/$instagramStr"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            AlertDialog.Builder(this)
                .setTitle("Save Card")
                .setMessage("Save this card to your account?")
                .setPositiveButton("Save") { _, _ ->
                    // Save to Firebase Realtime Database as the user's profile
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginUI::class.java))
                        finish()
                        return@setPositiveButton
                    }

                    val profile = mapOf(
                        "Name" to name.text.toString(),
                        "Occupation" to occ.text.toString(),
                        "Email" to email.text.toString(),
                        "Phone" to phone.text.toString(),
                        "Instagram" to instagram.text.toString(),
                        "Website" to website.text.toString(),
                        "Address" to address.text.toString()
                    )

                    Firebase.database.reference
                        .child("users")
                        .child(currentUser.uid)
                        .child("profile")
                        .setValue(profile)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Card saved to your account", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e("VisitingCard", "Failed to save profile: ", e)
                            Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show()
                        }

                    // Also save to local database using CardStorageHelper with user ID (optional)
                    val cardStorageHelper = CardStorageHelper(this)
                    cardStorageHelper.insertCard(
                        userId = currentUser?.uid ?: "",
                        name = name.text.toString(),
                        occupation = occ.text.toString(),
                        email = email.text.toString(),
                        phone = phone.text.toString(),
                        instagram = instagram.text.toString(),
                        website = website.text.toString(),
                        address = address.text.toString()
                    )
                    startActivity(Intent(this, SavedCardsUI::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnQr.setOnClickListener {
            val dataToEncode = """
                Name: $nameStr
                Occupation: $occStr
                Phone: $phoneStr
                Address: $addressStr
                Email: $emailStr
                Instagram: $instagramStr
                Website: $websiteStr
                
            """.trimIndent()

            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(dataToEncode, BarcodeFormat.QR_CODE, 600, 600)

            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 12, 24, 16)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                addView(imageView, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                })
                // Centered button bar (Save on left, Close on right)
                val buttonBar = LinearLayout(this@BusinessCardUI).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                val saveBtn = Button(this@BusinessCardUI).apply {
                    text = "Save"
                    isAllCaps = false
                    // Remove background to match request
                    background = null
                }
                val closeBtn = Button(this@BusinessCardUI).apply {
                    text = "Close"
                    isAllCaps = false
                    // Remove background and keep red text
                    background = null
                    setTextColor(Color.RED)
                }
                val btnLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 8, 16, 0) }
                buttonBar.addView(saveBtn, btnLp)
                buttonBar.addView(closeBtn, btnLp)
                addView(buttonBar, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
            }

            // Centered title and Save on the left, Close on the right
            val titleView = TextView(this).apply {
                text = "Scan this QR Code"
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(32, 24, 32, 24)
                setTextSize(18f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.BLACK)
            }

            val builder = AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setView(contentContainer)

            val dialog = builder.create()
            // Wire up custom buttons after dialog is created, so we can dismiss it
            val buttonBar = (contentContainer.getChildAt(1) as LinearLayout)
            val saveBtn = buttonBar.getChildAt(0) as Button
            val closeBtn = buttonBar.getChildAt(1) as Button

            // Keep Save text color same as before (use theme primary color)
            run {
                val tv = TypedValue()
                val resolved = if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)) tv else null
                resolved?.let {
                    val color = if (it.resourceId != 0) ContextCompat.getColor(this, it.resourceId) else it.data
                    saveBtn.setTextColor(color)
                }
            }
            saveBtn.setOnClickListener {
                saveImageToGallery(bitmap)
                dialog.dismiss()
            }
            closeBtn.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }

        viewSavedBtn.setOnClickListener {
            startActivity(Intent(this, SavedCardsUI::class.java))
        }

        scanQrBtn.setOnClickListener {
            startActivity(Intent(this, CardQrScannerUI::class.java))
        }

        shareImageBtn.setOnClickListener {
            // Offer a choice: Image (JPG) or PDF
            val options = arrayOf("Share as Image (JPG)", "Share as PDF")
            AlertDialog.Builder(this)
                .setTitle("Share Business Card")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> shareAsImage()
                        1 -> shareAsPdfWithLinks()
                    }
                    dialog.dismiss()
                }
                .show()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Trigger background sync on open (Uploads offline-saved cards, syncs profile, etc.)
        if (NetworkUtils.isOnline(this) && prefs.getBoolean("auto_sync_enabled", true)) {
            SyncManager.syncAll(this)
        }
    }

    private fun shareAsPdfWithLinks() {
        try {
            val container = findViewById<View>(R.id.shareContainer)
            val menuButton = findViewById<ImageButton>(R.id.menuButton)
            val prevMenuVisibility = menuButton.visibility
            menuButton.visibility = View.INVISIBLE

            // Prepare output file name up-front so it's available after rendering
            val displayName = name.text?.toString() ?: ""
            val base = slugify(displayName)
            val outFile = File(cacheDir, "${base}_business_card.pdf")
            if (outFile.exists()) outFile.delete()

            try {
                // Ensure measured
                var w = container.width
                var h = container.height
                if (w == 0 || h == 0) {
                    container.measure(
                        View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    w = container.measuredWidth
                    h = container.measuredHeight
                    container.layout(0, 0, w, h)
                }

                // Render view to bitmap (pixel-perfect)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply {
                    drawColor(Color.WHITE)
                    container.draw(this)
                }

                // Build links
                val phoneStr = phone.text.toString()
                val emailStr = email.text.toString()
                val websiteStr = website.text.toString()
                val addressStr = address.text.toString()
                val instagramStr = instagram.text.toString()

                val telLink = sanitizePhoneForTel(phoneStr)
                val mailLink = emailStr.trim().takeIf { it.isNotBlank() }?.let { "mailto:${it}" }
                val webLink = sanitizeWebsite(websiteStr)
                val mapsLink = buildMapsSearchUrl(addressStr)
                val instaLink = sanitizeInstagram(instagramStr)

                PDDocument().use { doc ->
                    // Page size equals view bitmap size for 1:1 mapping
                    val page = PDPage(PDRectangle(w.toFloat(), h.toFloat()))
                    doc.addPage(page)

                    // Draw bitmap into PDF
                    val imageXObject = LosslessFactory.createFromImage(doc, bmp)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(imageXObject, 0f, 0f, w.toFloat(), h.toFloat())
                    }

                    // Helper: compute rect of a descendant relative to container
                    fun rectInContainer(v: View): android.graphics.Rect {
                        val r = android.graphics.Rect(0, 0, v.width, v.height)
                        (container as ViewGroup).offsetDescendantRectToMyCoords(v, r)
                        return r
                    }

                    // Add link annotation for a row view
                    fun addLinkFor(view: View, uri: String?) {
                        if (uri == null) return
                        if (view.visibility != View.VISIBLE) return
                        val rr = rectInContainer(view)
                        if (rr.width() <= 0 || rr.height() <= 0) return
                        val llx = rr.left.toFloat()
                        val lly = h.toFloat() - rr.bottom.toFloat() // invert Y for PDF coords
                        val urx = rr.right.toFloat()
                        val ury = h.toFloat() - rr.top.toFloat()
                        val annot = PDAnnotationLink().apply {
                            rectangle = PDRectangle(llx, lly, urx - llx, ury - lly)
                            action = PDActionURI().apply { this.uri = uri }
                        }
                        page.annotations.add(annot)
                    }

                    // Target the whole row areas for easier tapping
                    val phoneRow = findViewById<View>(R.id.phoneRow)
                    val emailRow = findViewById<View>(R.id.emailRow)
                    val websiteRow = findViewById<View>(R.id.websiteRow)
                    val addressRow = findViewById<View>(R.id.addressRow)
                    val instagramRow = findViewById<View>(R.id.instagramRow)

                    addLinkFor(phoneRow, telLink)
                    addLinkFor(emailRow, mailLink)
                    addLinkFor(websiteRow, webLink)
                    addLinkFor(addressRow, mapsLink)
                    addLinkFor(instagramRow, instaLink)

                    doc.save(outFile)
                }
            } finally {
                menuButton.visibility = prevMenuVisibility
            }

            // Verify file exists and has content
            if (!outFile.exists() || outFile.length() == 0L) {
                Toast.makeText(this, "PDF not created (empty file)", Toast.LENGTH_SHORT).show()
                Log.e("VisitingCard", "PDF not created or empty at: ${outFile.absolutePath}")
                return
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${name.text} Business Card")
                putExtra(Intent.EXTRA_TITLE, "${name.text} Business Card")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, outFile.name, uri)
            }
            val shareTargets = packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
            shareTargets.forEach { resInfo ->
                grantUriPermission(resInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (shareTargets.isNotEmpty()) {
                try {
                    startActivity(Intent.createChooser(shareIntent, "Share Business Card PDF"))
                } catch (e: Exception) {
                    Log.e("VisitingCard", "Failed to launch share chooser", e)
                    Toast.makeText(this, "No app available to share PDF", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback to direct view intent
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val canView = viewIntent.resolveActivity(packageManager) != null
                if (canView) {
                    try { startActivity(viewIntent) } catch (e: Exception) {
                        Log.e("VisitingCard", "Failed to open PDF viewer", e)
                        Toast.makeText(this, "Unable to open PDF viewer", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No app found to share or open PDF. Install a PDF viewer.", Toast.LENGTH_LONG).show()
                }
            }

            // Optional: quick-open fallback for debugging (comment out if not needed)
            // try {
            //     val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            //         setDataAndType(uri, "application/pdf")
            //         addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            //     }
            //     if (viewIntent.resolveActivity(packageManager) != null) startActivity(viewIntent)
            // } catch (e: Exception) {
            //     Log.w("VisitingCard", "No app to view PDF directly", e)
            // }
        } catch (e: Exception) {
            Log.e("VisitingCard", "shareAsPdfWithLinks error", e)
            Toast.makeText(this, "Failed to share PDF: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Removed legacy WebView print helper; now using PdfBox-Android for clickable links

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val filename = "QR_${System.currentTimeMillis()}.png"
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VisitingCard")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                Toast.makeText(this, "Unable to create MediaStore entry", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw RuntimeException("Bitmap compress failed")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("VisitingCard", "Save to gallery failed", e)
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAsImage() {
        try {
            val shareContainer = findViewById<View>(R.id.shareContainer)
            val menuButton = findViewById<ImageButton>(R.id.menuButton)
            val prevMenuVisibility = menuButton.visibility
            menuButton.visibility = View.INVISIBLE

            val bitmap = Bitmap.createBitmap(shareContainer.width, shareContainer.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            shareContainer.draw(canvas)

            menuButton.visibility = prevMenuVisibility

            val displayName = name.text?.toString() ?: ""
            val base = slugify(displayName)
            val file = File(cacheDir, "${base}_business_card.jpg")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${name.text} Business Card")
                putExtra(Intent.EXTRA_TITLE, "${name.text} Business Card")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, file.name, uri)
            }
            packageManager.queryIntentActivities(shareIntent, 0).forEach { resInfo ->
                grantUriPermission(
                    resInfo.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Business Card"))
        } catch (e: Exception) {
            Log.e("VisitingCard", "Share as image failed", e)
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    

    private fun signOut() {
        // Sign out from Firebase
        Firebase.auth.signOut()

        // Also sign out from Google to clear the default selected account
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleClient = GoogleSignIn.getClient(this, gso)

        googleClient.signOut().addOnCompleteListener {
            // If you want to force full disconnect, you can also revoke access:
            // googleClient.revokeAccess().addOnCompleteListener { ... }
            startActivity(Intent(this, LoginUI::class.java))
            finish()
        }
    }
}
