package com.example.stickyn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent activity that triggers the system "Add Widget" dialog.
 * This acts as the main entry point when the user clicks the app icon.
 */
class PinWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use a simple view since this activity is translucent and transient
        setContentView(View(this))

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, StickyNoteWidget::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            
            // Unique request code for the broadcast callback
            val requestCode = 42
            val intent = Intent(this, StickyNoteWidget::class.java).apply {
                action = StickyNoteWidget.ACTION_WIDGET_PINNED
            }

            val successCallback = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )

            // Request the OS to show the widget pinning dialog
            val success = appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            if (!success) {
                Toast.makeText(this, "Could not show pinning dialog", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Pinning not supported by your launcher", Toast.LENGTH_SHORT).show()
        }

        // Delay finishing to ensure the system dialog has time to initialize
        window.decorView.postDelayed({
            if (!isFinishing) finish()
        }, 1000)
    }
}
