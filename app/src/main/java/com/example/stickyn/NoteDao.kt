package com.example.stickyn

import androidx.room.*

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND appWidgetId = -1")
    suspend fun getDeletedNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}
