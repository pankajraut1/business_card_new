package com.example.visitingcard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class EditInfoUI : AppCompatActivity() {
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    lateinit var submit: Button
    lateinit var name: EditText
    lateinit var occ: EditText
    lateinit var email: EditText
    lateinit var phone: EditText
    lateinit var instagram: EditText
    lateinit var website: EditText
    lateinit var address: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Show UI immediately to avoid blank screen while we may fetch
        initForm()

        // Firebase Auth is initialized via lazy delegate
        val editMode = intent.getBooleanExtra("editMode", false)

        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not signed in, redirect to login
            startActivity(Intent(this, LoginUI::class.java))
            finish()
            return
        }

        // Instant redirect if we already have cached profile (even when online)
        if (!editMode) {
            val cachedInstant = ProfileStorage(this).getProfile()
            val nameInstant = cachedInstant[ProfileStorage.KEY_NAME] ?: ""
            if (nameInstant.isNotBlank()) {
                val intent = Intent(this, BusinessCardUI::class.java)
                intent.putExtra("Name", nameInstant)
                intent.putExtra("Occupation", cachedInstant[ProfileStorage.KEY_OCCUPATION] ?: "")
                intent.putExtra("Email", cachedInstant[ProfileStorage.KEY_EMAIL] ?: (currentUser.email ?: ""))
                intent.putExtra("Phone", cachedInstant[ProfileStorage.KEY_PHONE] ?: "")
                intent.putExtra("Instagram", cachedInstant[ProfileStorage.KEY_INSTAGRAM] ?: "")
                intent.putExtra("Website", cachedInstant[ProfileStorage.KEY_WEBSITE] ?: "")
                intent.putExtra("Address", cachedInstant[ProfileStorage.KEY_ADDRESS] ?: "")
                startActivity(intent)
                finish()
                return
            }
        }

        // If offline, use cached profile and avoid network
        if (!NetworkUtils.isOnline(this)) {
            val cached = ProfileStorage(this).getProfile()
            val nameDb = cached[ProfileStorage.KEY_NAME] ?: ""
            val occDb = cached[ProfileStorage.KEY_OCCUPATION] ?: ""
            val emailDb = cached[ProfileStorage.KEY_EMAIL] ?: (currentUser.email ?: "")
            val phoneDb = cached[ProfileStorage.KEY_PHONE] ?: ""
            val instagramDb = cached[ProfileStorage.KEY_INSTAGRAM] ?: ""
            val websiteDb = cached[ProfileStorage.KEY_WEBSITE] ?: ""
            val addressDb = cached[ProfileStorage.KEY_ADDRESS] ?: ""

            if (!editMode && nameDb.isNotBlank()) {
                val intent = Intent(this, BusinessCardUI::class.java)
                intent.putExtra("Name", nameDb)
                intent.putExtra("Occupation", occDb)
                intent.putExtra("Email", emailDb)
                intent.putExtra("Phone", phoneDb)
                intent.putExtra("Instagram", instagramDb)
                intent.putExtra("Website", websiteDb)
                intent.putExtra("Address", addressDb)
                startActivity(intent)
                finish()
                return
            } else {
                // Prefill from cache
                if (editMode) {
                    name.setText(intent.getStringExtra("Name")?.takeIf { it.isNotBlank() } ?: nameDb)
                    occ.setText(intent.getStringExtra("Occupation")?.takeIf { it.isNotBlank() } ?: occDb)
                    phone.setText(intent.getStringExtra("Phone")?.takeIf { it.isNotBlank() } ?: phoneDb)
                    instagram.setText(intent.getStringExtra("Instagram")?.takeIf { it.isNotBlank() } ?: instagramDb)
                    website.setText(intent.getStringExtra("Website")?.takeIf { it.isNotBlank() } ?: websiteDb)
                    address.setText(intent.getStringExtra("Address")?.takeIf { it.isNotBlank() } ?: addressDb)
                } else {
                    if (name.text.isBlank()) name.setText(nameDb)
                    if (occ.text.isBlank()) occ.setText(occDb)
                    if (phone.text.isBlank()) phone.setText(phoneDb)
                    if (instagram.text.isBlank()) instagram.setText(instagramDb)
                    if (website.text.isBlank()) website.setText(websiteDb)
                    if (address.text.isBlank()) address.setText(addressDb)
                }
                return
            }
        }

        // Load from Firebase Realtime Database
        val dbRef = Firebase.database.reference
            .child("users")
            .child(currentUser.uid)
            .child("profile")

        dbRef.get()
            .addOnSuccessListener { snapshot ->
                val nameDb = snapshot.child("Name").getValue(String::class.java) ?: ""
                val occDb = snapshot.child("Occupation").getValue(String::class.java) ?: ""
                val emailDb = snapshot.child("Email").getValue(String::class.java) ?: (currentUser.email ?: "")
                val phoneDb = snapshot.child("Phone").getValue(String::class.java) ?: ""
                val instagramDb = snapshot.child("Instagram").getValue(String::class.java) ?: ""
                val websiteDb = snapshot.child("Website").getValue(String::class.java) ?: ""
                val addressDb = snapshot.child("Address").getValue(String::class.java) ?: ""

                // Cache locally for offline use
                ProfileStorage(this).saveProfile(
                    mapOf(
                        ProfileStorage.KEY_NAME to nameDb,
                        ProfileStorage.KEY_OCCUPATION to occDb,
                        ProfileStorage.KEY_EMAIL to emailDb,
                        ProfileStorage.KEY_PHONE to phoneDb,
                        ProfileStorage.KEY_INSTAGRAM to instagramDb,
                        ProfileStorage.KEY_WEBSITE to websiteDb,
                        ProfileStorage.KEY_ADDRESS to addressDb
                    )
                )

                if (!editMode && nameDb.isNotBlank()) {
                    val intent = Intent(this, BusinessCardUI::class.java)
                    intent.putExtra("Name", nameDb)
                    intent.putExtra("Occupation", occDb)
                    intent.putExtra("Email", emailDb)
                    intent.putExtra("Phone", phoneDb)
                    intent.putExtra("Instagram", instagramDb)
                    intent.putExtra("Website", websiteDb)
                    intent.putExtra("Address", addressDb)
                    startActivity(intent)
                    finish()
                } else {
                    // Already showing form; just prefill
                    // Prefill if editing or no redirect
                    if (editMode) {
                        name.setText(intent.getStringExtra("Name")?.takeIf { it.isNotBlank() } ?: nameDb)
                        occ.setText(intent.getStringExtra("Occupation")?.takeIf { it.isNotBlank() } ?: occDb)
                        phone.setText(intent.getStringExtra("Phone")?.takeIf { it.isNotBlank() } ?: phoneDb)
                        instagram.setText(intent.getStringExtra("Instagram")?.takeIf { it.isNotBlank() } ?: instagramDb)
                        website.setText(intent.getStringExtra("Website")?.takeIf { it.isNotBlank() } ?: websiteDb)
                        address.setText(intent.getStringExtra("Address")?.takeIf { it.isNotBlank() } ?: addressDb)
                    } else {
                        if (name.text.isBlank()) name.setText(nameDb)
                        if (occ.text.isBlank()) occ.setText(occDb)
                        if (phone.text.isBlank()) phone.setText(phoneDb)
                        if (instagram.text.isBlank()) instagram.setText(instagramDb)
                        if (website.text.isBlank()) website.setText(websiteDb)
                        if (address.text.isBlank()) address.setText(addressDb)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditInfoUI", "Failed to load profile: ", e)
                Toast.makeText(this, "Could not load saved profile", Toast.LENGTH_SHORT).show()
                // Try to prefill from cache
                val cached = ProfileStorage(this).getProfile()
                if (name.text.isBlank()) name.setText(cached[ProfileStorage.KEY_NAME] ?: "")
                if (occ.text.isBlank()) occ.setText(cached[ProfileStorage.KEY_OCCUPATION] ?: "")
                if (phone.text.isBlank()) phone.setText(cached[ProfileStorage.KEY_PHONE] ?: "")
                if (instagram.text.isBlank()) instagram.setText(cached[ProfileStorage.KEY_INSTAGRAM] ?: "")
                if (website.text.isBlank()) website.setText(cached[ProfileStorage.KEY_WEBSITE] ?: "")
                if (address.text.isBlank()) address.setText(cached[ProfileStorage.KEY_ADDRESS] ?: "")
            }
    }

    private fun initForm() {
        setContentView(R.layout.edit_info_ui)

        // Initialize views
        submit = findViewById(R.id.submit)
        name = findViewById(R.id.name)
        occ = findViewById(R.id.occupation)
        email = findViewById(R.id.email)
        phone = findViewById(R.id.phoneno)
        instagram = findViewById(R.id.instagram)
        website = findViewById(R.id.website)
        address = findViewById(R.id.address)

        // Set the email from the authenticated user
        val currentUser = auth.currentUser
        currentUser?.email?.let {
            email.setText(it)
            email.isEnabled = false // Prevent editing the email
        }

        submit.setOnClickListener {
            val nameStr = name.text.toString().trim()
            val occStr = occ.text.toString().trim()
            val emailStr = email.text.toString().trim()
            val phoneStr = phone.text.toString().trim()
            val instagramStr = instagram.text.toString().trim()
            val websiteStr = website.text.toString().trim()
            val addressStr = address.text.toString().trim()

            val noDigitsRegex = Regex(".*\\d.*")

            if (nameStr.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (occStr.isEmpty()) {
                Toast.makeText(this, "Please enter your occupation", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phoneStr.isEmpty()) {
                Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (noDigitsRegex.matches(nameStr)) {
                Toast.makeText(this, "Name cannot contain numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (noDigitsRegex.matches(occStr)) {
                Toast.makeText(this, "Occupation cannot contain numbers", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (!phoneStr.matches(Regex("^\\d{10}$"))) {
                Toast.makeText(this, "Enter a valid 10-digit phone number", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (emailStr.isNotEmpty() && !emailStr.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (websiteStr.isNotEmpty() && !websiteStr.matches(Regex("^(https?://)?[\\w.-]+\\.[a-z]{2,}.*$"))) {
                Toast.makeText(this, "Please enter a valid website URL", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener
            val profile = mapOf(
                "Name" to nameStr,
                "Occupation" to occStr,
                "Email" to emailStr,
                "Phone" to phoneStr,
                "Instagram" to instagramStr,
                "Website" to websiteStr,
                "Address" to addressStr
            )

            if (!NetworkUtils.isOnline(this)) {
                // Offline: save to local cache and mark dirty, then proceed to show card
                ProfileStorage(this).saveProfile(
                    mapOf(
                        ProfileStorage.KEY_NAME to nameStr,
                        ProfileStorage.KEY_OCCUPATION to occStr,
                        ProfileStorage.KEY_EMAIL to emailStr,
                        ProfileStorage.KEY_PHONE to phoneStr,
                        ProfileStorage.KEY_INSTAGRAM to instagramStr,
                        ProfileStorage.KEY_WEBSITE to websiteStr,
                        ProfileStorage.KEY_ADDRESS to addressStr
                    )
                )
                ProfileStorage(this).markDirty()

                val intent = Intent(this, BusinessCardUI::class.java)
                intent.putExtra("Name", nameStr)
                intent.putExtra("Occupation", occStr)
                intent.putExtra("Email", emailStr)
                intent.putExtra("Phone", phoneStr)
                intent.putExtra("Instagram", instagramStr)
                intent.putExtra("Website", websiteStr)
                intent.putExtra("Address", addressStr)
                startActivity(intent)
                return@setOnClickListener
            }

            Firebase.database.reference
                .child("users")
                .child(currentUserId)
                .child("profile")
                .setValue(profile)
                .addOnSuccessListener {
                    // Save locally as well
                    ProfileStorage(this).saveProfile(
                        mapOf(
                            ProfileStorage.KEY_NAME to nameStr,
                            ProfileStorage.KEY_OCCUPATION to occStr,
                            ProfileStorage.KEY_EMAIL to emailStr,
                            ProfileStorage.KEY_PHONE to phoneStr,
                            ProfileStorage.KEY_INSTAGRAM to instagramStr,
                            ProfileStorage.KEY_WEBSITE to websiteStr,
                            ProfileStorage.KEY_ADDRESS to addressStr
                        )
                    )
                    ProfileStorage(this).clearDirty()
                    val intent = Intent(this, BusinessCardUI::class.java)
                    intent.putExtra("Name", nameStr)
                    intent.putExtra("Occupation", occStr)
                    intent.putExtra("Email", emailStr)
                    intent.putExtra("Phone", phoneStr)
                    intent.putExtra("Instagram", instagramStr)
                    intent.putExtra("Website", websiteStr)
                    intent.putExtra("Address", addressStr)
                    startActivity(intent)
                }
                .addOnFailureListener { e ->
                    Log.e("EditInfoUI", "Failed to save profile: ", e)
                    Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
