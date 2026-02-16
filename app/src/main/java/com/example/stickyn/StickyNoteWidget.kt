package com.example.stickyn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StickyNoteWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_PINNED = "com.example.stickyn.ACTION_WIDGET_PINNED"
    }

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
        val db = AppDatabase.getDatabase(context)
        val sharedPrefs = context.applicationContext.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
        
        CoroutineScope(Dispatchers.IO).launch {
            for (appWidgetId in appWidgetIds) {
                val title = sharedPrefs.getString("widget_title_$appWidgetId", "") ?: ""
                val content = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
                
                // Save to DB if the note has content
                if (title.isNotEmpty() || content.isNotEmpty()) {
                    db.noteDao().insert(Note(title = title, content = content, appWidgetId = -1))
                }

                sharedPrefs.edit(commit = true) {
                    remove("widget_title_$appWidgetId")
                    remove("saved_note_text_$appWidgetId")
                    remove("widget_transparency_$appWidgetId")
                    remove("widget_background_color_$appWidgetId")
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_WIDGET_PINNED) {
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
}

fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

    val views = RemoteViews(context.packageName, R.layout.widget_note_layout)
    val sharedPrefs = context.applicationContext.getSharedPreferences("NoteWidgetPrefs", Context.MODE_PRIVATE)
    
    val widgetTitle = sharedPrefs.getString("widget_title_$appWidgetId", "")
    val transparency = sharedPrefs.getFloat("widget_transparency_$appWidgetId", 100f)
    val backgroundColor = sharedPrefs.getString("widget_background_color_$appWidgetId", null)

    // Apply background color if set
    if (backgroundColor != null) {
        try {
            views.setInt(R.id.widget_layout, "setBackgroundColor", backgroundColor.toColorInt())
        } catch (e: Exception) {
            // Ignore invalid colors
        }
    }

    // Apply transparency
    views.setFloat(R.id.widget_layout, "setAlpha", transparency / 100f)

    val titleToDisplay = if (!widgetTitle.isNullOrEmpty()) {
        SpannableString(widgetTitle).apply {
            setSpan(UnderlineSpan(), 0, length, 0)
        }
    } else {
        ""
    }
    views.setTextViewText(R.id.widget_title_text, titleToDisplay)
    views.setViewVisibility(R.id.widget_title_text, View.VISIBLE)

    val serviceIntent = Intent(context, WidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = "widget://service/$appWidgetId".toUri()
    }
    
    views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
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
