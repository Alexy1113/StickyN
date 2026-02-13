package com.example.stickyn

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.InputStream
import kotlin.math.pow
import kotlin.math.sqrt

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

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScrolling = false
    private var initialSelectionStart = -1
    private var initialSelectionEnd = -1
    private var touchSlop = 0

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insertImageFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = applicationContext.getSharedPreferences("NoteWidgetPrefs", MODE_PRIVATE)
        
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        editTitle = findViewById(R.id.edit_widget_title)
        editTextNote = findViewById(R.id.edit_text_note)
        buttonSave = findViewById(R.id.button_save)

        // Make title underlined in the editor too
        editTitle.paintFlags = editTitle.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        // Use ArrowKeyMovementMethod but override touch for custom scroll behavior
        editTextNote.movementMethod = ArrowKeyMovementMethod.getInstance()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            editTextNote.setOnDragListener(null)
        }
        
        editTextNote.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isScrolling = false
                    initialSelectionStart = editTextNote.selectionStart
                    initialSelectionEnd = editTextNote.selectionEnd
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (!isScrolling && sqrt(dx.pow(2) + dy.pow(2)) > touchSlop) {
                        isScrolling = true
                    }
                    
                    if (isScrolling) {
                        // Manually scroll the view
                        val scrollY = v.scrollY
                        val targetY = (scrollY - dy).toInt()
                        val visibleHeight = v.height - v.paddingTop - v.paddingBottom
                        val maxScroll = (editTextNote.layout?.height ?: 0) - visibleHeight
                        v.scrollTo(v.scrollX, targetY.coerceIn(0, maxScroll.coerceAtLeast(0)))
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                        
                        // Keep cursor where it was before the gesture started
                        if (initialSelectionStart >= 0) {
                            editTextNote.setSelection(initialSelectionStart, initialSelectionEnd)
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isScrolling) {
                        isScrolling = false
                        v.performClick()
                        return@setOnTouchListener true
                    } else {
                        // Manual tap handling: move cursor without jumping/snapping
                        val offset = getOffsetForPosition(event.x, event.y)
                        val curX = editTextNote.scrollX
                        val curY = editTextNote.scrollY
                        
                        editTextNote.requestFocus()
                        editTextNote.setSelection(offset)
                        
                        // Use post to override the default "bring into view" behavior of setSelection
                        editTextNote.post {
                            editTextNote.scrollTo(curX, curY)
                        }
                        
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(editTextNote, InputMethodManager.SHOW_IMPLICIT)

                        val text = editTextNote.text
                        if (text is Spanned) {
                            val spans = text.getSpans(offset, offset, ClickableSpan::class.java)
                            if (spans.isNotEmpty()) {
                                spans[0].onClick(editTextNote)
                            }
                        }
                        v.performClick()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        editTextNote.setOnLongClickListener {
            val offset = getOffsetForPosition(lastTouchX, lastTouchY)
            val text = editTextNote.text
            if (text is Spanned) {
                val spans = text.getSpans(offset, offset, ImageSpan::class.java)
                if (spans.isNotEmpty()) {
                    return@setOnLongClickListener true
                }
            }
            false
        }

        setupFormattingButtons()
        setupTextFormattingLogic()

        handleIntent(intent)

        buttonSave.setOnClickListener {
            val titleText = editTitle.text.toString()
            val noteText = Html.toHtml(editTextNote.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
            
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val success = sharedPrefs.edit().apply {
                    putString("widget_title_$appWidgetId", titleText)
                    putString("saved_note_text_$appWidgetId", noteText)
                }.commit()
                
                if (success) {
                    updateWidget()
                    Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to save to disk.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Error: Widget ID missing.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOffsetForPosition(x: Float, y: Float): Int {
        val layout = editTextNote.layout ?: return 0
        val adjX = x - editTextNote.totalPaddingLeft + editTextNote.scrollX
        val adjY = y - editTextNote.totalPaddingTop + editTextNote.scrollY
        val line = layout.getLineForVertical(adjY.toInt())
        return layout.getOffsetForHorizontal(line, adjX)
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
            pickImageLauncher.launch(arrayOf("image/*"))
        }
    }

    private fun insertImageFromUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show()
                return
            }
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap != null) {
                val targetWidth = if (editTextNote.width > 0) {
                    editTextNote.width - editTextNote.paddingLeft - editTextNote.paddingRight
                } else {
                    (resources.displayMetrics.widthPixels * 0.8).toInt()
                }

                val scaledBitmap = scaleBitmap(bitmap, targetWidth)
                val drawable = scaledBitmap.toDrawable(resources)
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

                val selectionStart = editTextNote.selectionStart.coerceAtLeast(0)
                val selectionEnd = editTextNote.selectionEnd.coerceAtLeast(0)
                
                val builder = SpannableStringBuilder(editTextNote.text)
                
                // Add fewer empty lines (2 before, 3 after) as requested
                val insertionText = "\n\n \n\n\n"
                builder.replace(selectionStart, selectionEnd, insertionText)
                
                val imageIndex = selectionStart + 2
                
                val imageSpan = ImageSpan(drawable, uri.toString())
                builder.setSpan(imageSpan, imageIndex, imageIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        showFullscreenImage(uri)
                    }
                }
                builder.setSpan(clickableSpan, imageIndex, imageIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                editTextNote.setText(builder)
                editTextNote.setSelection(selectionStart + insertionText.length)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFullscreenImage(uri: Uri) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_fullscreen_image)

        val imageView = dialog.findViewById<ImageView>(R.id.fullscreen_image)
        val btnClose = dialog.findViewById<Button>(R.id.button_close)
        val btnDelete = dialog.findViewById<Button>(R.id.button_delete_image)

        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        
        btnDelete.setOnClickListener {
            deleteImageFromNote(uri.toString())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteImageFromNote(uriString: String) {
        val spannable = editTextNote.text
        val imageSpans = spannable.getSpans(0, spannable.length, ImageSpan::class.java)
        val clickableSpans = spannable.getSpans(0, spannable.length, ClickableSpan::class.java)

        for (span in imageSpans) {
            if (span.source == uriString) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                if (start != -1 && end != -1) {
                    for (cSpan in clickableSpans) {
                        if (spannable.getSpanStart(cSpan) == start) {
                            spannable.removeSpan(cSpan)
                        }
                    }
                    spannable.delete(start, end)
                    break
                }
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (maxWidth <= 0 || bitmap.width <= maxWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val height = (maxWidth * aspectRatio).toInt()
        return bitmap.scale(maxWidth, height, true)
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
                if (count != before) {
                    editTextNote.post { scrollToCursorIfNeeded() }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editTextNote.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val selectionStart = editTextNote.selectionStart
                val selectionEnd = editTextNote.selectionEnd
                
                if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                    val spannable = editTextNote.text
                    val rangeStart = if (keyCode == KeyEvent.KEYCODE_DEL) selectionStart - 1 else selectionStart
                    val rangeEnd = if (keyCode == KeyEvent.KEYCODE_DEL) selectionStart else selectionStart + 1
                    
                    if (rangeStart >= 0 && rangeEnd <= spannable.length) {
                        val imageSpans = spannable.getSpans(rangeStart, rangeEnd, ImageSpan::class.java)
                        if (imageSpans.isNotEmpty()) {
                            Toast.makeText(this, "Tap image to delete from fullscreen", Toast.LENGTH_SHORT).show()
                            return@setOnKeyListener true
                        }
                    }
                    
                    if (selectionStart != selectionEnd) {
                        val imageSpans = spannable.getSpans(selectionStart, selectionEnd, ImageSpan::class.java)
                        if (imageSpans.isNotEmpty()) {
                            Toast.makeText(this, "Selection contains images. Delete them individually.", Toast.LENGTH_SHORT).show()
                            return@setOnKeyListener true
                        }
                    }
                }

                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (isBulletList) {
                        editTextNote.post { applyBulletAtCurrentLine() }
                    }
                }
            }
            false
        }
    }

    private fun scrollToCursorIfNeeded() {
        val layout = editTextNote.layout ?: return
        val selectionStart = editTextNote.selectionStart
        if (selectionStart < 0) return
        
        val line = layout.getLineForOffset(selectionStart)
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        
        val currentScrollY = editTextNote.scrollY
        val editTextVisibleHeight = editTextNote.height - editTextNote.totalPaddingTop - editTextNote.totalPaddingBottom
        
        val isOutOfSight = lineTop < currentScrollY || lineBottom > (currentScrollY + editTextVisibleHeight)
        
        if (isOutOfSight) {
            val targetScrollY = (lineTop + lineBottom) / 2 - editTextVisibleHeight / 2
            val maxScrollY = layout.height - editTextVisibleHeight
            val safeScrollY = targetScrollY.coerceIn(0, maxScrollY.coerceAtLeast(0))
            
            editTextNote.scrollTo(0, safeScrollY)
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
            editTextNote.post {
                loadData()
            }
        }
        
        editTextNote.requestFocus()
        editTextNote.setSelection(editTextNote.text.length)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editTextNote, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun loadData() {
        val widgetTitleKey = "widget_title_$appWidgetId"
        val noteTextKey = "saved_note_text_$appWidgetId"
        
        editTitle.setText("")
        editTextNote.setText("")

        if (sharedPrefs.contains(widgetTitleKey) || sharedPrefs.contains(noteTextKey)) {
            val savedTitle = sharedPrefs.getString(widgetTitleKey, "")
            val savedNote = sharedPrefs.getString(noteTextKey, "")
            editTitle.setText(savedTitle)
            if (!savedNote.isNullOrEmpty()) {
                val imageGetter = Html.ImageGetter { source ->
                    try {
                        val uri = source.toUri()
                        val inputStream = contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (bitmap != null) {
                            val scaledBitmap = scaleBitmap(bitmap, editTextNote.width - editTextNote.paddingLeft - editTextNote.paddingRight)
                            val drawable = scaledBitmap.toDrawable(resources)
                            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                            return@ImageGetter drawable
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    null
                }

                val spanned = Html.fromHtml(savedNote, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
                val builder = SpannableStringBuilder(spanned)
                
                val imageSpans = builder.getSpans(0, builder.length, ImageSpan::class.java)
                for (span in imageSpans) {
                    val start = builder.getSpanStart(span)
                    val end = builder.getSpanEnd(span)
                    val uriStr = span.source ?: continue
                    val uri = uriStr.toUri()
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            showFullscreenImage(uri)
                        }
                    }
                    builder.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                
                editTextNote.setText(builder)
            }
        }
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, StickyNoteWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        
        for (id in ids) {
            updateAppWidget(this, appWidgetManager, id)
        }

        editTextNote.postDelayed({
            for (id in ids) {
                @Suppress("DEPRECATION")
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view)
            }
        }, 500)
    }
}
