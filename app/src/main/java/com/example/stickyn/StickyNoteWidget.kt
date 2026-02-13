package com.example.stickyn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

class StickyNoteWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val sharedPrefs = context.applicationContext.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            for (appWidgetId in appWidgetIds) {
                remove("widget_title_$appWidgetId")
                remove("saved_note_text_$appWidgetId")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // This is a safety check. If for some reason the system is passing dummy data
        // when pinning, we should ensure the NoteEditActivity handles it.
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val editIntent = Intent(context, NoteEditActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = "widget://note/$appWidgetId".toUri()
            }
            context.startActivity(editIntent)
        }
    }
}

fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

    val views = RemoteViews(context.packageName, R.layout.widget_note_layout)
    val sharedPrefs = context.applicationContext.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
    
    // Ensure we default to an empty string if no data exists, rather than any dummy values.
    val widgetTitle = sharedPrefs.getString("widget_title_$appWidgetId", "")
    val themeMode = sharedPrefs.getString("widget_theme_mode", "light")

    val backgroundColor = when (themeMode) {
        "dark" -> "#333333".toColorInt()
        "matrix" -> "#000000".toColorInt()
        else -> "#FFFFDD".toColorInt()
    }
    val textColor = when (themeMode) {
        "dark" -> Color.WHITE
        "matrix" -> "#00FF41".toColorInt()
        else -> Color.BLACK
    }

    views.setInt(R.id.widget_layout, "setBackgroundColor", backgroundColor)
    views.setTextColor(R.id.widget_title_text, textColor)
    views.setTextColor(R.id.appwidget_text, textColor)
    views.setInt(R.id.button_menu, "setColorFilter", textColor)
    views.setInt(R.id.button_add_note, "setColorFilter", textColor)
    views.setInt(R.id.button_edit_note, "setColorFilter", textColor)

    views.setTextViewText(R.id.widget_title_text, widgetTitle ?: "")
    views.setViewVisibility(R.id.widget_title_text, View.VISIBLE)

    val serviceIntent = Intent(context, WidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = "widget://service/$appWidgetId".toUri()
    }
    
    views.setRemoteAdapter(appWidgetId, R.id.widget_list_view, serviceIntent)
    views.setEmptyView(R.id.widget_list_view, R.id.appwidget_text)

    val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    val editIntent = Intent(context, NoteEditActivity::class.java).apply {
        action = "com.example.stickyn.ACTION_EDIT_$appWidgetId"
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "widget://note/$appWidgetId".toUri()
    }
    val editPendingIntent = PendingIntent.getActivity(
        context, appWidgetId * 100 + 1, editIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_edit_note, editPendingIntent)

    val pinIntent = Intent(context, PinWidgetActivity::class.java).apply {
        action = "com.example.stickyn.ACTION_PIN_$appWidgetId"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        data = "widget://pin/$appWidgetId".toUri()
    }
    val pinPendingIntent = PendingIntent.getActivity(
        context, appWidgetId * 100 + 2, pinIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_add_note, pinPendingIntent)

    val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
        action = "com.example.stickyn.ACTION_SETTINGS_$appWidgetId"
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        data = "widget://settings/$appWidgetId".toUri()
    }
    val settingsPendingIntent = PendingIntent.getActivity(
        context, appWidgetId * 100 + 3, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_menu, settingsPendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
