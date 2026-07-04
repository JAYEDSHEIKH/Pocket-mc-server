package com.pocketcraft.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ServerProfile::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serverProfileDao(): ServerProfileDao

    class Converters {
        @TypeConverter fun fromLoaderType(value: LoaderType): String = value.name
        @TypeConverter fun toLoaderType(value: String): LoaderType = LoaderType.valueOf(value)

        @TypeConverter fun fromServerStatus(value: ServerStatus): String = value.name
        @TypeConverter fun toServerStatus(value: String): ServerStatus = ServerStatus.valueOf(value)
    }

    companion object {
        /** Adds customStartCommand (nullable TEXT) and serverNotes (TEXT NOT NULL). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN customStartCommand TEXT")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN serverNotes TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
