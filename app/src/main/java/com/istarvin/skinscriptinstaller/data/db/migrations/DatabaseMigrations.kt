package com.istarvin.skinscriptinstaller.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val availablePaths: Set<Pair<Int, Int>> = setOf(1 to 2, 2 to 3, 3 to 4)

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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create heroes table
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS heroes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL
                )"""
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_heroes_name ON heroes(name)"
            )

            // Create skins table
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS skins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    heroId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    FOREIGN KEY(heroId) REFERENCES heroes(id) ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_skins_heroId ON skins(heroId)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_skins_heroId_name ON skins(heroId, name)"
            )

            // Add classification columns to skin_scripts
            db.execSQL(
                "ALTER TABLE skin_scripts ADD COLUMN heroId INTEGER DEFAULT NULL REFERENCES heroes(id) ON DELETE SET NULL"
            )
            db.execSQL(
                "ALTER TABLE skin_scripts ADD COLUMN originalSkinId INTEGER DEFAULT NULL REFERENCES skins(id) ON DELETE SET NULL"
            )
            db.execSQL(
                "ALTER TABLE skin_scripts ADD COLUMN replacementSkinId INTEGER DEFAULT NULL REFERENCES skins(id) ON DELETE SET NULL"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_skin_scripts_heroId ON skin_scripts(heroId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_skin_scripts_originalSkinId ON skin_scripts(originalSkinId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_skin_scripts_replacementSkinId ON skin_scripts(replacementSkinId)"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE heroes ADD COLUMN heroIcon TEXT")
        }
    }
}
