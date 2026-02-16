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
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import android.text.style.ImageSpan

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetItemFactory(applicationContext, intent)
    }
}

class WidgetItemFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var noteText: String = ""
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

    override fun onDataSetChanged() {
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        noteText = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
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
                if (cleaned.isNotEmpty()) {
                    segments.add(NoteSegment.Text(cleaned))
                }
            }

            span.source?.let {
                segments.add(NoteSegment.Image(it))
            }

            lastEnd = end
        }

        if (lastEnd < fullSpanned.length) {
            val remainingText = fullSpanned.subSequence(lastEnd, fullSpanned.length)
            val cleaned = cleanText(remainingText)
            if (cleaned.isNotEmpty()) {
                segments.add(NoteSegment.Text(cleaned))
            }
        }
    }

    private fun cleanText(s: CharSequence): CharSequence {
        val sb = SpannableStringBuilder(s)
        var i = 0
        while (i < sb.length) {
            if (sb[i] == '\uFFFC') {
                sb.delete(i, i + 1)
            } else {
                i++
            }
        }
        return trimSpanned(sb)
    }

    override fun onDestroy() {}

    override fun getCount(): Int = segments.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= segments.size) return RemoteViews(context.packageName, R.layout.widget_note_item)

        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        val segment = segments[position]

        views.setViewVisibility(R.id.item_text_top, View.GONE)
        views.setViewVisibility(R.id.item_image, View.GONE)
        views.setViewVisibility(R.id.item_text_bottom, View.GONE)

        when (segment) {
            is NoteSegment.Text -> {
                views.setTextViewText(R.id.item_text_top, segment.content)
                // Text color is now handled by XML using @color/widget_text
                views.setViewVisibility(R.id.item_text_top, View.VISIBLE)
            }
            is NoteSegment.Image -> {
                try {
                    val uri = segment.uriString.toUri()
                    val maxWidth = 600
                    val maxHeight = 800
                    
                    val bitmap = decodeSampledBitmapFromUri(uri, maxWidth, maxHeight)
                    if (bitmap != null) {
                        val finalBitmap = scaleBitmap(bitmap, maxWidth)
                        views.setImageViewBitmap(R.id.item_image, finalBitmap)
                        views.setViewVisibility(R.id.item_image, View.VISIBLE)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val fillInIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickFillInIntent(R.id.item_text_top, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_image, fillInIntent)

        return views
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (maxWidth <= 0 || bitmap.width <= maxWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val height = (maxWidth * aspectRatio).toInt()
        return bitmap.scale(maxWidth, height, true)
    }

    private fun trimSpanned(s: CharSequence): CharSequence {
        var start = 0
        var end = s.length
        while (start < end && Character.isWhitespace(s[start])) {
            start++
        }
        while (end > start && Character.isWhitespace(s[end - 1])) {
            end--
        }
        return if (start < end) s.subSequence(start, end) else ""
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
