package com.example.stickyn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PinWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, StickyNoteWidget::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            // Use the widget provider itself as the callback receiver
            // This avoids needing a separate receiver in the Manifest
            val successCallback = PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, StickyNoteWidget::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val success = appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            if (!success) {
                Toast.makeText(this, "Could not show Add to Home Screen dialog", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Pinning not supported by your launcher", Toast.LENGTH_SHORT).show()
        }

        // Use a longer delay to ensure the system dialog has time to pop up
        // before this background activity closes.
        window.decorView.postDelayed({
            if (!isFinishing) finish()
        }, 1500)
    }
}
