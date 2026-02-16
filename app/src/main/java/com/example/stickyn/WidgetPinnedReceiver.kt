package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver that catches the system callback when a widget is successfully pinned to the home screen.
 * Handles the migration of note data from the backup database back to SharedPreferences for restored widgets.
 */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val newWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val noteId = intent.getIntExtra("NOTE_ID", -1)

        if (newWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && noteId != -1) {
            val db = AppDatabase.getDatabase(context)
            val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)

            CoroutineScope(Dispatchers.IO).launch {
                val note = db.noteDao().getNoteById(noteId)
                note?.let {
                    // Update the note record with its new active AppWidget ID
                    val updatedNote = it.copy(appWidgetId = newWidgetId)
                    db.noteDao().update(updatedNote)

                    // Migrate data from DB to SharedPreferences so the widget can display it immediately
                    sharedPrefs.edit().apply {
                        putString("widget_title_$newWidgetId", it.title)
                        putString("saved_note_text_$newWidgetId", it.content)
                        putInt("widget_base_text_size_$newWidgetId", it.textSize.toInt())
                    }.commit() // Use commit() to ensure it's saved before updateAppWidget

                    // Trigger a UI refresh for the newly created widget instance
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, newWidgetId)
                    
                    // Force refresh of the list view data
                    appWidgetManager.notifyAppWidgetViewDataChanged(newWidgetId, R.id.widget_list_view)
                }
            }
        }
    }
}
