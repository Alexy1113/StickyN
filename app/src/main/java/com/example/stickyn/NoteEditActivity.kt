package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class NoteEditActivity : AppCompatActivity() {

    private lateinit var editTitle: EditText
    private lateinit var editTextNote: EditText
    private lateinit var buttonSave: Button
    private lateinit var sharedPrefs: SharedPreferences
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private var isBold = false
    private var isItalic = false
    private var isUnderline = false
    private var isStrikethrough = false
    private var isBulletList = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = applicationContext.getSharedPreferences("NoteWidgetPrefs", MODE_PRIVATE)
        
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        editTitle = findViewById(R.id.edit_widget_title)
        editTextNote = findViewById(R.id.edit_text_note)
        buttonSave = findViewById(R.id.button_save)

        setupFormattingButtons()
        setupTextFormattingLogic()

        handleIntent(intent)

        buttonSave.setOnClickListener {
            val titleText = editTitle.text.toString()
            val noteText = Html.toHtml(editTextNote.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
            
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                sharedPrefs.edit(commit = true) {
                    putString("widget_title_$appWidgetId", titleText)
                    putString("saved_note_text_$appWidgetId", noteText)
                }
                updateWidget()
                Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Error: Widget ID missing.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFormattingButtons() {
        val btnBold = findViewById<ImageButton>(R.id.button_bold)
        val btnItalic = findViewById<ImageButton>(R.id.button_italic)
        val btnUnderline = findViewById<ImageButton>(R.id.button_underline)
        val btnStrikethrough = findViewById<ImageButton>(R.id.button_strikethrough)
        val btnBullet = findViewById<ImageButton>(R.id.button_bullet_list)
        val btnImage = findViewById<ImageButton>(R.id.button_add_image)

        btnBold.setOnClickListener {
            if (editTextNote.hasSelection()) applySpanToSelection(StyleSpan(Typeface.BOLD))
            else { isBold = !isBold; btnBold.isActivated = isBold }
        }
        btnItalic.setOnClickListener {
            if (editTextNote.hasSelection()) applySpanToSelection(StyleSpan(Typeface.ITALIC))
            else { isItalic = !isItalic; btnItalic.isActivated = isItalic }
        }
        btnUnderline.setOnClickListener {
            if (editTextNote.hasSelection()) applySpanToSelection(UnderlineSpan())
            else { isUnderline = !isUnderline; btnUnderline.isActivated = isUnderline }
        }
        btnStrikethrough.setOnClickListener {
            if (editTextNote.hasSelection()) applySpanToSelection(StrikethroughSpan())
            else { isStrikethrough = !isStrikethrough; btnStrikethrough.isActivated = isStrikethrough }
        }
        btnBullet.setOnClickListener {
            if (editTextNote.hasSelection()) applyBulletToSelection()
            else {
                isBulletList = !isBulletList
                btnBullet.isActivated = isBulletList
                if (isBulletList) applyBulletAtCurrentLine()
            }
        }
        btnImage.setOnClickListener {
            Toast.makeText(this, "Image feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySpanToSelection(span: Any) {
        val start = editTextNote.selectionStart
        val end = editTextNote.selectionEnd
        val spannable = editTextNote.text
        val existingSpans = spannable.getSpans(start, end, span.javaClass)
        for (existingSpan in existingSpans) spannable.removeSpan(existingSpan)
        spannable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyBulletToSelection() {
        val start = editTextNote.selectionStart
        val end = editTextNote.selectionEnd
        val text = editTextNote.text
        val lines = text.substring(start, end).split("\n")
        val builder = SpannableStringBuilder()
        for (i in lines.indices) {
            if (!lines[i].startsWith("• ")) builder.append("• ")
            builder.append(lines[i])
            if (i < lines.size - 1) builder.append("\n")
        }
        text.replace(start, end, builder)
    }

    private fun setupTextFormattingLogic() {
        editTextNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before) {
                    val addedTextEnd = start + count
                    val spannable = editTextNote.text
                    if (isBold) spannable.setSpan(StyleSpan(Typeface.BOLD), start, addedTextEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (isItalic) spannable.setSpan(StyleSpan(Typeface.ITALIC), start, addedTextEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (isUnderline) spannable.setSpan(UnderlineSpan(), start, addedTextEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (isStrikethrough) spannable.setSpan(StrikethroughSpan(), start, addedTextEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editTextNote.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (isBulletList) {
                    editTextNote.post { applyBulletAtCurrentLine() }
                }
            }
            false
        }
    }

    private fun applyBulletAtCurrentLine() {
        val cursorPosition = editTextNote.selectionStart
        val text = editTextNote.text
        var lineStart = cursorPosition
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        if (lineStart + 2 <= text.length && text.substring(lineStart, lineStart + 2) == "• ") return
        text.insert(lineStart, "• ")
        editTextNote.setSelection(cursorPosition + 2)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val currentIntent = intent ?: return
        var incomingId = currentIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        
        if (incomingId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val data = currentIntent.data
            if (data != null && data.scheme == "widget") {
                incomingId = data.lastPathSegment?.toIntOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

        appWidgetId = incomingId

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            loadData()
        }
        
        editTextNote.requestFocus()
        editTextNote.setSelection(editTextNote.text.length)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editTextNote, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun loadData() {
        val savedTitle = sharedPrefs.getString("widget_title_$appWidgetId", "")
        val savedNote = sharedPrefs.getString("saved_note_text_$appWidgetId", "")
        editTitle.setText(savedTitle)
        if (!savedNote.isNullOrEmpty()) {
            editTextNote.setText(Html.fromHtml(savedNote, Html.FROM_HTML_MODE_LEGACY))
        } else {
            editTextNote.setText("")
        }
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.widget_list_view)
        updateAppWidget(this, appWidgetManager, appWidgetId)
    }
}
