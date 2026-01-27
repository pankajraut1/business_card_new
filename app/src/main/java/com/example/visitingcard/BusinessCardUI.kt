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
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.core.graphics.drawable.DrawableCompat
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.Drawable

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

    private var currentTheme: UiTheme? = null

    private data class UiTheme(
        val id: String,
        val title: String,
        val headerColor: Int,
        val pageBgColor: Int,
        val pillBgColor: Int,
        val pillTextColor: Int,
        val bottomBarBgColor: Int,
        val bottomBarTextColor: Int,
        val actionBgColor: Int,
        val actionIconTintColor: Int
    )

    private val themes: List<UiTheme> by lazy {
        listOf(
            UiTheme(
                id = "classic_red",
                title = "Classic Red",
                headerColor = Color.parseColor("#A82120"),
                pageBgColor = Color.parseColor("#F5F7FA"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#1A1A1A"),
                bottomBarBgColor = Color.parseColor("#D0D5D9"),
                bottomBarTextColor = Color.BLACK,
                actionBgColor = Color.parseColor("#A82120"),
                actionIconTintColor = Color.WHITE
            ),
            UiTheme(
                id = "ocean_blue",
                title = "Ocean Blue",
                headerColor = Color.parseColor("#1565C0"),
                pageBgColor = Color.parseColor("#F5F7FA"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#102027"),
                bottomBarBgColor = Color.parseColor("#D6E4F0"),
                bottomBarTextColor = Color.BLACK,
                actionBgColor = Color.parseColor("#1565C0"),
                actionIconTintColor = Color.WHITE
            ),
            UiTheme(
                id = "forest_green",
                title = "Forest Green",
                headerColor = Color.parseColor("#2E7D32"),
                pageBgColor = Color.parseColor("#F5F7FA"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#102027"),
                bottomBarBgColor = Color.parseColor("#D7E6D8"),
                bottomBarTextColor = Color.BLACK,
                actionBgColor = Color.parseColor("#2E7D32"),
                actionIconTintColor = Color.WHITE
            ),
            UiTheme(
                id = "midnight",
                title = "Midnight",
                headerColor = Color.parseColor("#263238"),
                pageBgColor = Color.parseColor("#ECEFF1"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#102027"),
                bottomBarBgColor = Color.parseColor("#C7D0D6"),
                bottomBarTextColor = Color.BLACK,
                actionBgColor = Color.parseColor("#263238"),
                actionIconTintColor = Color.WHITE
            )
        )
    }

    private data class UiPrefs(
        val showContactIcons: Boolean,
        val compactSpacing: Boolean,
        val textSizeSp: Float,
        val boldText: Boolean,
        val showBottomLabels: Boolean,
        val showWebsiteRow: Boolean,
        val showInstagramRow: Boolean,
        val pillRadiusDp: Float,
        val pillBorder: Boolean
    )

    private fun getUiPrefs(): UiPrefs {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val showIcons = prefs.getBoolean("bc_show_icons", true)
        val compact = prefs.getBoolean("bc_compact", false)
        val textSize = prefs.getFloat("bc_text_size_sp", 16f)
        val bold = prefs.getBoolean("bc_text_bold", false)
        val showLabels = prefs.getBoolean("bc_show_bottom_labels", true)
        val showWebsite = prefs.getBoolean("bc_show_website", true)
        val showInstagram = prefs.getBoolean("bc_show_instagram", true)
        val radius = prefs.getFloat("bc_pill_radius_dp", 24f)
        val border = prefs.getBoolean("bc_pill_border", false)
        return UiPrefs(
            showContactIcons = showIcons,
            compactSpacing = compact,
            textSizeSp = textSize,
            boldText = bold,
            showBottomLabels = showLabels,
            showWebsiteRow = showWebsite,
            showInstagramRow = showInstagram,
            pillRadiusDp = radius,
            pillBorder = border
        )
    }

    private fun setUiPrefs(newPrefs: UiPrefs) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("bc_show_icons", newPrefs.showContactIcons)
            .putBoolean("bc_compact", newPrefs.compactSpacing)
            .putFloat("bc_text_size_sp", newPrefs.textSizeSp)
            .putBoolean("bc_text_bold", newPrefs.boldText)
            .putBoolean("bc_show_bottom_labels", newPrefs.showBottomLabels)
            .putBoolean("bc_show_website", newPrefs.showWebsiteRow)
            .putBoolean("bc_show_instagram", newPrefs.showInstagramRow)
            .putFloat("bc_pill_radius_dp", newPrefs.pillRadiusDp)
            .putBoolean("bc_pill_border", newPrefs.pillBorder)
            .apply()
    }

    private fun getSelectedThemeId(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getString("business_theme", "classic_red") ?: "classic_red"
    }

    private fun setSelectedThemeId(id: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("business_theme", id).apply()
    }

    private fun applyTheme(theme: UiTheme) {
        currentTheme = theme
        findViewById<View?>(R.id.mainRoot)?.setBackgroundColor(theme.pageBgColor)

        findViewById<View?>(R.id.headerContainer)?.background?.mutate()?.let { bg ->
            DrawableCompat.setTint(bg, theme.headerColor)
        }

        val uiPrefs = getUiPrefs()
        listOf(
            R.id.phoneRow,
            R.id.addressRow,
            R.id.emailRow,
            R.id.websiteRow,
            R.id.instagramRow
        ).forEach { id ->
            val row = findViewById<View?>(id) ?: return@forEach
            row.background = createPillDrawable(
                bgColor = theme.pillBgColor,
                radiusDp = uiPrefs.pillRadiusDp,
                border = uiPrefs.pillBorder
            )
        }

        listOf(
            R.id.phoneno,
            R.id.address,
            R.id.email,
            R.id.website,
            R.id.instagram
        ).forEach { id ->
            (findViewById<View?>(id) as? TextView)?.setTextColor(theme.pillTextColor)
        }

        findViewById<View?>(R.id.bottomActionBar)?.setBackgroundColor(theme.bottomBarBgColor)

        val actionButtonIds = listOf(
            R.id.btnQr,
            R.id.scanQrBtn,
            R.id.viewSavedCardsBtn,
            R.id.shareImageBtn
        )
        actionButtonIds.forEach { id ->
            val btn = findViewById<View?>(id) as? ImageButton ?: return@forEach
            btn.background = createActionRippleDrawable(theme.actionBgColor)
            btn.backgroundTintList = null
            btn.imageTintList = ColorStateList.valueOf(theme.actionIconTintColor)
        }

        val bottomBar = findViewById<View?>(R.id.bottomActionBar) as? ViewGroup
        if (bottomBar != null) {
            for (i in 0 until bottomBar.childCount) {
                val col = bottomBar.getChildAt(i) as? ViewGroup ?: continue
                for (j in 0 until col.childCount) {
                    val v = col.getChildAt(j)
                    if (v is TextView) v.setTextColor(theme.bottomBarTextColor)
                }
            }
        }
    }

    private fun createActionRippleDrawable(solidColor: Int): Drawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(solidColor)
        }
        val ripple = derivePressedColor(solidColor)
        return RippleDrawable(ColorStateList.valueOf(ripple), base, null)
    }

    private fun derivePressedColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = 70
        val dr = (r * 0.75f).toInt().coerceIn(0, 255)
        val dg = (g * 0.75f).toInt().coerceIn(0, 255)
        val db = (b * 0.75f).toInt().coerceIn(0, 255)
        return Color.argb(a, dr, dg, db)
    }

    private fun applyCustomization(prefs: UiPrefs) {
        val iconIds = listOf(
            R.id.phoneIcon,
            R.id.addressIcon,
            R.id.emailIcon,
            R.id.websiteIcon,
            R.id.instagramIcon
        )

        iconIds.forEach { id ->
            findViewById<View?>(id)?.visibility = if (prefs.showContactIcons) View.VISIBLE else View.GONE
        }

        val labelIds = listOf(
            R.id.labelQr,
            R.id.labelScan,
            R.id.labelSaved,
            R.id.labelShare
        )
        labelIds.forEach { id ->
            findViewById<View?>(id)?.visibility = if (prefs.showBottomLabels) View.VISIBLE else View.GONE
        }

        findViewById<View?>(R.id.websiteRow)?.visibility = if (prefs.showWebsiteRow) View.VISIBLE else View.GONE
        findViewById<View?>(R.id.instagramRow)?.visibility = if (prefs.showInstagramRow) View.VISIBLE else View.GONE

        val baseStyle = if (prefs.boldText) Typeface.BOLD else Typeface.NORMAL

        // Keep name larger than other fields so it doesn't become too small.
        val nameSizeSp = (prefs.textSizeSp + 8f).coerceAtLeast(22f)
        val occupationSizeSp = (prefs.textSizeSp + 2f).coerceAtLeast(16f)

        (findViewById<View?>(R.id.nameText) as? TextView)?.let { tv ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, nameSizeSp)
            tv.typeface = Typeface.create(tv.typeface, baseStyle)
        }
        (findViewById<View?>(R.id.occupation) as? TextView)?.let { tv ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, occupationSizeSp)
            tv.typeface = Typeface.create(tv.typeface, baseStyle)
        }

        val detailTextIds = listOf(
            R.id.phoneno,
            R.id.address,
            R.id.email,
            R.id.website,
            R.id.instagram
        )
        detailTextIds.forEach { id ->
            (findViewById<View?>(id) as? TextView)?.let { tv ->
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.textSizeSp)
                tv.typeface = Typeface.create(tv.typeface, baseStyle)
            }
        }

        val marginTop = if (prefs.compactSpacing) 16 else 36
        val orderedRows = listOf(
            R.id.phoneRow,
            R.id.addressRow,
            R.id.emailRow,
            R.id.websiteRow,
            R.id.instagramRow
        )
        val visibleRows = orderedRows.mapNotNull { id ->
            val v = findViewById<View?>(id)
            if (v != null && v.visibility != View.GONE) v else null
        }

        visibleRows.forEachIndexed { idx, row ->
            val lp = row.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.topMargin = if (idx == 0) 0 else marginTop
                row.layoutParams = lp
            }
        }

        currentTheme?.let { theme ->
            visibleRows.forEach { row ->
                row.background = createPillDrawable(
                    bgColor = theme.pillBgColor,
                    radiusDp = prefs.pillRadiusDp,
                    border = prefs.pillBorder
                )
            }
        }
    }

    private fun createPillDrawable(bgColor: Int, radiusDp: Float, border: Boolean): Drawable {
        val rPx = dpToPx(radiusDp)
        val strokePx = if (border) dpToPx(1f) else 0f

        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = rPx
        d.setColor(bgColor)
        if (strokePx > 0f) {
            val strokeWidth: Int = strokePx.toInt()
            val strokeColor: Int = Color.parseColor("#B0B0B0")
            d.setStroke(strokeWidth, strokeColor)
        }
        return d
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun chooseExportCardBg(): Int {
        val pageBg = currentTheme?.pageBgColor ?: Color.parseColor("#F5F7FA")
        return if (luminance(pageBg) > 0.90f) Color.parseColor("#F5F7FA") else pageBg
    }

    private fun chooseExportPillBg(): Int {
        val pillBg = currentTheme?.pillBgColor ?: Color.WHITE
        return if (luminance(pillBg) > 0.92f) Color.parseColor("#EEF2F5") else pillBg
    }

    private fun createExportCardBackground(bgColor: Int): Drawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dpToPx(16f)
        d.setColor(bgColor)
        d.setStroke(dpToPx(1f).toInt(), Color.parseColor("#D0D5D9"))
        return d
    }

    private fun createExportPillDrawable(bgColor: Int): Drawable {
        val uiPrefs = getUiPrefs()
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dpToPx(uiPrefs.pillRadiusDp)
        d.setColor(bgColor)
        d.setStroke(dpToPx(1f).toInt(), Color.parseColor("#D0D5D9"))
        return d
    }

    private data class ExportBgSnapshot(
        val cardBg: Drawable?,
        val cardContentBg: Drawable?,
        val rowBgs: Map<Int, Drawable?>
    )

    private fun applyExportStylingForBitmap(): ExportBgSnapshot {
        val card = findViewById<View?>(R.id.card)
        val cardContent = findViewById<View?>(R.id.cardContent)

        val rowIds = listOf(
            R.id.phoneRow,
            R.id.addressRow,
            R.id.emailRow,
            R.id.websiteRow,
            R.id.instagramRow
        )
        val rowBgs = rowIds.associateWith { id -> findViewById<View?>(id)?.background }

        val snapshot = ExportBgSnapshot(
            cardBg = card?.background,
            cardContentBg = cardContent?.background,
            rowBgs = rowBgs
        )

        // IMPORTANT: The card container overlaps the header (negative margin). During export,
        // force its background to transparent so it cannot cover name/occupation.
        card?.background = null

        val exportPillBg = chooseExportPillBg()
        val exportPillDrawable = createExportPillDrawable(exportPillBg)
        rowIds.forEach { id ->
            val v = findViewById<View?>(id) ?: return@forEach
            if (v.visibility == View.VISIBLE) {
                v.background = exportPillDrawable.constantState?.newDrawable()?.mutate() ?: exportPillDrawable
            }
        }

        // Ensure inner content doesn't introduce extra fills.
        cardContent?.setBackgroundColor(Color.TRANSPARENT)

        return snapshot
    }

    private data class ExportBitmapResult(
        val bitmap: Bitmap,
        val contentWidth: Int,
        val contentHeight: Int,
        val waveHeight: Int
    )

    private fun renderExportBitmap(
        backgroundColor: Int,
        clearBeforeDraw: Boolean
    ): ExportBitmapResult {
        val shareContainer = findViewById<View>(R.id.shareContainer)
        val wave = findViewById<View>(R.id.bottomWaveDivider)

        // Ensure shareContainer measured
        var w = shareContainer.width
        var h = shareContainer.height
        if (w == 0 || h == 0) {
            shareContainer.measure(
                View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            w = shareContainer.measuredWidth
            h = shareContainer.measuredHeight
            shareContainer.layout(0, 0, w, h)
        }

        // Ensure wave measured with same width
        var waveH = wave.height
        if (waveH == 0) {
            wave.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            waveH = wave.measuredHeight
            wave.layout(0, 0, w, waveH)
        }

        val totalH = h + waveH
        val bmp = Bitmap.createBitmap(w, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (clearBeforeDraw) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        canvas.drawColor(backgroundColor)
        shareContainer.draw(canvas)
        canvas.save()
        canvas.translate(0f, h.toFloat())
        wave.draw(canvas)
        canvas.restore()

        return ExportBitmapResult(
            bitmap = bmp,
            contentWidth = w,
            contentHeight = h,
            waveHeight = waveH
        )
    }

    private fun restoreAfterExport(snapshot: ExportBgSnapshot) {
        findViewById<View?>(R.id.card)?.background = snapshot.cardBg
        findViewById<View?>(R.id.cardContent)?.background = snapshot.cardContentBg
        snapshot.rowBgs.forEach { (id, bg) ->
            findViewById<View?>(id)?.background = bg
        }
    }

    private fun showCustomizeDialog() {
        val current = getUiPrefs()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val showIconsToggle = CheckBox(this).apply {
            text = "Show contact icons"
            isChecked = current.showContactIcons
        }
        container.addView(showIconsToggle)

        val compactToggle = CheckBox(this).apply {
            text = "Compact spacing"
            isChecked = current.compactSpacing
        }
        container.addView(compactToggle)

        val boldToggle = CheckBox(this).apply {
            text = "Bold text"
            isChecked = current.boldText
        }
        container.addView(boldToggle)

        val showLabelsToggle = CheckBox(this).apply {
            text = "Show bottom action labels"
            isChecked = current.showBottomLabels
        }
        container.addView(showLabelsToggle)

        val showWebsiteToggle = CheckBox(this).apply {
            text = "Show website"
            isChecked = current.showWebsiteRow
        }
        container.addView(showWebsiteToggle)

        val showInstagramToggle = CheckBox(this).apply {
            text = "Show instagram"
            isChecked = current.showInstagramRow
        }
        container.addView(showInstagramToggle)

        val borderToggle = CheckBox(this).apply {
            text = "Pill border"
            isChecked = current.pillBorder
        }
        container.addView(borderToggle)

        val radiusLabel = TextView(this).apply {
            text = "Pill corner radius"
            setPadding(0, 24, 0, 8)
        }
        container.addView(radiusLabel)

        val radiusOptions = arrayOf("Small", "Medium", "Large")
        val radiusValues = floatArrayOf(12f, 24f, 32f)
        val radiusPre = when {
            current.pillRadiusDp <= 14f -> 0
            current.pillRadiusDp >= 30f -> 2
            else -> 1
        }
        val radiusRadio = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        radiusOptions.forEachIndexed { idx, label ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
            }
            radiusRadio.addView(rb)
            if (idx == radiusPre) radiusRadio.check(rb.id)
        }
        container.addView(radiusRadio)

        val sizeLabel = TextView(this).apply {
            text = "Text size"
            setPadding(0, 24, 0, 8)
        }
        container.addView(sizeLabel)

        val sizes = arrayOf("Small", "Normal", "Large")
        val sizeValues = floatArrayOf(14f, 16f, 18f)
        val pre = when {
            current.textSizeSp <= 14.5f -> 0
            current.textSizeSp >= 17.5f -> 2
            else -> 1
        }

        val radio = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        sizes.forEachIndexed { idx, label ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
            }
            radio.addView(rb)
            if (idx == pre) radio.check(rb.id)
        }
        container.addView(radio)

        AlertDialog.Builder(this)
            .setTitle("Customize Card")
            .setView(container)
            .setPositiveButton("Apply") { dialog, _ ->
                val checkedIndex = (0 until radio.childCount)
                    .firstOrNull { (radio.getChildAt(it) as? RadioButton)?.isChecked == true }
                    ?: pre

                val radiusCheckedIndex = (0 until radiusRadio.childCount)
                    .firstOrNull { (radiusRadio.getChildAt(it) as? RadioButton)?.isChecked == true }
                    ?: radiusPre

                val updated = UiPrefs(
                    showContactIcons = showIconsToggle.isChecked,
                    compactSpacing = compactToggle.isChecked,
                    textSizeSp = sizeValues.getOrElse(checkedIndex) { 16f },
                    boldText = boldToggle.isChecked,
                    showBottomLabels = showLabelsToggle.isChecked,
                    showWebsiteRow = showWebsiteToggle.isChecked,
                    showInstagramRow = showInstagramToggle.isChecked,
                    pillRadiusDp = radiusValues.getOrElse(radiusCheckedIndex) { 24f },
                    pillBorder = borderToggle.isChecked
                )
                setUiPrefs(updated)
                applyCustomization(updated)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemePicker() {
        val currentId = getSelectedThemeId()
        val labels = themes.map { it.title }.toTypedArray()
        val preselect = themes.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(labels, preselect) { dialog, which ->
                val theme = themes.getOrNull(which) ?: return@setSingleChoiceItems
                setSelectedThemeId(theme.id)
                applyTheme(theme)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val user = auth.currentUser
        if (uri == null || user == null) return@registerForActivityResult
        coroutineScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    BackupRestoreManager.writeBackupToUri(
                        context = this@BusinessCardUI,
                        userId = user.uid,
                        outUri = uri
                    )
                }
                Toast.makeText(this@BusinessCardUI, "Backup created ($count cards)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BusinessCardUI, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val restoreBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val user = auth.currentUser
        if (uri == null || user == null) return@registerForActivityResult
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupRestoreManager.restoreFromBackupUri(
                        context = this@BusinessCardUI,
                        currentUserId = user.uid,
                        inUri = uri
                    )
                }
                Toast.makeText(
                    this@BusinessCardUI,
                    "Restore done: ${result.restoredCount} added, ${result.skippedCount} skipped",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: SecurityException) {
                Toast.makeText(this@BusinessCardUI, e.message ?: "This backup belongs to a different user", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@BusinessCardUI, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        themes.firstOrNull { it.id == getSelectedThemeId() }?.let { applyTheme(it) }
        applyCustomization(getUiPrefs())

        navigationView.menu.findItem(R.id.nav_delete_account)?.let { delItem ->
            val s = SpannableString(delItem.title)
            s.setSpan(ForegroundColorSpan(Color.parseColor("#D32F2F")), 0, s.length, 0)
            delItem.title = s
        }

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
                R.id.nav_delete_account -> {
                    confirmAndDeleteAccount()
                }
                R.id.nav_theme -> {
                    showThemePicker()
                }
                R.id.nav_customize -> {
                    showCustomizeDialog()
                }
                R.id.nav_auto_sync -> {
                    val newVal = !item.isChecked
                    item.isChecked = newVal
                    prefs.edit().putBoolean("auto_sync_enabled", newVal).apply()
                    Toast.makeText(this, if (newVal) "Auto Sync ON" else "Auto Sync OFF", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_backup_cards -> {
                    val user = auth.currentUser
                    if (user == null) {
                        Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
                    } else {
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val fileName = "visiting_cards_backup_${sdf.format(Date())}.json"
                        createBackupLauncher.launch(fileName)
                    }
                }
                R.id.nav_restore_cards -> {
                    val user = auth.currentUser
                    if (user == null) {
                        Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
                    } else {
                        restoreBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                }
                R.id.nav_credit -> {
                    Toast.makeText(this, getString(R.string.copyright), Toast.LENGTH_LONG).show()
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
            val tel = sanitizePhoneForTel(phone.text.toString())
            if (tel != null) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(tel)))
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
            val options = arrayOf("Share as Image (JPG)", "Share as Image (PNG)", "Share as PDF")
            AlertDialog.Builder(this)
                .setTitle("Share Business Card")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> shareAsImageJpg()
                        1 -> shareAsImagePng()
                        2 -> shareAsPdfWithLinks()
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

            var exportSnapshot: ExportBgSnapshot? = null

            // Prepare output file name up-front so it's available after rendering
            val displayName = name.text?.toString() ?: ""
            val base = slugify(displayName)
            val outFile = File(cacheDir, "${base}_business_card.pdf")
            if (outFile.exists()) outFile.delete()

            try {
                exportSnapshot = applyExportStylingForBitmap()

                // Render shareContainer + bottom wave into a single bitmap
                val render = renderExportBitmap(backgroundColor = Color.WHITE, clearBeforeDraw = false)
                val bmp = render.bitmap
                val w = render.contentWidth
                val contentH = render.contentHeight
                val totalH = contentH + render.waveHeight

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
                    val page = PDPage(PDRectangle(w.toFloat(), totalH.toFloat()))
                    doc.addPage(page)

                    // Draw bitmap into PDF
                    val imageXObject = LosslessFactory.createFromImage(doc, bmp)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(imageXObject, 0f, 0f, w.toFloat(), totalH.toFloat())
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
                        val lly = totalH.toFloat() - rr.bottom.toFloat() // invert Y for PDF coords
                        val urx = rr.right.toFloat()
                        val ury = totalH.toFloat() - rr.top.toFloat()
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
                exportSnapshot?.let { restoreAfterExport(it) }
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

    private fun shareAsImageJpg() {
        try {
            val shareContainer = findViewById<View>(R.id.shareContainer)
            val menuButton = findViewById<ImageButton>(R.id.menuButton)
            val prevMenuVisibility = menuButton.visibility
            menuButton.visibility = View.INVISIBLE

            var exportSnapshot: ExportBgSnapshot? = null

            val bitmap = try {
                exportSnapshot = applyExportStylingForBitmap()
                renderExportBitmap(backgroundColor = Color.WHITE, clearBeforeDraw = false).bitmap
            } finally {
                exportSnapshot?.let { restoreAfterExport(it) }
                menuButton.visibility = prevMenuVisibility
            }

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

    private fun shareAsImagePng() {
        try {
            val shareContainer = findViewById<View>(R.id.shareContainer)
            val menuButton = findViewById<ImageButton>(R.id.menuButton)
            val prevMenuVisibility = menuButton.visibility
            menuButton.visibility = View.INVISIBLE

            var exportSnapshot: ExportBgSnapshot? = null

            val bitmap = try {
                exportSnapshot = applyExportStylingForBitmap()
                // Keep transparency, but still render the gray bottom wave
                renderExportBitmap(backgroundColor = Color.TRANSPARENT, clearBeforeDraw = true).bitmap
            } finally {
                exportSnapshot?.let { restoreAfterExport(it) }
                menuButton.visibility = prevMenuVisibility
            }

            val displayName = name.text?.toString() ?: ""
            val base = slugify(displayName)
            val file = File(cacheDir, "${base}_business_card.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
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
            Log.e("VisitingCard", "Share as PNG failed", e)
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    

    private fun signOut() {
        // Sign out from Firebase
        Firebase.auth.signOut()

        // Clear local cached profile so it can't bleed into a different account
        ProfileStorage(this).clearAll()

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

    private fun confirmAndDeleteAccount() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage(
                "This will permanently delete your account and all saved data. This action cannot be undone.\n\nDo you want to continue?"
            )
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .setPositiveButton("Delete") { d, _ ->
                d.dismiss()
                deleteAccountNow()
            }
            .show()
    }

    private fun deleteAccountNow() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid

        // 1) Remove user's data from Realtime Database
        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .removeValue()
            .addOnCompleteListener {
                // 2) Clear local data (so even if delete() fails, we don't keep old data)
                ProfileStorage(this).clearAll()
                try {
                    CardStorageHelper(this).clearUserCards(uid)
                } catch (_: Exception) {
                }

                // 3) Delete Firebase Auth user
                user.delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginUI::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        if (e is FirebaseAuthRecentLoginRequiredException) {
                            Toast.makeText(
                                this,
                                "Please login again to delete your account, then try again.",
                                Toast.LENGTH_LONG
                            ).show()
                            signOut()
                        } else {
                            Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
    }
}
