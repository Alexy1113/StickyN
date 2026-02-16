package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    // Update database
                    val updatedNote = it.copy(appWidgetId = newWidgetId)
                    db.noteDao().update(updatedNote)

                    // Migrate data from DB to SharedPreferences for the new widget ID
                    sharedPrefs.edit().apply {
                        putString("widget_title_$newWidgetId", it.title)
                        putString("saved_note_text_$newWidgetId", it.content)
                    }.apply()

                    // Update the widget UI
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, newWidgetId)
                }
            }
        }
    }
}
