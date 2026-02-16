package com.example.stickyn

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set window size
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val readmeText = findViewById<TextView>(R.id.text_readme)
        readmeText.text = Html.fromHtml(getString(R.string.readme), Html.FROM_HTML_MODE_LEGACY)
        readmeText.movementMethod = LinkMovementMethod.getInstance()

        findViewById<Button>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.button_restore).setOnClickListener {
            startActivity(Intent(this, RestoreActivity::class.java))
            finish()
        }
    }
}
