package com.powermath.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One successful calculation. Failed calculations are never stored. */
@Entity(tableName = "history", indices = [Index("timestamp")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    /** ResultMode name active when the result was produced (EXACT, DECIMAL, SCIENTIFIC). */
    val mode: String,
    val timestamp: Long,
    /** OutcomeKind name (VALUE, SOLUTION, ALGEBRA, DEFINED, BOOLEAN). */
    val kind: String
)

/** A workspace variable: the source text plus its exact value in re-parseable linear form. */
@Entity(tableName = "variables")
data class VariableEntity(
    @PrimaryKey val name: String,
    val source: String,
    val valueText: String,
    val createdAt: Long
)

@Entity(tableName = "functions")
data class FunctionEntity(
    @PrimaryKey val name: String,
    /** Comma-separated parameter names. */
    val params: String,
    val body: String,
    val createdAt: Long
)
