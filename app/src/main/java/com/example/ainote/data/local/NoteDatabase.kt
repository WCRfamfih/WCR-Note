package com.example.ainote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, NoteKnowledgeScopeEntity::class], version = 5, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: NoteDatabase? = null

        fun getInstance(context: Context): NoteDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "ai_note.db"
                )
                    .addMigrations(Migration1To2)
                    .addMigrations(Migration2To3)
                    .addMigrations(Migration3To4)
                    .addMigrations(Migration4To5)
                    .build()
                    .also { instance = it }
            }
        }

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN folderName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN contentType TEXT NOT NULL DEFAULT 'note'")
            }
        }

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_knowledge_scopes (
                        noteId INTEGER NOT NULL PRIMARY KEY,
                        enabledFolderNames TEXT NOT NULL DEFAULT '',
                        disabledFolderNames TEXT NOT NULL DEFAULT '',
                        enabledKnowledgeIds TEXT NOT NULL DEFAULT '',
                        disabledKnowledgeIds TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isGlobalKnowledge INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
