package com.example.stickyn

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val appWidgetId: Int = -1, // -1 означает, что виджет не на главном экране
    val isArchived: Boolean = false
)
