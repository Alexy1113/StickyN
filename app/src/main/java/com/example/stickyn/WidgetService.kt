package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.Html
import android.text.Spanned
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import java.util.regex.Pattern

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

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        noteText = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
    }

    override fun onDestroy() {}

    override fun getCount(): Int {
        val plainText = Html.fromHtml(noteText, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        val hasImage = noteText.contains("<img", ignoreCase = true)
        return if (plainText.isNotEmpty() || hasImage) 1 else 0
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        val themeMode = sharedPrefs.getString("widget_theme_mode", "light")
        val textColor = when (themeMode) {
            "dark" -> "#FFFFFF".toColorInt()
            "matrix" -> "#00FF41".toColorInt()
            else -> "#000000".toColorInt()
        }

        val imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\">]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE)
        val matcher = imgPattern.matcher(noteText)

        if (matcher.find()) {
            val imgSource = matcher.group(1)
            val beforeHtml = noteText.substring(0, matcher.start())
            val afterHtml = noteText.substring(matcher.end())

            // Handle Top Text
            val topSpanned = Html.fromHtml(beforeHtml, Html.FROM_HTML_MODE_LEGACY)
            val trimmedTop = trimSpanned(topSpanned)
            if (trimmedTop.isNotEmpty()) {
                views.setTextViewText(R.id.item_text_top, trimmedTop)
                views.setTextColor(R.id.item_text_top, textColor)
                views.setViewVisibility(R.id.item_text_top, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.item_text_top, View.GONE)
            }

            // Handle Image
            var hasImage = false
            if (!imgSource.isNullOrEmpty()) {
                try {
                    val uri = imgSource.toUri()
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.item_image, bitmap)
                        views.setViewVisibility(R.id.item_image, View.VISIBLE)
                        hasImage = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (!hasImage) views.setViewVisibility(R.id.item_image, View.GONE)

            // Handle Bottom Text
            val bottomSpanned = Html.fromHtml(afterHtml, Html.FROM_HTML_MODE_LEGACY)
            val trimmedBottom = trimSpanned(bottomSpanned)
            if (trimmedBottom.isNotEmpty()) {
                views.setTextViewText(R.id.item_text_bottom, trimmedBottom)
                views.setTextColor(R.id.item_text_bottom, textColor)
                views.setViewVisibility(R.id.item_text_bottom, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.item_text_bottom, View.GONE)
            }

        } else {
            val allSpanned = Html.fromHtml(noteText, Html.FROM_HTML_MODE_LEGACY)
            val trimmedAll = trimSpanned(allSpanned)
            views.setTextViewText(R.id.item_text_top, trimmedAll)
            views.setTextColor(R.id.item_text_top, textColor)
            views.setViewVisibility(R.id.item_text_top, if (trimmedAll.isNotEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.item_image, View.GONE)
            views.setViewVisibility(R.id.item_text_bottom, View.GONE)
        }

        val fillInIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickFillInIntent(R.id.item_text_top, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_text_bottom, fillInIntent)

        return views
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
        return s.subSequence(start, end)
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
