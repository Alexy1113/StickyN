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
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            for (appWidgetId in appWidgetIds) {
                remove("widget_title_$appWidgetId")
                remove("saved_note_text_$appWidgetId")
            }
        }
    }
}

fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_note_layout)
    val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
    
    val widgetTitle = sharedPrefs.getString("widget_title_$appWidgetId", "")
    val themeMode = sharedPrefs.getString("widget_theme_mode", "light")

    // Determine colors
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
    
    // Explicitly set Visibility and Color for Title
    views.setViewVisibility(R.id.widget_title_text, View.VISIBLE)
    views.setTextColor(R.id.widget_title_text, textColor)
    
    // If widgetTitle is blank, we can show nothing or a subtle placeholder. 
    // To avoid it constantly saying "Title", let's use the actual value if present.
    val displayTitle = if (widgetTitle.isNullOrBlank()) "" else widgetTitle
    views.setTextViewText(R.id.widget_title_text, displayTitle)
    
    views.setTextColor(R.id.appwidget_text, textColor)
    views.setInt(R.id.button_menu, "setColorFilter", textColor)
    views.setInt(R.id.button_add_note, "setColorFilter", textColor)
    views.setInt(R.id.button_edit_note, "setColorFilter", textColor)

    // Setup ListView Service
    val serviceIntent = Intent(context, WidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = toUri(Intent.URI_INTENT_SCHEME).toUri()
    }
    
    @Suppress("DEPRECATION")
    views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
    views.setEmptyView(R.id.widget_list_view, R.id.appwidget_text)

    val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    // Edit Note Intent - Using a Data URI to make the PendingIntent unique per widget
    val editIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        data = "widget://note/$appWidgetId".toUri()
    }

    // Template for ListView items
    val templatePendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        editIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setPendingIntentTemplate(R.id.widget_list_view, templatePendingIntent)

    // Set click listener for the Edit Button
    val directEditPendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId + 200,
        editIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_edit_note, directEditPendingIntent)

    // Ensure Title area doesn't trigger edit anymore
    views.setOnClickPendingIntent(R.id.widget_title_text, null)
    views.setOnClickPendingIntent(R.id.appwidget_text, null)

    // New Note Button
    val pinIntent = Intent(context, PinWidgetActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        data = "widget://pin/$appWidgetId".toUri()
    }
    val pinPendingIntent = PendingIntent.getActivity(
        context, appWidgetId + 300, pinIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_add_note, pinPendingIntent)

    // Settings Button
    val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        data = "widget://settings/$appWidgetId".toUri()
    }
    val settingsPendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId + 100,
        settingsIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_menu, settingsPendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
    
    @Suppress("DEPRECATION")
    appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.widget_list_view)
}
