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

class PinWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use a simple view
        setContentView(View(this))

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, StickyNoteWidget::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            
            // We use a unique request code to avoid any collision
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

            val success = appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            if (success) {
                // Inform the user that a dialog should have appeared
                Toast.makeText(this, "Add the widget to your home screen", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not show pinning dialog", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Pinning not supported by your launcher", Toast.LENGTH_SHORT).show()
        }

        // We'll keep the activity alive for a bit longer and only finish if it's not the one
        // currently "hosting" the system dialog's context (though the dialog is system-level).
        // On some devices, finishing too fast kills the request.
        window.decorView.postDelayed({
            if (!isFinishing) finish()
        }, 3000)
    }
}
