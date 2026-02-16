package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Html
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.scale
import androidx.core.net.toUri
import android.text.style.ImageSpan

/**
 * Service that provides the RemoteViewsFactory for the scrollable list in the widget.
 */
class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetItemFactory(applicationContext, intent)
    }
}

/**
 * Factory class that generates individual items (text or images) for the widget's ListView.
 */
class WidgetItemFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var noteText: String = ""
    private var baseTextSize: Int = 18
    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private sealed class NoteSegment {
        data class Text(val content: CharSequence) : NoteSegment()
        data class Image(val uriString: String) : NoteSegment()
    }

    private var segments = mutableListOf<NoteSegment>()

    override fun onCreate() {}

    /** Triggered when the widget data is refreshed. */
    override fun onDataSetChanged() {
        // Use MODE_MULTI_PROCESS to ensure we see the data just saved by the Activity
        @Suppress("DEPRECATION")
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_MULTI_PROCESS)
        
        noteText = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
        baseTextSize = sharedPrefs.getInt("widget_base_text_size_$appWidgetId", 18)
        parseSegments()
    }

    private fun parseSegments() {
        segments.clear()
        if (noteText.isEmpty()) return

        val imageGetter = Html.ImageGetter { ColorDrawable(Color.TRANSPARENT) }
        val fullSpanned = Html.fromHtml(noteText, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
        val imageSpans = fullSpanned.getSpans(0, fullSpanned.length, ImageSpan::class.java)
        val sortedSpans = imageSpans.sortedBy { fullSpanned.getSpanStart(it) }

        var lastEnd = 0
        for (span in sortedSpans) {
            val start = fullSpanned.getSpanStart(span)
            val end = fullSpanned.getSpanEnd(span)

            if (start > lastEnd) {
                val textPart = fullSpanned.subSequence(lastEnd, start)
                val cleaned = cleanText(textPart)
                if (cleaned.isNotEmpty()) segments.add(NoteSegment.Text(cleaned))
            }
            span.source?.let { segments.add(NoteSegment.Image(it)) }
            lastEnd = end
        }

        if (lastEnd < fullSpanned.length) {
            val remainingText = fullSpanned.subSequence(lastEnd, fullSpanned.length)
            val cleaned = cleanText(remainingText)
            if (cleaned.isNotEmpty()) segments.add(NoteSegment.Text(cleaned))
        }
    }

    private fun cleanText(s: CharSequence): CharSequence {
        val sb = SpannableStringBuilder(s)
        var i = 0
        while (i < sb.length) {
            if (sb[i] == '\uFFFC') sb.delete(i, i + 1) else i++
        }
        return trimSpanned(sb)
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= segments.size) return RemoteViews(context.packageName, R.layout.widget_note_item)

        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        val segment = segments[position]

        views.setViewVisibility(R.id.item_text_top, View.GONE)
        views.setViewVisibility(R.id.item_image, View.GONE)

        when (segment) {
            is NoteSegment.Text -> {
                views.setTextViewText(R.id.item_text_top, segment.content)
                views.setTextViewTextSize(R.id.item_text_top, TypedValue.COMPLEX_UNIT_SP, baseTextSize.toFloat())
                views.setViewVisibility(R.id.item_text_top, View.VISIBLE)
            }
            is NoteSegment.Image -> {
                try {
                    val bitmap = decodeSampledBitmapFromUri(segment.uriString.toUri(), 600, 800)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.item_image, scaleBitmap(bitmap, 600))
                        views.setViewVisibility(R.id.item_image, View.VISIBLE)
                    }
                } catch (e: Exception) {}
            }
        }

        val fillInIntent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
        views.setOnClickFillInIntent(R.id.item_text_top, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_image, fillInIntent)

        return views
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (maxWidth <= 0 || bitmap.width <= maxWidth) return bitmap
        return bitmap.scale(maxWidth, (maxWidth * (bitmap.height.toFloat() / bitmap.width)).toInt(), true)
    }

    private fun trimSpanned(s: CharSequence): CharSequence {
        var start = 0; var end = s.length
        while (start < end && Character.isWhitespace(s[start])) start++
        while (end > start && Character.isWhitespace(s[end - 1])) end--
        return if (start < end) s.subSequence(start, end) else ""
    }

    override fun onDestroy() { segments.clear() }
    override fun getCount(): Int = segments.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
