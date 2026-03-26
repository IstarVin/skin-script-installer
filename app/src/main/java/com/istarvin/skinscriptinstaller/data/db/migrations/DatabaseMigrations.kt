package com.istarvin.skinscriptinstaller.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE installations ADD COLUMN userId INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_installations_userId ON installations(userId)"
            )
        }
    }
}
