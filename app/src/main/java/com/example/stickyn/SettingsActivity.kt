package com.example.stickyn

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity that displays app information (Readme) and provides links to other administrative screens.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set window size for a floating dialog appearance
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val readmeText = findViewById<TextView>(R.id.text_readme)
        // Parse HTML readme from strings.xml
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
