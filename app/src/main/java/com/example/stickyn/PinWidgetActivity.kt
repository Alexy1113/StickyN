package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PinWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use a transparent view to ensure the activity is considered "foreground"
        setContentView(View(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val myProvider = ComponentName(this, StickyNoteWidget::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            } else {
                Toast.makeText(this, "Launcher pinning not supported", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Give the system a moment to show the dialog before finishing
        window.decorView.postDelayed({
            finish()
        }, 500)
    }
}
