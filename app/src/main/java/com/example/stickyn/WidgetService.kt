package com.example.stickyn

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var noteText: String = ""
    private var appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        noteText = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
    }

    override fun onDestroy() {}

    // Only show item if text is not empty, otherwise ListView empty view kicks in
    override fun getCount(): Int = if (noteText.isEmpty()) 0 else 1

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        views.setTextViewText(R.id.item_text, noteText)
        
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        val themeMode = sharedPrefs.getString("widget_theme_mode", "light")
        
        val textColor = when (themeMode) {
            "dark" -> "#FFFFFF".toColorInt()
            else -> "#000000".toColorInt()
        }
        views.setTextColor(R.id.item_text, textColor)

        // Important: Use fillInIntent to pass the widget ID to the template
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
