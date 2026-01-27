package com.example.visitingcard

import android.content.Context
import android.net.Uri
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object BackupRestoreManager {

    private const val JSON_VERSION = 1

    suspend fun writeBackupToUri(
        context: Context,
        userId: String,
        outUri: Uri
    ): Int {
        val helper = CardStorageHelper(context)
        val cards = helper.getUserCards(userId)

        val root = JSONObject().apply {
            put("version", JSON_VERSION)
            put("userId", userId)
            put("exportedAt", System.currentTimeMillis())

            val arr = JSONArray()
            cards.forEach { c ->
                val o = JSONObject()
                o.put(CardStorageHelper.KEY_NAME, c[CardStorageHelper.KEY_NAME] ?: "")
                o.put(CardStorageHelper.KEY_OCCUPATION, c[CardStorageHelper.KEY_OCCUPATION] ?: "")
                o.put(CardStorageHelper.KEY_EMAIL, c[CardStorageHelper.KEY_EMAIL] ?: "")
                o.put(CardStorageHelper.KEY_PHONE, c[CardStorageHelper.KEY_PHONE] ?: "")
                o.put(CardStorageHelper.KEY_INSTAGRAM, c[CardStorageHelper.KEY_INSTAGRAM] ?: "")
                o.put(CardStorageHelper.KEY_WEBSITE, c[CardStorageHelper.KEY_WEBSITE] ?: "")
                o.put(CardStorageHelper.KEY_ADDRESS, c[CardStorageHelper.KEY_ADDRESS] ?: "")
                o.put(CardStorageHelper.KEY_TAG, c[CardStorageHelper.KEY_TAG] ?: "")
                arr.put(o)
            }
            put("cards", arr)
        }

        context.contentResolver.openOutputStream(outUri)?.use { os ->
            os.write(root.toString().toByteArray(Charsets.UTF_8))
            os.flush()
        } ?: throw IllegalStateException("Unable to open output stream")

        return cards.size
    }

    data class RestoreResult(
        val restoredCount: Int,
        val skippedCount: Int
    )

    suspend fun restoreFromBackupUri(
        context: Context,
        currentUserId: String,
        inUri: Uri
    ): RestoreResult {
        val jsonText = context.contentResolver.openInputStream(inUri)?.use { ins ->
            ins.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Unable to open input stream")

        val root = JSONObject(jsonText)
        val backupUserId = root.optString("userId", "")
        if (backupUserId.isBlank() || backupUserId != currentUserId) {
            throw SecurityException("This backup file belongs to a different user")
        }

        val cardsArray = root.optJSONArray("cards") ?: JSONArray()

        // Build local deterministic key set
        val helper = CardStorageHelper(context)
        val localCards = helper.getUserCards(currentUserId)
        val localDetKeys = localCards.map { card ->
            detKey(
                card[CardStorageHelper.KEY_NAME] ?: "",
                card[CardStorageHelper.KEY_OCCUPATION] ?: "",
                card[CardStorageHelper.KEY_EMAIL] ?: "",
                card[CardStorageHelper.KEY_PHONE] ?: "",
                card[CardStorageHelper.KEY_INSTAGRAM] ?: "",
                card[CardStorageHelper.KEY_WEBSITE] ?: "",
                card[CardStorageHelper.KEY_ADDRESS] ?: ""
            )
        }.toMutableSet()

        // Build cloud key set once
        val dbRef = Firebase.database.reference
            .child("users")
            .child(currentUserId)
            .child("cards")
        val snapshot = dbRef.get().await()
        val cloudKeys = snapshot.children.mapNotNull { it.key }.toMutableSet()

        var restored = 0
        var skipped = 0

        for (i in 0 until cardsArray.length()) {
            val o = cardsArray.optJSONObject(i) ?: continue

            val name = o.optString(CardStorageHelper.KEY_NAME, "")
            val occ = o.optString(CardStorageHelper.KEY_OCCUPATION, "")
            val email = o.optString(CardStorageHelper.KEY_EMAIL, "")
            val phone = o.optString(CardStorageHelper.KEY_PHONE, "")
            val insta = o.optString(CardStorageHelper.KEY_INSTAGRAM, "")
            val web = o.optString(CardStorageHelper.KEY_WEBSITE, "")
            val addr = o.optString(CardStorageHelper.KEY_ADDRESS, "")
            val tag = o.optString(CardStorageHelper.KEY_TAG, "")

            val key = detKey(name, occ, email, phone, insta, web, addr)
            if (cloudKeys.contains(key) || localDetKeys.contains(key)) {
                skipped++
                continue
            }

            helper.insertCardWithTag(
                userId = currentUserId,
                name = name,
                occupation = occ,
                email = email,
                phone = phone,
                instagram = insta,
                website = web,
                address = addr,
                tag = tag
            )

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
                CardStorageHelper.KEY_TAG to tag,
                "source" to "restore",
                "createdAt" to formattedTime
            )

            dbRef.child(key).setValue(cardForFirebase).await()

            cloudKeys.add(key)
            localDetKeys.add(key)
            restored++
        }

        return RestoreResult(restoredCount = restored, skippedCount = skipped)
    }

    private fun detKey(
        name: String,
        occ: String,
        email: String,
        phone: String,
        insta: String,
        web: String,
        addr: String
    ): String {
        val normalized = listOf(
            name.trim(),
            occ.trim(),
            email.trim().lowercase(),
            phone.trim(),
            insta.trim(),
            web.trim(),
            addr.trim()
        ).joinToString("|")
        return sha256(normalized)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
