package ai.venice.vaultview

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VaultDb(ctx: Context) : SQLiteOpenHelper(ctx, "vault.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE media (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                path TEXT NOT NULL,
                name TEXT NOT NULL,
                mime TEXT,
                kind TEXT,
                size INTEGER,
                sha TEXT UNIQUE,
                ts INTEGER,
                suspicious INTEGER DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_ts ON media(ts DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
}