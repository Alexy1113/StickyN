package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.Html
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.toColorInt

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
        // Use applicationContext and explicit pref name for absolute consistency
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        noteText = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
    }

    override fun onDestroy() {}

    override fun getCount(): Int = if (noteText.isEmpty()) 0 else 1

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        
        val formattedText = if (noteText.isNotEmpty()) {
            Html.fromHtml(noteText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            ""
        }
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
