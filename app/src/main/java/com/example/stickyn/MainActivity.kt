package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var editTitle: EditText
    private lateinit var editTextNote: EditText
    private lateinit var buttonSave: Button
    private lateinit var sharedPrefs: SharedPreferences
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("NoteWidgetPrefs", MODE_PRIVATE)
        
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        editTitle = findViewById(R.id.edit_widget_title)
        editTextNote = findViewById(R.id.edit_text_note)
        buttonSave = findViewById(R.id.button_save)

        handleIntent(intent)

        buttonSave.setOnClickListener {
            val titleText = editTitle.text.toString()
            val noteText = editTextNote.text.toString()
            
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Synchronous save to prevent race conditions with widget update
                sharedPrefs.edit(commit = true) {
                    putString("widget_title_$appWidgetId", titleText)
                    putString("saved_note_text_$appWidgetId", noteText)
                }
                updateWidget()
                Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Error: No Widget found to save to.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Try to get ID from intent
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Fallback: If opened from Launcher, pick the first existing widget
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, StickyNoteWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) {
                appWidgetId = ids[0]
            }
        }

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            loadData()
        }
        
        // UI Polish
        editTextNote.requestFocus()
        editTextNote.setSelection(editTextNote.text.length)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editTextNote, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun loadData() {
        val savedTitle = sharedPrefs.getString("widget_title_$appWidgetId", "")
        val savedNote = sharedPrefs.getString("saved_note_text_$appWidgetId", "")
        editTitle.setText(savedTitle)
        editTextNote.setText(savedNote)
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateAppWidget(this, appWidgetManager, appWidgetId)
    }
}
