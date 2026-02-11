package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set window size
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        findViewById<Button>(R.id.button_back).setOnClickListener {
            finish()
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, StickyNoteWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        val intent = Intent(this, StickyNoteWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        sendBroadcast(intent)
    }
}
