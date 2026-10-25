package com.powermath.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema version 1.
 * Migration policy: never use destructive migration. When the schema changes, bump [version]
 * and register an explicit Migration via `.addMigrations(...)` in [get], so users keep their
 * workspace and history. (exportSchema is off to avoid requiring a schema directory.)
 */
@Database(entities = [HistoryEntity::class, VariableEntity::class, FunctionEntity::class], version = 1, exportSchema = false)
abstract class PowerMathDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        @Volatile private var instance: PowerMathDatabase? = null

        fun get(context: Context): PowerMathDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PowerMathDatabase::class.java, "powermath.db")
                .build().also { instance = it }
        }
    }
}
