package com.powermath.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    /** Bounded query so a huge history never makes the list slow. */
    @Query("SELECT * FROM history ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM variables ORDER BY createdAt ASC")
    suspend fun variables(): List<VariableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariable(v: VariableEntity)

    @Query("DELETE FROM variables WHERE name = :name")
    suspend fun deleteVariable(name: String)

    @Query("SELECT * FROM functions ORDER BY createdAt ASC")
    suspend fun functions(): List<FunctionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFunction(f: FunctionEntity)

    @Query("DELETE FROM functions WHERE name = :name")
    suspend fun deleteFunction(name: String)
}
