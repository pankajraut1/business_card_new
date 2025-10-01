package com.example.visitingcard

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteException

class CardStorageHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_CARDS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }

    fun saveCard(userId: String, card: Map<String, String>): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_USER_ID, userId)
            put(KEY_NAME, card[KEY_NAME])
            put(KEY_OCCUPATION, card[KEY_OCCUPATION])
            put(KEY_EMAIL, card[KEY_EMAIL])
            put(KEY_PHONE, card[KEY_PHONE])
            put(KEY_INSTAGRAM, card[KEY_INSTAGRAM])
            put(KEY_WEBSITE, card[KEY_WEBSITE])
            put(KEY_ADDRESS, card[KEY_ADDRESS])
        }

        val id = db.insert(TABLE_CARDS, null, values)
        db.close()
        return id
    }

    fun insertCard(
        userId: String,
        name: String,
        occupation: String,
        email: String,
        phone: String,
        instagram: String,
        website: String,
        address: String
    ): Long {
        val db = this.writableDatabase
        val values = ContentValues()

        values.put(KEY_USER_ID, userId)
        values.put(KEY_NAME, name)
        values.put(KEY_OCCUPATION, occupation)
        values.put(KEY_EMAIL, email)
        values.put(KEY_PHONE, phone)
        values.put(KEY_INSTAGRAM, instagram)
        values.put(KEY_WEBSITE, website)
        values.put(KEY_ADDRESS, address)

        val id = db.insert(TABLE_CARDS, null, values)
        db.close()
        return id
    }

    fun getUserCards(userId: String): List<Map<String, String>> {
        val cards = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor: Cursor = try {
            // Preferred: newest first by timestamp
            val selectQuery =
                "SELECT * FROM $TABLE_CARDS WHERE $KEY_USER_ID = ? ORDER BY $KEY_TIMESTAMP DESC"
            db.rawQuery(selectQuery, arrayOf(userId))
        } catch (e: SQLiteException) {
            // Backward compatibility: some older installs may lack the timestamp column
            val fallbackQuery = "SELECT * FROM $TABLE_CARDS WHERE $KEY_USER_ID = ?"
            db.rawQuery(fallbackQuery, arrayOf(userId))
        }
        cursor.use { c ->
            if (c.moveToFirst()) {
                do {
                    val card = hashMapOf<String, String>()
                    card[KEY_ID] = c.getLong(c.getColumnIndexOrThrow(KEY_ID)).toString()
                    card[KEY_NAME] = c.getString(c.getColumnIndexOrThrow(KEY_NAME))
                    card[KEY_OCCUPATION] = c.getString(c.getColumnIndexOrThrow(KEY_OCCUPATION))
                    card[KEY_EMAIL] = c.getString(c.getColumnIndexOrThrow(KEY_EMAIL))
                    card[KEY_PHONE] = c.getString(c.getColumnIndexOrThrow(KEY_PHONE))
                    card[KEY_INSTAGRAM] = c.getString(c.getColumnIndexOrThrow(KEY_INSTAGRAM))
                    card[KEY_WEBSITE] = c.getString(c.getColumnIndexOrThrow(KEY_WEBSITE))
                    card[KEY_ADDRESS] = c.getString(c.getColumnIndexOrThrow(KEY_ADDRESS))
                    cards.add(card)
                } while (c.moveToNext())
            }
        }
        db.close()
        return cards
    }

    fun deleteCard(id: Long): Boolean {
        val db = this.writableDatabase
        val deleted = db.delete(TABLE_CARDS, "$KEY_ID = ?", arrayOf(id.toString())) > 0
        db.close()
        return deleted
    }

    fun existsCard(
        userId: String,
        name: String,
        occupation: String,
        email: String,
        phone: String,
        instagram: String,
        website: String,
        address: String
    ): Boolean {
        val db = this.readableDatabase
        val sql = "SELECT 1 FROM $TABLE_CARDS WHERE $KEY_USER_ID = ? AND $KEY_NAME = ? AND $KEY_OCCUPATION = ? AND $KEY_EMAIL = ? AND $KEY_PHONE = ? AND $KEY_INSTAGRAM = ? AND $KEY_WEBSITE = ? AND $KEY_ADDRESS = ? LIMIT 1"
        val args = arrayOf(userId, name, occupation, email, phone, instagram, website, address)
        val cursor = db.rawQuery(sql, args)
        val exists = cursor.use { it.moveToFirst() }
        db.close()
        return exists
    }

    fun clearUserCards(userId: String) {
        val db = this.writableDatabase
        db.delete(TABLE_CARDS, "$KEY_USER_ID = ?", arrayOf(userId))
        db.close()
    }

    fun deleteCardByContent(
        userId: String,
        name: String,
        occupation: String,
        email: String,
        phone: String,
        instagram: String,
        website: String,
        address: String
    ): Int {
        val db = this.writableDatabase
        val where = "$KEY_USER_ID = ? AND $KEY_NAME = ? AND $KEY_OCCUPATION = ? AND $KEY_EMAIL = ? AND $KEY_PHONE = ? AND $KEY_INSTAGRAM = ? AND $KEY_WEBSITE = ? AND $KEY_ADDRESS = ?"
        val args = arrayOf(userId, name, occupation, email, phone, instagram, website, address)
        val rows = db.delete(TABLE_CARDS, where, args)
        db.close()
        return rows
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "VisitingCards.db"

        const val TABLE_CARDS = "cards"
        const val KEY_ID = "id"
        const val KEY_USER_ID = "user_id"
        const val KEY_NAME = "name"
        const val KEY_OCCUPATION = "occupation"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
        const val KEY_INSTAGRAM = "instagram"
        const val KEY_WEBSITE = "website"
        const val KEY_ADDRESS = "address"
        const val KEY_TIMESTAMP = "timestamp"

        private const val CREATE_TABLE_CARDS = """
            CREATE TABLE $TABLE_CARDS(
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_USER_ID TEXT NOT NULL,
                $KEY_NAME TEXT,
                $KEY_OCCUPATION TEXT,
                $KEY_EMAIL TEXT,
                $KEY_PHONE TEXT,
                $KEY_INSTAGRAM TEXT,
                $KEY_WEBSITE TEXT,
                $KEY_ADDRESS TEXT,
                $KEY_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """
    }
}
