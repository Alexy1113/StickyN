package com.example.stickyn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AppWidgetProvider for the Sticky Note widget.
 */
class StickyNoteWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_PINNED = "com.example.stickyn.ACTION_WIDGET_PINNED"
        const val ACTION_MANUAL_UPDATE = "com.example.stickyn.ACTION_MANUAL_UPDATE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val db = AppDatabase.getDatabase(context)
        @Suppress("DEPRECATION")
        val sharedPrefs = context.applicationContext.getSharedPreferences("NoteWidgetPrefs", Context.MODE_MULTI_PROCESS)

        CoroutineScope(Dispatchers.IO).launch {
            for (appWidgetId in appWidgetIds) {
                val title = sharedPrefs.getString("widget_title_$appWidgetId", "") ?: ""
                val content = sharedPrefs.getString("saved_note_text_$appWidgetId", "") ?: ""
                val textSize = sharedPrefs.getInt("widget_base_text_size_$appWidgetId", 18).toFloat()

                if (title.isNotEmpty() || content.isNotEmpty()) {
                    db.noteDao().insert(Note(title = title, content = content, appWidgetId = -1, textSize = textSize))
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PINNED -> {
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
            ACTION_MANUAL_UPDATE -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }
}



//Core function to refresh the widget UI state.

fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

    val views = RemoteViews(context.packageName, R.layout.widget_note_layout)

    // 1. ПРИНУДИТЕЛЬНОЕ ЧТЕНИЕ
    @Suppress("DEPRECATION")
    val sharedPrefs = context.getSharedPreferences("NoteWidgetPrefs", Context.MODE_MULTI_PROCESS)

    // 2. ЧИТАЕМ ДАННЫЕ
    val widgetTitle = sharedPrefs.getString("widget_title_$appWidgetId", "") ?: ""
    val backgroundColor = sharedPrefs.getString("widget_background_color_$appWidgetId", null)

    // 3. УСТАНОВКА ЗАГОЛОВКА
    views.setTextViewText(R.id.widget_title_text, widgetTitle)
    // Убедись, что R.color.widget_text существует, иначе замени на Color.BLACK для теста
    views.setTextColor(R.id.widget_title_text, ContextCompat.getColor(context, R.color.widget_text))
    views.setViewVisibility(R.id.widget_title_text, if (widgetTitle.isNotEmpty()) View.VISIBLE else View.GONE)
    views.setTextViewText(R.id.widget_title_text, widgetTitle)

    // Устанавливаем размер текста, например 20 SP
    views.setTextViewTextSize(
        R.id.widget_title_text,
        TypedValue.COMPLEX_UNIT_SP,
        20f
    )

    // 4. ПРИМЕНЕНИЕ ФОНА
    if (backgroundColor != null) {
        try {
            views.setInt(R.id.widget_layout, "setBackgroundResource", 0) // Сброс картинки, если была
            views.setInt(R.id.widget_layout, "setBackgroundColor", backgroundColor.toColorInt())
        } catch (e: Exception) { }
    }

    // 5. НАСТРОЙКА СПИСКА (Критично, чтобы тайтл не пропадал!)
    val serviceIntent = Intent(context, WidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = "widget://service/$appWidgetId".toUri()
    }
    @Suppress("DEPRECATION")
    views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)

    // 6. НАСТРОЙКА КЛИКОВ (Кнопки)
    val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    // Редактирование (Edit)
    val editIntent = Intent(context, NoteEditActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "widget://note/$appWidgetId".toUri()
    }
    val editPendingIntent = PendingIntent.getActivity(
        context, appWidgetId * 10 + 1, editIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_edit_note, editPendingIntent)

    // Добавление (Pin / Add)
    val pinIntent = Intent(context, PinWidgetActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val pinPendingIntent = PendingIntent.getActivity(
        context, appWidgetId * 10 + 2, pinIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_add_note, pinPendingIntent)

    // Настройки (Settings / Menu)
    val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val settingsPendingIntent = PendingIntent.getActivity(
        context, appWidgetId * 10 + 3, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    )
    views.setOnClickPendingIntent(R.id.button_menu, settingsPendingIntent)

    // 7. ФИНАЛЬНЫЙ ВЫСТРЕЛ — ПЕРЕДАЕМ ВСЁ СИСТЕМЕ
    appWidgetManager.updateAppWidget(appWidgetId, views)
}