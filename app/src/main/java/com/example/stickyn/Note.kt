package com.example.stickyn

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a note record in the Room database.
 * Used to store backup copies of notes when a widget is deleted.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Title of the note. */
    val title: String,
    /** HTML-formatted content of the note. */
    val content: String,
    /** The original AppWidget ID this note belonged to (if any). */
    val appWidgetId: Int,
    /** Text size of the note. */
    val textSize: Float = 16f
)
