package com.example.visitingcard

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

class SavedCardsUI : AppCompatActivity() {
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private lateinit var cardStorageHelper: CardStorageHelper
    private lateinit var container: LinearLayout
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val FOOTER_TAG = "footer_note"
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val displayKeySet = mutableSetOf<String>()

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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.saved_cards_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
                    map["createdAt"] = child.child("createdAt").getValue(String::class.java) ?: ""
                    map["__fbKey"] = child.key
                    cloudCards.add(map)
                }

                container.removeAllViews()
                displayKeySet.clear()

                if (cloudCards.isEmpty()) {
                    showNoCardsMessage()
                } else {
                    cloudCards.sortByDescending { (it["createdAt"] as? String) ?: "" }
                    cloudCards.forEach { card ->
                        val key = computeCardKey(card)
                        if (displayKeySet.add(key)) {
                            createCardView(card)
                        }
                    }
                    addFooterNote()

                    // Cache to local DB
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
                                if (!cardStorageHelper.existsCard(userId, name, occ, email, phone, insta, web, addr)) {
                                    cardStorageHelper.insertCard(userId, name, occ, email, phone, insta, web, addr)
                                }
                            }
                        } catch (_: Exception) { }
                    }
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
                    map["createdAt"] = child.child("createdAt").getValue(String::class.java) ?: ""
                    map["__fbKey"] = child.key
                    cloudCards.add(map)
                }

                // Reset UI and dedupe set before repainting from cloud
                container.removeAllViews()
                displayKeySet.clear()
                if (cloudCards.isEmpty()) {
                    showNoCardsMessage()
                } else {
                    // createdAt is ISO-8601 UTC string (yyyy-MM-dd'T'HH:mm:ss'Z'), lexicographic sort works
                    cloudCards.sortByDescending { (it["createdAt"] as? String) ?: "" }
                    cloudCards.forEach { card ->
                        val key = computeCardKey(card)
                        if (displayKeySet.add(key)) {
                            createCardView(card)
                        }
                    }
                    addFooterNote()

                    // Cache to local SQLite for offline access (do not clear to keep unsynced local cards)
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
                                if (!cardStorageHelper.existsCard(userId, name, occ, email, phone, insta, web, addr)) {
                                    cardStorageHelper.insertCard(userId, name, occ, email, phone, insta, web, addr)
                                }
                            }
                        } catch (e: Exception) {
                            // Swallow cache failures silently
                        }
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
                // Reset UI and dedupe set for local-first render
                container.removeAllViews()
                displayKeySet.clear()
                if (cards.isEmpty()) {
                    showNoCardsMessage()
                    return@launch
                }
                cards.forEach { card ->
                    val key = computeCardKey(card)
                    if (displayKeySet.add(key)) {
                        createCardView(card)
                    }
                }
                addFooterNote()
            } catch (e: Exception) {
                showError("Failed to load cards: ${e.message}")
            }
        }
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
        // Use a FrameLayout so we can overlay the delete icon without consuming vertical space
        val cardFrame = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(context, R.drawable.saved_card_bg)
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
            setBackgroundColor(ContextCompat.getColor(this@SavedCardsUI, R.color.headerRed))
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
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
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
    
    private fun addCardRow(
        parent: LinearLayout,
        label: String, // ignored
        value: String,
        @DrawableRes iconResId: Int,
        clickable: Boolean,
        onClick: (() -> Unit)? = null
    ) {
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
            row.addView(iconView)
        }

        val valueText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx()
            }
            text = value
            textSize = 13f
            setTextColor(Color.parseColor("#1A1A1A"))
            isClickable = clickable
            isFocusable = clickable
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(valueText)

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
