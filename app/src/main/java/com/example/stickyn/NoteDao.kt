package com.example.stickyn

import androidx.room.*

/**
 * Data Access Object (DAO) for the [Note] entity.
 * Provides methods to interact with the persistent note database.
 */
@Dao
interface NoteDao {
    /**
     * Retrieves all notes that were backed up from deleted widgets.
     * These notes have an appWidgetId of -1.
     */
    @Query("SELECT * FROM notes WHERE appWidgetId = -1")
    suspend fun getDeletedNotes(): List<Note>

    /**
     * Retrieves a single note by its unique database ID.
     */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    /**
     * Inserts a new note or replaces an existing one.
     * @return The row ID of the inserted record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    /**
     * Updates an existing note record.
     */
    @Update
    suspend fun update(note: Note)

    /**
     * Deletes a specific note record from the database.
     */
    @Delete
    suspend fun delete(note: Note)
}
