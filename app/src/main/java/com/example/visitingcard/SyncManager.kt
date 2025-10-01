package com.example.visitingcard

import android.content.Context
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object SyncManager {
    private const val TAG = "SyncManager"

    fun syncAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!NetworkUtils.isOnline(context)) return@launch
                syncProfile(context)
                syncCards(context)
            } catch (e: Exception) {
                Log.w(TAG, "syncAll failed: ${e.message}")
            }
        }
    }

    suspend fun syncProfile(context: Context) {
        try {
            val user = Firebase.auth.currentUser ?: return
            val storage = ProfileStorage(context)
            if (storage.isDirty()) {
                // Push local changes to cloud
                val local = storage.getProfile()
                val toUpload = mapOf(
                    "Name" to (local[ProfileStorage.KEY_NAME] ?: ""),
                    "Occupation" to (local[ProfileStorage.KEY_OCCUPATION] ?: ""),
                    "Email" to (local[ProfileStorage.KEY_EMAIL] ?: (user.email ?: "")),
                    "Phone" to (local[ProfileStorage.KEY_PHONE] ?: ""),
                    "Instagram" to (local[ProfileStorage.KEY_INSTAGRAM] ?: ""),
                    "Website" to (local[ProfileStorage.KEY_WEBSITE] ?: ""),
                    "Address" to (local[ProfileStorage.KEY_ADDRESS] ?: "")
                )
                Firebase.database.reference
                    .child("users").child(user.uid).child("profile")
                    .setValue(toUpload).await()
                storage.clearDirty()
            } else {
                // Pull cloud to local cache
                val snapshot = Firebase.database.reference
                    .child("users").child(user.uid).child("profile")
                    .get().await()
                val profile = mapOf(
                    ProfileStorage.KEY_NAME to (snapshot.child("Name").getValue(String::class.java) ?: ""),
                    ProfileStorage.KEY_OCCUPATION to (snapshot.child("Occupation").getValue(String::class.java) ?: ""),
                    ProfileStorage.KEY_EMAIL to (snapshot.child("Email").getValue(String::class.java) ?: (user.email ?: "")),
                    ProfileStorage.KEY_PHONE to (snapshot.child("Phone").getValue(String::class.java) ?: ""),
                    ProfileStorage.KEY_INSTAGRAM to (snapshot.child("Instagram").getValue(String::class.java) ?: ""),
                    ProfileStorage.KEY_WEBSITE to (snapshot.child("Website").getValue(String::class.java) ?: ""),
                    ProfileStorage.KEY_ADDRESS to (snapshot.child("Address").getValue(String::class.java) ?: "")
                )
                storage.saveProfile(profile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncProfile failed: ${e.message}")
        }
    }

    suspend fun syncCards(context: Context) {
        try {
            val user = Firebase.auth.currentUser ?: return
            val dbRef = Firebase.database.reference
                .child("users").child(user.uid).child("cards")
            val snapshot = dbRef.get().await()
            val helper = CardStorageHelper(context)

            // Build cloud map and migrate duplicates to deterministic keys
            val cloudSet = mutableSetOf<String>()
            val cloudCards = mutableListOf<Map<String, String>>()
            // key -> list of children representing same content
            val groups = mutableMapOf<String, MutableList<com.google.firebase.database.DataSnapshot>>()
            snapshot.children.forEach { child ->
                val name = child.child(CardStorageHelper.KEY_NAME).getValue(String::class.java) ?: ""
                val occ = child.child(CardStorageHelper.KEY_OCCUPATION).getValue(String::class.java) ?: ""
                val email = child.child(CardStorageHelper.KEY_EMAIL).getValue(String::class.java) ?: ""
                val phone = child.child(CardStorageHelper.KEY_PHONE).getValue(String::class.java) ?: ""
                val insta = child.child(CardStorageHelper.KEY_INSTAGRAM).getValue(String::class.java) ?: ""
                val web = child.child(CardStorageHelper.KEY_WEBSITE).getValue(String::class.java) ?: ""
                val addr = child.child(CardStorageHelper.KEY_ADDRESS).getValue(String::class.java) ?: ""
                val key = normalizeKey(name, occ, email, phone, insta, web, addr)
                groups.getOrPut(key) { mutableListOf() }.add(child)
                cloudSet.add(key)
                cloudCards.add(
                    mapOf(
                        CardStorageHelper.KEY_NAME to name,
                        CardStorageHelper.KEY_OCCUPATION to occ,
                        CardStorageHelper.KEY_EMAIL to email,
                        CardStorageHelper.KEY_PHONE to phone,
                        CardStorageHelper.KEY_INSTAGRAM to insta,
                        CardStorageHelper.KEY_WEBSITE to web,
                        CardStorageHelper.KEY_ADDRESS to addr
                    )
                )
            }

            // For each group, keep exactly one under deterministic key and remove the rest
            for ((key, children) in groups) {
                val detKey = hashKey(key)
                // Build value from the first child (preserve createdAt/source if available)
                val first = children.first()
                val payload = hashMapOf<String, Any?>().apply {
                    put(CardStorageHelper.KEY_NAME, first.child(CardStorageHelper.KEY_NAME).getValue(String::class.java) ?: "")
                    put(CardStorageHelper.KEY_OCCUPATION, first.child(CardStorageHelper.KEY_OCCUPATION).getValue(String::class.java) ?: "")
                    put(CardStorageHelper.KEY_EMAIL, first.child(CardStorageHelper.KEY_EMAIL).getValue(String::class.java) ?: "")
                    put(CardStorageHelper.KEY_PHONE, first.child(CardStorageHelper.KEY_PHONE).getValue(String::class.java) ?: "")
                    put(CardStorageHelper.KEY_INSTAGRAM, first.child(CardStorageHelper.KEY_INSTAGRAM).getValue(String::class.java) ?: "")
                    put(CardStorageHelper.KEY_WEBSITE, first.child(CardStorageHelper.KEY_WEBSITE).getValue(String::class.java) ?: "")
                    put(CardStorageHelper.KEY_ADDRESS, first.child(CardStorageHelper.KEY_ADDRESS).getValue(String::class.java) ?: "")
                    put("source", first.child("source").getValue(String::class.java) ?: "local_sync")
                    put("createdAt", first.child("createdAt").getValue(String::class.java) ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date()))
                }
                // Write/overwrite canonical deterministic node
                dbRef.child(detKey).setValue(payload).await()
                // Remove all other nodes (including non-deterministic and duplicate deterministic ones)
                children.forEach { child ->
                    if (child.key != detKey) {
                        child.ref.removeValue().await()
                    }
                }
            }

            // Upload local-only cards (not in cloud)
            val localCards = helper.getUserCards(user.uid)
            localCards.forEach { card ->
                val name = card[CardStorageHelper.KEY_NAME] ?: ""
                val occ = card[CardStorageHelper.KEY_OCCUPATION] ?: ""
                val email = card[CardStorageHelper.KEY_EMAIL] ?: ""
                val phone = card[CardStorageHelper.KEY_PHONE] ?: ""
                val insta = card[CardStorageHelper.KEY_INSTAGRAM] ?: ""
                val web = card[CardStorageHelper.KEY_WEBSITE] ?: ""
                val addr = card[CardStorageHelper.KEY_ADDRESS] ?: ""
                val key = normalizeKey(name, occ, email, phone, insta, web, addr)
                if (!cloudSet.contains(key)) {
                    val formattedTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date())
                    val cardForFirebase = hashMapOf(
                        CardStorageHelper.KEY_NAME to name,
                        CardStorageHelper.KEY_OCCUPATION to occ,
                        CardStorageHelper.KEY_EMAIL to email,
                        CardStorageHelper.KEY_PHONE to phone,
                        CardStorageHelper.KEY_INSTAGRAM to insta,
                        CardStorageHelper.KEY_WEBSITE to web,
                        CardStorageHelper.KEY_ADDRESS to addr,
                        "source" to "local_sync",
                        "createdAt" to formattedTime
                    )
                    // Use deterministic key to avoid duplicates on repeated syncs
                    dbRef.child(hashKey(key)).setValue(cardForFirebase).await()
                    cloudSet.add(key)
                }
            }

            // Download cloud-only cards into local
            cloudCards.forEach { c ->
                val name = c[CardStorageHelper.KEY_NAME] ?: ""
                val occ = c[CardStorageHelper.KEY_OCCUPATION] ?: ""
                val email = c[CardStorageHelper.KEY_EMAIL] ?: ""
                val phone = c[CardStorageHelper.KEY_PHONE] ?: ""
                val insta = c[CardStorageHelper.KEY_INSTAGRAM] ?: ""
                val web = c[CardStorageHelper.KEY_WEBSITE] ?: ""
                val addr = c[CardStorageHelper.KEY_ADDRESS] ?: ""
                if (!helper.existsCard(user.uid, name, occ, email, phone, insta, web, addr)) {
                    helper.insertCard(user.uid, name, occ, email, phone, insta, web, addr)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncCards failed: ${e.message}")
        }
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
