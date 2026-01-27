package com.example.visitingcard

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SavedCardsUI : AppCompatActivity() {
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private lateinit var cardStorageHelper: CardStorageHelper
    private lateinit var container: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var tagChipGroup: ChipGroup
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val FOOTER_TAG = "footer_note"
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val displayKeySet = mutableSetOf<String>()

    private var currentTheme: UiTheme? = null

    private data class UiTheme(
        val id: String,
        val headerColor: Int,
        val pageBgColor: Int,
        val pillBgColor: Int,
        val pillTextColor: Int
    )

    private val themes: List<UiTheme> by lazy {
        listOf(
            UiTheme(
                id = "classic_red",
                headerColor = Color.parseColor("#A82120"),
                pageBgColor = Color.parseColor("#F5F7FA"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#1A1A1A")
            ),
            UiTheme(
                id = "ocean_blue",
                headerColor = Color.parseColor("#1565C0"),
                pageBgColor = Color.parseColor("#F5F7FA"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#102027")
            ),
            UiTheme(
                id = "forest_green",
                headerColor = Color.parseColor("#2E7D32"),
                pageBgColor = Color.parseColor("#F5F7FA"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#102027")
            ),
            UiTheme(
                id = "midnight",
                headerColor = Color.parseColor("#263238"),
                pageBgColor = Color.parseColor("#ECEFF1"),
                pillBgColor = Color.WHITE,
                pillTextColor = Color.parseColor("#102027")
            )
        )
    }

    private fun getSelectedThemeId(): String {
        return prefs.getString("business_theme", "classic_red") ?: "classic_red"
    }

    private fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun ensureContrastingPillBg(pillBg: Int, pageBg: Int): Int {
        // If colors are too close (e.g., white on near-white), use a light gray pill bg.
        val diff = kotlin.math.abs(luminance(pillBg) - luminance(pageBg))
        return if (diff < 0.12f) Color.parseColor("#E6EAEE") else pillBg
    }

    private fun createCardContainerDrawable(): GradientDrawable {
        val radius = 16.dpToPx().toFloat()
        val strokeWidth = 1.dpToPx()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
            setStroke(strokeWidth, Color.parseColor("#D0D5D9"))
        }
    }

    private fun applyTheme() {
        val base = themes.firstOrNull { it.id == getSelectedThemeId() } ?: themes.first()
        val adjustedPillBg = ensureContrastingPillBg(base.pillBgColor, base.pageBgColor)
        val theme = base.copy(pillBgColor = adjustedPillBg)
        currentTheme = theme

        findViewById<View?>(R.id.savedCardsRoot)?.setBackgroundColor(theme.pageBgColor)

        findViewById<Toolbar?>(R.id.toolbar)?.setBackgroundColor(theme.headerColor)

        // Keep the filters readable and consistent
        findViewById<View?>(R.id.filterBar)?.setBackgroundColor(theme.pageBgColor)

        findViewById<EditText?>(R.id.searchEditText)?.let { et ->
            et.background?.mutate()?.let { bg ->
                DrawableCompat.setTint(bg, theme.pillBgColor)
            }
            et.setTextColor(theme.pillTextColor)
            et.setHintTextColor(Color.parseColor("#6B7280"))
        }

        // Rebuild chips so their colors follow the selected theme
        updateTagFilterOptions(allCards)
    }

    private val defaultTags = listOf("", "Lead", "Client", "Vendor", "Friend")
    private var activeSearchQuery: String = ""
    private var activeTagFilter: String = "ALL"
    private var allCards: List<Map<String, Any?>> = emptyList()

    private enum class SortMode {
        RECENT,
        NAME_AZ
    }

    private var sortMode: SortMode = SortMode.RECENT

    private fun computeCardKey(card: Map<String, Any?>): String {
        val name = (card[CardStorageHelper.KEY_NAME] ?: "").toString().trim()
        val occ = (card[CardStorageHelper.KEY_OCCUPATION] ?: "").toString().trim()
        val email = (card[CardStorageHelper.KEY_EMAIL] ?: "").toString().trim()
        val phone = (card[CardStorageHelper.KEY_PHONE] ?: "").toString().trim()
        val insta = (card[CardStorageHelper.KEY_INSTAGRAM] ?: "").toString().trim()
        val web = (card[CardStorageHelper.KEY_WEBSITE] ?: "").toString().trim()
        val addr = (card[CardStorageHelper.KEY_ADDRESS] ?: "").toString().trim()
        return listOf(name, occ, email, phone, insta, web, addr).joinToString("|")
    }

    private fun sanitizePhoneForTel(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val cleaned = buildString {
            var i = 0
            if (trimmed.startsWith("+")) {
                append('+')
                i = 1
            }
            while (i < trimmed.length) {
                val ch = trimmed[i]
                if (ch.isDigit()) append(ch)
                i++
            }
        }
        return if (cleaned.any { it.isDigit() }) "tel:$cleaned" else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.saved_cards_ui)
        
        // Set toolbar as support action bar so menu items appear on top-right
        findViewById<Toolbar>(R.id.toolbar)?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.title = "Saved Cards"
        }

        // Initialize views
        container = findViewById(R.id.savedCardsContainer)
        searchEditText = findViewById(R.id.searchEditText)
        tagChipGroup = findViewById(R.id.tagChipGroup)
        
        // Firebase Auth is initialized via lazy delegate
        cardStorageHelper = CardStorageHelper(this)
        
        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not signed in, redirect to login
            startActivity(Intent(this, LoginUI::class.java))
            finish()
            return
        }
        
        // Always show local cards first to avoid any white screen
        loadLocalCards(currentUser.uid)
        // Then, if online and auto-sync enabled, attach cloud listener to refresh
        if (NetworkUtils.isOnline(this) && prefs.getBoolean("auto_sync_enabled", true)) {
            loadUserCards(currentUser.uid)
        }

        setupFilters()

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        // If the user changed theme elsewhere, re-apply when returning
        applyTheme()
        renderFiltered()
    }

    private fun setupFilters() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeSearchQuery = s?.toString()?.trim() ?: ""
                renderFiltered()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.saved_cards_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortPicker()
                true
            }
            R.id.action_sync_now -> {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    refreshNow(uid)
                } else {
                    showError("User not signed in")
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortPicker() {
        val options = arrayOf("Recent", "Name (A–Z)")
        val pre = when (sortMode) {
            SortMode.RECENT -> 0
            SortMode.NAME_AZ -> 1
        }
        AlertDialog.Builder(this)
            .setTitle("Sort")
            .setSingleChoiceItems(options, pre) { dialog, which ->
                sortMode = when (which) {
                    1 -> SortMode.NAME_AZ
                    else -> SortMode.RECENT
                }
                renderFiltered()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshNow(userId: String) {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "Offline – showing local cards", Toast.LENGTH_SHORT).show()
            loadLocalCards(userId)
            return
        }

        Toast.makeText(this, "Syncing…", Toast.LENGTH_SHORT).show()

        val dbRef = Firebase.database.reference
            .child("users")
            .child(userId)
            .child("cards")

        dbRef.get()
            .addOnSuccessListener { snapshot ->
                val cloudCards = mutableListOf<Map<String, Any?>>()
                snapshot.children.forEach { child ->
                    val map = hashMapOf<String, Any?>()
                    map[CardStorageHelper.KEY_NAME] = child.child(CardStorageHelper.KEY_NAME).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_OCCUPATION] = child.child(CardStorageHelper.KEY_OCCUPATION).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_EMAIL] = child.child(CardStorageHelper.KEY_EMAIL).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_PHONE] = child.child(CardStorageHelper.KEY_PHONE).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_INSTAGRAM] = child.child(CardStorageHelper.KEY_INSTAGRAM).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_WEBSITE] = child.child(CardStorageHelper.KEY_WEBSITE).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_ADDRESS] = child.child(CardStorageHelper.KEY_ADDRESS).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_TAG] = child.child(CardStorageHelper.KEY_TAG).getValue(String::class.java) ?: ""
                    map["createdAt"] = child.child("createdAt").getValue(String::class.java) ?: ""
                    map["__fbKey"] = child.key
                    cloudCards.add(map)
                }

                if (cloudCards.isNotEmpty()) {
                    cloudCards.sortByDescending { (it["createdAt"] as? String) ?: "" }
                }

                allCards = cloudCards
                updateTagFilterOptions(allCards)
                renderFiltered()

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        cloudCards.forEach { card ->
                            val name = (card[CardStorageHelper.KEY_NAME] as? String) ?: ""
                            val occ = (card[CardStorageHelper.KEY_OCCUPATION] as? String) ?: ""
                            val email = (card[CardStorageHelper.KEY_EMAIL] as? String) ?: ""
                            val phone = (card[CardStorageHelper.KEY_PHONE] as? String) ?: ""
                            val insta = (card[CardStorageHelper.KEY_INSTAGRAM] as? String) ?: ""
                            val web = (card[CardStorageHelper.KEY_WEBSITE] as? String) ?: ""
                            val addr = (card[CardStorageHelper.KEY_ADDRESS] as? String) ?: ""
                            val tag = (card[CardStorageHelper.KEY_TAG] as? String) ?: ""
                            if (!cardStorageHelper.existsCard(userId, name, occ, email, phone, insta, web, addr)) {
                                cardStorageHelper.insertCardWithTag(userId, name, occ, email, phone, insta, web, addr, tag)
                            }
                        }
                    } catch (_: Exception) { }
                }

                Toast.makeText(this, "Synced", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                showError("Sync failed: ${e.message}")
                // fallback to local
                loadLocalCards(userId)
            }
    }
    
    
    private fun loadUserCards(userId: String) {
        // If offline or auto-sync disabled, only show locally cached
        if (!NetworkUtils.isOnline(this) || !prefs.getBoolean("auto_sync_enabled", true)) {
            loadLocalCards(userId)
            return
        }

        // Use realtime listener so UI stays in sync
        val dbRef = Firebase.database.reference
            .child("users")
            .child(userId)
            .child("cards")

        dbRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val cloudCards = mutableListOf<Map<String, Any?>>()
                snapshot.children.forEach { child ->
                    val map = hashMapOf<String, Any?>()
                    map[CardStorageHelper.KEY_NAME] = child.child(CardStorageHelper.KEY_NAME).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_OCCUPATION] = child.child(CardStorageHelper.KEY_OCCUPATION).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_EMAIL] = child.child(CardStorageHelper.KEY_EMAIL).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_PHONE] = child.child(CardStorageHelper.KEY_PHONE).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_INSTAGRAM] = child.child(CardStorageHelper.KEY_INSTAGRAM).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_WEBSITE] = child.child(CardStorageHelper.KEY_WEBSITE).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_ADDRESS] = child.child(CardStorageHelper.KEY_ADDRESS).getValue(String::class.java) ?: ""
                    map[CardStorageHelper.KEY_TAG] = child.child(CardStorageHelper.KEY_TAG).getValue(String::class.java) ?: ""
                    map["createdAt"] = child.child("createdAt").getValue(String::class.java) ?: ""
                    map["__fbKey"] = child.key
                    cloudCards.add(map)
                }

                if (cloudCards.isNotEmpty()) {
                    cloudCards.sortByDescending { (it["createdAt"] as? String) ?: "" }
                }

                allCards = cloudCards
                updateTagFilterOptions(allCards)
                renderFiltered()

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        cloudCards.forEach { card ->
                            val name = (card[CardStorageHelper.KEY_NAME] as? String) ?: ""
                            val occ = (card[CardStorageHelper.KEY_OCCUPATION] as? String) ?: ""
                            val email = (card[CardStorageHelper.KEY_EMAIL] as? String) ?: ""
                            val phone = (card[CardStorageHelper.KEY_PHONE] as? String) ?: ""
                            val insta = (card[CardStorageHelper.KEY_INSTAGRAM] as? String) ?: ""
                            val web = (card[CardStorageHelper.KEY_WEBSITE] as? String) ?: ""
                            val addr = (card[CardStorageHelper.KEY_ADDRESS] as? String) ?: ""
                            val tag = (card[CardStorageHelper.KEY_TAG] as? String) ?: ""
                            if (!cardStorageHelper.existsCard(userId, name, occ, email, phone, insta, web, addr)) {
                                cardStorageHelper.insertCardWithTag(userId, name, occ, email, phone, insta, web, addr, tag)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // On error, fallback to local DB
                loadLocalCards(userId)
            }
        })
    }
    
    private fun loadLocalCards(userId: String) {
        coroutineScope.launch {
            try {
                val cards = withContext(Dispatchers.IO) {
                    cardStorageHelper.getUserCards(userId)
                }
                val localMapped = cards.map { it as Map<String, Any?> }
                allCards = localMapped
                updateTagFilterOptions(allCards)
                renderFiltered()
            } catch (e: Exception) {
                showError("Failed to load cards: ${e.message}")
            }
        }
    }

    private fun updateTagFilterOptions(cards: List<Map<String, Any?>>) {
        val tagsFromData = cards
            .map { (it[CardStorageHelper.KEY_TAG] ?: "").toString().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val options = mutableListOf<String>()
        options.add("All")
        options.add("Untagged")
        defaultTags.filter { it.isNotBlank() }.forEach { t ->
            if (!options.contains(t)) options.add(t)
        }
        tagsFromData.forEach { t ->
            if (!options.contains(t)) options.add(t)
        }

        buildTagChips(options)
    }

    private fun buildTagChips(options: List<String>) {
        // Keep selection stable
        val selectedLabel = when (activeTagFilter) {
            "ALL" -> "All"
            "UNTAGGED" -> "Untagged"
            else -> activeTagFilter
        }

        tagChipGroup.isSingleSelection = true

        tagChipGroup.setOnCheckedStateChangeListener(null)
        tagChipGroup.removeAllViews()

        val theme = currentTheme
        val chipBg = theme?.headerColor ?: Color.parseColor("#A82120")
        val chipText = Color.WHITE
        val checkedBg = darkenColor(chipBg)

        options.forEach { label ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isClickable = true
                chipBackgroundColor = ColorStateList.valueOf(chipBg)
                setTextColor(chipText)
                checkedIcon = null
                // Make checked state visible by changing background
                setOnCheckedChangeListener { _, isChecked ->
                    chipBackgroundColor = ColorStateList.valueOf(if (isChecked) checkedBg else chipBg)
                }
            }
            tagChipGroup.addView(chip)
            if (label == selectedLabel) chip.isChecked = true
        }

        tagChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            val checkedChip = checkedId?.let { group.findViewById<Chip>(it) }
            val value = checkedChip?.text?.toString() ?: "All"

            activeTagFilter = when (value) {
                "All" -> "ALL"
                "Untagged" -> "UNTAGGED"
                else -> value
            }
            renderFiltered()
        }
    }

    private fun darkenColor(color: Int): Int {
        val r = (Color.red(color) * 0.82f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * 0.82f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * 0.82f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun renderFiltered() {
        val q = activeSearchQuery.trim().lowercase()
        val filtered = allCards.filter { card ->
            val tag = (card[CardStorageHelper.KEY_TAG] ?: "").toString().trim()
            val tagOk = when (activeTagFilter) {
                "ALL" -> true
                "UNTAGGED" -> tag.isBlank()
                else -> tag.equals(activeTagFilter, ignoreCase = true)
            }
            if (!tagOk) return@filter false

            if (q.isBlank()) return@filter true

            // Search by NAME only (as requested)
            val name = (card[CardStorageHelper.KEY_NAME] ?: "").toString().trim().lowercase()
            name.contains(q)
        }

        val sorted = when (sortMode) {
            SortMode.NAME_AZ -> filtered.sortedBy {
                (it[CardStorageHelper.KEY_NAME] ?: "").toString().trim().lowercase()
            }
            SortMode.RECENT -> filtered
        }

        container.removeAllViews()
        displayKeySet.clear()

        if (sorted.isEmpty()) {
            showNoCardsMessage()
            return
        }

        sorted.forEach { card ->
            val key = computeCardKey(card)
            if (displayKeySet.add(key)) {
                createCardView(card)
            }
        }
        addFooterNote()
    }
    
    private fun showNoCardsMessage() {
        val noCardsText = TextView(this).apply {
            text = "No saved cards yet"
            setTextAppearance(android.R.style.TextAppearance_Medium)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32.dpToPx(), 0, 0)
        }
        container.addView(noCardsText)
    }
    
    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun createCardView(card: Map<String, Any?>) {
        val theme = currentTheme

        // Use a FrameLayout so we can overlay the delete icon without consuming vertical space
        val cardFrame = FrameLayout(this).apply {
            background = createCardContainerDrawable()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // Reduced spacing between items
                setMargins(10.dpToPx(), 8.dpToPx(), 10.dpToPx(), 10.dpToPx())
            }
            elevation = 3f
            // Remove padding here so the wave can draw full-bleed to the rounded corners
            setPadding(0, 0, 0, 0)
            // Ensure children (wave header) are clipped to rounded background corners
            clipToOutline = true
        }

        // Add a solid red band under the wave to ensure edge-to-edge coverage into rounded corners
        val headerBand = View(this).apply {
            setBackgroundColor(theme?.headerColor ?: ContextCompat.getColor(this@SavedCardsUI, R.color.headerRed))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                72.dpToPx()
            ).apply { gravity = Gravity.TOP }
        }
        cardFrame.addView(headerBand)

        // Add decorative header wave as background overlay at the top (does not change content size)
        val headerWave = ImageView(this).apply {
            setImageResource(R.drawable.header_wave)
            scaleType = ImageView.ScaleType.FIT_XY
            theme?.let { t -> imageTintList = ColorStateList.valueOf(t.headerColor) }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                72.dpToPx()
            ).apply {
                gravity = Gravity.TOP
            }
        }
        cardFrame.addView(headerWave)

        // Inner vertical container for rows
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Preserve the overall internal spacing by moving previous cardFrame padding here
            // Previous: cardFrame padding (16,8,16,10) + content top 12 and bottom 10
            // Now combined as: left/right 16, top 8+12=20, bottom 10+10=20
            setPadding(16.dpToPx(), 20.dpToPx(), 16.dpToPx(), 20.dpToPx())
        }
        cardFrame.addView(contentLayout)

        // Top-right delete icon overlay (white on red bg)
        val deleteIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            contentDescription = "Delete"
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@SavedCardsUI, R.color.white))
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                val fbKey = card["__fbKey"] as? String
                val userId = auth.currentUser?.uid
                if (fbKey != null && !fbKey.isNullOrBlank() && userId != null) {
                    AlertDialog.Builder(this@SavedCardsUI)
                        .setTitle("Delete Card")
                        .setMessage("Are you sure you want to delete this card? This will remove it locally and from cloud (if available).")
                        .setPositiveButton("Delete") { _, _ ->
                            Firebase.database.reference
                                .child("users")
                                .child(userId)
                                .child("cards")
                                .child(fbKey)
                                .removeValue()
                                .addOnSuccessListener {
                                    Toast.makeText(this@SavedCardsUI, "Deleted", Toast.LENGTH_SHORT).show()
                                    container.removeView(cardFrame)
                                    // Also remove from local cache to avoid reappearing later
                                    val name = (card[CardStorageHelper.KEY_NAME] ?: "").toString()
                                    val occ = (card[CardStorageHelper.KEY_OCCUPATION] ?: "").toString()
                                    val email = (card[CardStorageHelper.KEY_EMAIL] ?: "").toString()
                                    val phone = (card[CardStorageHelper.KEY_PHONE] ?: "").toString()
                                    val insta = (card[CardStorageHelper.KEY_INSTAGRAM] ?: "").toString()
                                    val web = (card[CardStorageHelper.KEY_WEBSITE] ?: "").toString()
                                    val addr = (card[CardStorageHelper.KEY_ADDRESS] ?: "").toString()
                                    cardStorageHelper.deleteCardByContent(userId, name, occ, email, phone, insta, web, addr)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@SavedCardsUI, "Delete failed", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val idStr = card[CardStorageHelper.KEY_ID]?.toString()
                    val name = (card[CardStorageHelper.KEY_NAME] ?: "").toString()
                    val occ = (card[CardStorageHelper.KEY_OCCUPATION] ?: "").toString()
                    val email = (card[CardStorageHelper.KEY_EMAIL] ?: "").toString()
                    val phone = (card[CardStorageHelper.KEY_PHONE] ?: "").toString()
                    val insta = (card[CardStorageHelper.KEY_INSTAGRAM] ?: "").toString()
                    val web = (card[CardStorageHelper.KEY_WEBSITE] ?: "").toString()
                    val addr = (card[CardStorageHelper.KEY_ADDRESS] ?: "").toString()

                    // Ensure we have a userId to operate on
                    if (userId == null) {
                        Toast.makeText(this@SavedCardsUI, "User not signed in", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // If online, try to remove any matching card(s) from Firebase by content
                    if (NetworkUtils.isOnline(this@SavedCardsUI)) {
                        Firebase.database.reference
                            .child("users")
                            .child(userId!!)
                            .child("cards")
                            .get()
                            .addOnSuccessListener { snap ->
                                var removedFromCloud = 0
                                snap.children.forEach { child ->
                                    val cmap = hashMapOf<String, Any?>()
                                    cmap[CardStorageHelper.KEY_NAME] = child.child(CardStorageHelper.KEY_NAME).getValue(String::class.java) ?: ""
                                    cmap[CardStorageHelper.KEY_OCCUPATION] = child.child(CardStorageHelper.KEY_OCCUPATION).getValue(String::class.java) ?: ""
                                    cmap[CardStorageHelper.KEY_EMAIL] = child.child(CardStorageHelper.KEY_EMAIL).getValue(String::class.java) ?: ""
                                    cmap[CardStorageHelper.KEY_PHONE] = child.child(CardStorageHelper.KEY_PHONE).getValue(String::class.java) ?: ""
                                    cmap[CardStorageHelper.KEY_INSTAGRAM] = child.child(CardStorageHelper.KEY_INSTAGRAM).getValue(String::class.java) ?: ""
                                    cmap[CardStorageHelper.KEY_WEBSITE] = child.child(CardStorageHelper.KEY_WEBSITE).getValue(String::class.java) ?: ""
                                    cmap[CardStorageHelper.KEY_ADDRESS] = child.child(CardStorageHelper.KEY_ADDRESS).getValue(String::class.java) ?: ""
                                    if (computeCardKey(cmap) == computeCardKey(card)) {
                                        child.ref.removeValue()
                                        removedFromCloud++
                                    }
                                }
                                // Remove locally regardless
                                val id = idStr?.toLongOrNull()
                                val localRemoved = if (id != null) {
                                    cardStorageHelper.deleteCard(id)
                                } else {
                                    cardStorageHelper.deleteCardByContent(userId!!, name, occ, email, phone, insta, web, addr) > 0
                                }
                                container.removeView(cardFrame)
                                Toast.makeText(this@SavedCardsUI, if (removedFromCloud > 0) "Deleted from cloud and local" else "Deleted locally", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                // Fallback to local delete
                                val id = idStr?.toLongOrNull()
                                val ok = if (id != null) {
                                    cardStorageHelper.deleteCard(id)
                                } else {
                                    cardStorageHelper.deleteCardByContent(userId!!, name, occ, email, phone, insta, web, addr) > 0
                                }
                                if (ok) {
                                    container.removeView(cardFrame)
                                    Toast.makeText(this@SavedCardsUI, "Deleted locally. Cloud delete failed.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@SavedCardsUI, "Delete failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        // Offline: delete locally; on next sync the card won't be re-uploaded because it's gone locally
                        val id = idStr?.toLongOrNull()
                        val ok = if (id != null) {
                            cardStorageHelper.deleteCard(id)
                        } else {
                            if (userId != null) {
                                cardStorageHelper.deleteCardByContent(userId!!, name, occ, email, phone, insta, web, addr) > 0
                            } else {
                                false
                            }
                        }
                        if (ok) {
                            container.removeView(cardFrame)
                            Toast.makeText(this@SavedCardsUI, "Deleted locally. Will remove from cloud when online.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SavedCardsUI, "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val deleteLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            rightMargin = 8.dpToPx()
            topMargin = 8.dpToPx()
        }
        cardFrame.addView(deleteIcon, deleteLp)

        // Add large title (Name) at top-left (slightly smaller to keep card compact)
        card[CardStorageHelper.KEY_NAME]?.let { nm ->
            val nameView = TextView(this).apply {
                text = nm.toString()
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                // No start padding; align to pill's left EDGE via marginStart
                setPadding(0, 0, 0, 4.dpToPx())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            nameView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // Align with pill LEFT EDGE: equals the row's left margin (8dp)
                marginStart = 8.dpToPx()
            }
            contentLayout.addView(nameView)
        }

        val tagValue = (card[CardStorageHelper.KEY_TAG] ?: "").toString().trim()
        if (tagValue.isNotBlank()) {
            val tagView = TextView(this).apply {
                text = tagValue
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(0, 0, 0, 8.dpToPx())
            }
            tagView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8.dpToPx()
            }
            contentLayout.addView(tagView)
        }

        cardFrame.setOnLongClickListener {
            showTagDialog(card)
            true
        }
        
        // Add occupation row
        card[CardStorageHelper.KEY_OCCUPATION]?.takeIf { it.toString().isNotBlank() }?.let { occupation ->
            addCardRow(contentLayout, "", occupation.toString(), R.drawable.ic_occupation, false)
        }
        
        // Add email row
        card[CardStorageHelper.KEY_EMAIL]?.takeIf { it.toString().isNotBlank() }?.let { email ->
            addCardRow(contentLayout, "", email.toString(), R.drawable.ic_email, true) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                }
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
        }
        
        // Add phone row
        card[CardStorageHelper.KEY_PHONE]?.takeIf { it.toString().isNotBlank() }?.let { phone ->
            addCardRow(contentLayout, "", phone.toString(), R.drawable.ic_phone, true) {
                val tel = sanitizePhoneForTel(phone.toString())
                if (tel != null) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(tel)))
            }
        }
        
        // Add Instagram row
        card[CardStorageHelper.KEY_INSTAGRAM]?.takeIf { it.toString().isNotBlank() }?.let { instagram ->
            addCardRow(contentLayout, "", instagram.toString(), R.drawable.ic_instagram, true) {
                val username = if (instagram.toString().startsWith("@")) 
                    instagram.toString().substring(1) 
                else 
                    instagram.toString()
                val url = "https://instagram.com/$username"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        
        // Add website row
        card[CardStorageHelper.KEY_WEBSITE]?.takeIf { it.toString().isNotBlank() }?.let { website ->
            val displayUrl = if (website.toString().startsWith("http")) 
                website.toString() 
            else 
                "https://$website"
            addCardRow(contentLayout, "", website.toString(), R.drawable.ic_website, true) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(displayUrl)))
            }
        }
        
        // Add address row
        card[CardStorageHelper.KEY_ADDRESS]?.takeIf { it.toString().isNotBlank() }?.let { address ->
            addCardRow(contentLayout, "", address.toString(), R.drawable.ic_address, true) {
                val mapUri = Uri.parse("geo:0,0?q=" + Uri.encode(address.toString()))
                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
        }
        
        container.addView(cardFrame)
    }

    private fun showTagDialog(card: Map<String, Any?>) {
        val userId = auth.currentUser?.uid ?: return
        val idStr = card[CardStorageHelper.KEY_ID]?.toString()
        val current = (card[CardStorageHelper.KEY_TAG] ?: "").toString().trim()

        val tags = mutableListOf<String>()
        tags.add("No tag")
        defaultTags.filter { it.isNotBlank() }.forEach { t ->
            if (!tags.contains(t)) tags.add(t)
        }
        val dataTags = allCards
            .map { (it[CardStorageHelper.KEY_TAG] ?: "").toString().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        dataTags.forEach { t -> if (!tags.contains(t)) tags.add(t) }

        val preselect = when {
            current.isBlank() -> 0
            else -> tags.indexOf(current).takeIf { it >= 0 } ?: 0
        }

        AlertDialog.Builder(this)
            .setTitle("Set Tag")
            .setSingleChoiceItems(tags.toTypedArray(), preselect) { dialog, which ->
                val selected = tags[which]
                val newTag = if (selected == "No tag") "" else selected

                val localId = idStr?.toLongOrNull()
                if (localId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        cardStorageHelper.updateTagById(localId, newTag)
                    }
                }

                if (NetworkUtils.isOnline(this) && prefs.getBoolean("auto_sync_enabled", true)) {
                    val fbKey = card["__fbKey"] as? String
                    if (!fbKey.isNullOrBlank()) {
                        Firebase.database.reference
                            .child("users")
                            .child(userId)
                            .child("cards")
                            .child(fbKey)
                            .child(CardStorageHelper.KEY_TAG)
                            .setValue(newTag)
                    } else {
                        updateCloudTagByContent(userId, card, newTag)
                    }
                }

                val updated = card.toMutableMap()
                updated[CardStorageHelper.KEY_TAG] = newTag
                allCards = allCards.map { c ->
                    if (computeCardKey(c) == computeCardKey(card)) updated else c
                }
                updateTagFilterOptions(allCards)
                renderFiltered()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCloudTagByContent(userId: String, card: Map<String, Any?>, newTag: String) {
        Firebase.database.reference
            .child("users")
            .child(userId)
            .child("cards")
            .get()
            .addOnSuccessListener { snap ->
                snap.children.forEach { child ->
                    val cmap = hashMapOf<String, Any?>()
                    cmap[CardStorageHelper.KEY_NAME] = child.child(CardStorageHelper.KEY_NAME).getValue(String::class.java) ?: ""
                    cmap[CardStorageHelper.KEY_OCCUPATION] = child.child(CardStorageHelper.KEY_OCCUPATION).getValue(String::class.java) ?: ""
                    cmap[CardStorageHelper.KEY_EMAIL] = child.child(CardStorageHelper.KEY_EMAIL).getValue(String::class.java) ?: ""
                    cmap[CardStorageHelper.KEY_PHONE] = child.child(CardStorageHelper.KEY_PHONE).getValue(String::class.java) ?: ""
                    cmap[CardStorageHelper.KEY_INSTAGRAM] = child.child(CardStorageHelper.KEY_INSTAGRAM).getValue(String::class.java) ?: ""
                    cmap[CardStorageHelper.KEY_WEBSITE] = child.child(CardStorageHelper.KEY_WEBSITE).getValue(String::class.java) ?: ""
                    cmap[CardStorageHelper.KEY_ADDRESS] = child.child(CardStorageHelper.KEY_ADDRESS).getValue(String::class.java) ?: ""
                    if (computeCardKey(cmap) == computeCardKey(card)) {
                        child.ref.child(CardStorageHelper.KEY_TAG).setValue(newTag)
                    }
                }
            }
    }
    
    private fun addCardRow(
        parent: LinearLayout,
        label: String, // ignored
        value: String,
        @DrawableRes iconResId: Int,
        clickable: Boolean,
        onClick: (() -> Unit)? = null
    ) {
        val theme = currentTheme
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(context, R.drawable.rounded_pill)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                36.dpToPx()
            ).apply {
                // add padding from all sides around each pill
                setMargins(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 0)
            }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6.dpToPx(), 0, 6.dpToPx(), 0)
        }

        if (iconResId != 0) {
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx())
                setBackgroundResource(R.drawable.circle_icon_bg)
                setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                setImageResource(iconResId)
            }
            // Keep icon legible on different themes
            theme?.let { t ->
                iconView.backgroundTintList = ColorStateList.valueOf(t.headerColor)
                iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            row.addView(iconView)
        }

        val valueText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx()
            }
            text = value
            textSize = 13f
            setTextColor(theme?.pillTextColor ?: Color.parseColor("#1A1A1A"))
            isClickable = clickable
            isFocusable = clickable
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(valueText)

        theme?.let { t ->
            row.background?.mutate()?.let { bg ->
                DrawableCompat.setTint(bg, t.pillBgColor)
            }
        }

        if (clickable && onClick != null) {
            row.setOnClickListener { onClick.invoke() }
            valueText.setOnClickListener { onClick.invoke() }
        }

        parent.addView(row)
    }

    

    private fun addFooterNote() {
        // Remove existing footer if present to avoid duplicates
        for (i in container.childCount - 1 downTo 0) {
            val v = container.getChildAt(i)
            if (v.tag == FOOTER_TAG) {
                container.removeViewAt(i)
            }
        }

        val noteText = TextView(this).apply {
            tag = FOOTER_TAG
            text = "* Phone, email, Instagram, website and address are clickable"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setPadding(8, 24, 8, 24)
        }
        container.addView(noteText)
    }
}
