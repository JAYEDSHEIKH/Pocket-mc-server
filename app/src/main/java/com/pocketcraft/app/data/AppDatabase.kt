package com.pocketcraft.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [ServerProfile::class],
    version = 1,
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
}
