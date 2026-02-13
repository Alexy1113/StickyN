package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.Html
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

    override fun getCount(): Int = if (noteText.isEmpty()) 0 else 1

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        
        // Extract first image URI from HTML if exists
        val imgMatcher = Pattern.compile("<img src=\"([^\"]+)\"").matcher(noteText)
        if (imgMatcher.find()) {
            val imgSource = imgMatcher.group(1)
            try {
                val uri = imgSource.toUri()
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.item_image, bitmap)
                    views.setViewVisibility(R.id.item_image, View.VISIBLE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                views.setViewVisibility(R.id.item_image, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.item_image, View.GONE)
        }

        // Strip img tags for TextView to avoid OBJ placeholder
        val textWithoutImages = noteText.replace("<img[^>]*>".toRegex(), "")
        val formattedText = Html.fromHtml(textWithoutImages, Html.FROM_HTML_MODE_LEGACY)

        views.setTextViewText(R.id.item_text, formattedText)
        
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        val themeMode = sharedPrefs.getString("widget_theme_mode", "light")
        
        val textColor = when (themeMode) {
            "dark" -> "#FFFFFF".toColorInt()
            "matrix" -> "#00FF41".toColorInt()
            else -> "#000000".toColorInt()
        }
        views.setTextColor(R.id.item_text, textColor)

        val fillInIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickFillInIntent(R.id.item_text, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
