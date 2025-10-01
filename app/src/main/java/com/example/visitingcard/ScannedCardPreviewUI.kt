package com.example.visitingcard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.visitingcard.R
import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database


class ScannedPreviewUI : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scanned_preview_ui)

        val cardData = intent.getStringExtra("cardData")

        val tvRaw = findViewById<TextView>(R.id.scannedCardText)
        val saveButton = findViewById<Button>(R.id.saveCardButton)

        val tvName = findViewById<TextView>(R.id.tvName)
        val rowOccupation = findViewById<LinearLayout>(R.id.rowOccupation)
        val tvOccupation = findViewById<TextView>(R.id.tvOccupation)
        val rowEmail = findViewById<LinearLayout>(R.id.rowEmail)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val rowPhone = findViewById<LinearLayout>(R.id.rowPhone)
        val tvPhone = findViewById<TextView>(R.id.tvPhone)
        val rowInstagram = findViewById<LinearLayout>(R.id.rowInstagram)
        val tvInstagram = findViewById<TextView>(R.id.tvInstagram)
        val rowWebsite = findViewById<LinearLayout>(R.id.rowWebsite)
        val tvWebsite = findViewById<TextView>(R.id.tvWebsite)
        val rowAddress = findViewById<LinearLayout>(R.id.rowAddress)
        val tvAddress = findViewById<TextView>(R.id.tvAddress)

        // Populate preview
        val parsed = parseScannedCardData(cardData ?: "")
        val name = parsed[CardStorageHelper.KEY_NAME] ?: ""
        val occ = parsed[CardStorageHelper.KEY_OCCUPATION] ?: ""
        val email = parsed[CardStorageHelper.KEY_EMAIL] ?: ""
        val phone = parsed[CardStorageHelper.KEY_PHONE] ?: ""
        val insta = parsed[CardStorageHelper.KEY_INSTAGRAM] ?: ""
        val web = parsed[CardStorageHelper.KEY_WEBSITE] ?: ""
        val addr = parsed[CardStorageHelper.KEY_ADDRESS] ?: ""

        tvName.text = if (name.isNotBlank()) name else "Unknown"

        fun setRow(row: LinearLayout, tv: TextView, value: String) {
            if (value.isBlank()) {
                row.visibility = View.GONE
            } else {
                row.visibility = View.VISIBLE
                tv.text = value
            }
        }
        setRow(rowOccupation, tvOccupation, occ)
        setRow(rowEmail, tvEmail, email)
        setRow(rowPhone, tvPhone, phone)
        setRow(rowInstagram, tvInstagram, insta)
        setRow(rowWebsite, tvWebsite, web)
        setRow(rowAddress, tvAddress, addr)

        // Click actions similar to SavedCardsUI
        if (email.isNotBlank()) {
            val click: (View) -> Unit = {
                val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:$email") }
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
            rowEmail.setOnClickListener(click)
            tvEmail.setOnClickListener(click)
        }
        if (phone.isNotBlank()) {
            val click: (View) -> Unit = {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            }
            rowPhone.setOnClickListener(click)
            tvPhone.setOnClickListener(click)
        }
        if (insta.isNotBlank()) {
            val username = if (insta.startsWith("@")) insta.substring(1) else insta
            val url = "https://instagram.com/$username"
            val click: (View) -> Unit = {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            rowInstagram.setOnClickListener(click)
            tvInstagram.setOnClickListener(click)
        }
        if (web.isNotBlank()) {
            val displayUrl = if (web.startsWith("http")) web else "https://$web"
            val click: (View) -> Unit = {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(displayUrl)))
            }
            rowWebsite.setOnClickListener(click)
            tvWebsite.setOnClickListener(click)
        }
        if (addr.isNotBlank()) {
            val click: (View) -> Unit = {
                val mapUri = Uri.parse("geo:0,0?q=" + Uri.encode(addr))
                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
            rowAddress.setOnClickListener(click)
            tvAddress.setOnClickListener(click)
        }

        // Fallback raw view when nothing parsable
        val anyShown = listOf(occ, email, phone, insta, web, addr).any { it.isNotBlank() }
        if (!anyShown && name.isBlank()) {
            tvRaw.visibility = View.VISIBLE
            tvRaw.text = cardData ?: "No data found"
        } else {
            tvRaw.visibility = View.GONE
        }

        saveButton.setOnClickListener {
            cardData?.let {
                // Reject payment or non-business QR contents
                if (isLikelyPaymentOrNonBusiness(it)) {
                    Toast.makeText(this, "This QR looks like a payment/non-business code. Not saved.", Toast.LENGTH_SHORT).show()
                    return@let
                }
                val userId = Firebase.auth.currentUser?.uid
                if (userId == null) {
                    Toast.makeText(this, "Please log in to save cards", Toast.LENGTH_SHORT).show()
                    return@let
                }

                val parsed = parseScannedCardData(it)
                // Ensure we have at least minimal business information
                if (!hasMinimumBusinessInfo(parsed)) {
                    Toast.makeText(this, "Not enough business card info detected.", Toast.LENGTH_SHORT).show()
                    return@let
                }
                val helper = CardStorageHelper(this)
                helper.insertCard(
                    userId = userId,
                    name = parsed[CardStorageHelper.KEY_NAME] ?: "",
                    occupation = parsed[CardStorageHelper.KEY_OCCUPATION] ?: "",
                    email = parsed[CardStorageHelper.KEY_EMAIL] ?: "",
                    phone = parsed[CardStorageHelper.KEY_PHONE] ?: "",
                    instagram = parsed[CardStorageHelper.KEY_INSTAGRAM] ?: "",
                    website = parsed[CardStorageHelper.KEY_WEBSITE] ?: "",
                    address = parsed[CardStorageHelper.KEY_ADDRESS] ?: "",
                )

                // Also save to Firebase under users/{uid}/cards
                // Use ISO-8601 UTC so it's human-readable and sorts lexicographically
                val formattedTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date())
                val cardForFirebase = hashMapOf(
                    CardStorageHelper.KEY_NAME to (parsed[CardStorageHelper.KEY_NAME] ?: ""),
                    CardStorageHelper.KEY_OCCUPATION to (parsed[CardStorageHelper.KEY_OCCUPATION] ?: ""),
                    CardStorageHelper.KEY_EMAIL to (parsed[CardStorageHelper.KEY_EMAIL] ?: ""),
                    CardStorageHelper.KEY_PHONE to (parsed[CardStorageHelper.KEY_PHONE] ?: ""),
                    CardStorageHelper.KEY_INSTAGRAM to (parsed[CardStorageHelper.KEY_INSTAGRAM] ?: ""),
                    CardStorageHelper.KEY_WEBSITE to (parsed[CardStorageHelper.KEY_WEBSITE] ?: ""),
                    CardStorageHelper.KEY_ADDRESS to (parsed[CardStorageHelper.KEY_ADDRESS] ?: ""),
                    "source" to "scan",
                    // Single human-readable field
                    "createdAt" to formattedTime
                )

                // Use deterministic key based on normalized content to avoid duplicates
                val keyRaw = normalizeKey(
                    cardForFirebase[CardStorageHelper.KEY_NAME] as String,
                    cardForFirebase[CardStorageHelper.KEY_OCCUPATION] as String,
                    cardForFirebase[CardStorageHelper.KEY_EMAIL] as String,
                    cardForFirebase[CardStorageHelper.KEY_PHONE] as String,
                    cardForFirebase[CardStorageHelper.KEY_INSTAGRAM] as String,
                    cardForFirebase[CardStorageHelper.KEY_WEBSITE] as String,
                    cardForFirebase[CardStorageHelper.KEY_ADDRESS] as String
                )
                val deterministicKey = hashKey(keyRaw)

                Firebase.database.reference
                    .child("users")
                    .child(userId)
                    .child("cards")
                    .child(deterministicKey)
                    .setValue(cardForFirebase)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Card saved to cloud", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Saved locally. Cloud save failed.", Toast.LENGTH_SHORT).show()
                    }
                Toast.makeText(this, "Card saved successfully!", Toast.LENGTH_SHORT).show()
                finish() // Go back after saving
            }
        }

    }

    private fun isLikelyPaymentOrNonBusiness(raw: String): Boolean {
        val lower = raw.lowercase()
        // Common UPI/payment indicators
        val paymentMarkers = listOf(
            "upi://", "upi:", "vpa=", "pa=", "pn=", "tr=", "mc=",
            "gpay", "google pay", "tez", "phonepe", "paytm", "bhim", "bharatqr", "npci"
        )
        if (paymentMarkers.any { lower.contains(it) }) return true
        // If it's just a bare URL without any business fields, treat as non-business
        val hasBusinessKeywords = listOf("name", "email", "phone", "instagram", "website", "address").any { lower.contains(it) }
        if ((lower.startsWith("http://") || lower.startsWith("https://")) && !hasBusinessKeywords) return true
        // Very short or single token strings often are not business cards
        val tokens = lower.split('\n', ' ', '\t').filter { it.isNotBlank() }
        if (tokens.size <= 1 && !hasBusinessKeywords) return true
        return false
    }

    private fun hasMinimumBusinessInfo(map: Map<String, String>): Boolean {
        val nameOk = (map[CardStorageHelper.KEY_NAME] ?: "").isNotBlank()
        val phoneOk = (map[CardStorageHelper.KEY_PHONE] ?: "").isNotBlank()
        val emailOk = (map[CardStorageHelper.KEY_EMAIL] ?: "").isNotBlank()
        // Require at least a name and (phone or email)
        return nameOk && (phoneOk || emailOk)
    }

    private fun parseScannedCardData(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        raw.lines().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val label = parts[0].trim().lowercase()
                val value = parts[1].trim()
                when (label) {
                    "name" -> map[CardStorageHelper.KEY_NAME] = value
                    "occupation" -> map[CardStorageHelper.KEY_OCCUPATION] = value
                    "email" -> map[CardStorageHelper.KEY_EMAIL] = value
                    "phone" -> map[CardStorageHelper.KEY_PHONE] = value
                    "instagram" -> map[CardStorageHelper.KEY_INSTAGRAM] = value
                    "website" -> map[CardStorageHelper.KEY_WEBSITE] = value
                    "address" -> map[CardStorageHelper.KEY_ADDRESS] = value
                }
            }
        }
        return map
    }

    private fun normalizeKey(
        name: String,
        occ: String,
        email: String,
        phone: String,
        insta: String,
        web: String,
        addr: String
    ): String {
        fun n(s: String) = s.trim()
        return listOf(n(name), n(occ), n(email.lowercase()), n(phone), n(insta), n(web), n(addr)).joinToString("|")
    }

    private fun hashKey(key: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(key.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
