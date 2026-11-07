package at.familie.haushaltplus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Lightweight last-known snapshot. No network or periodic refresh required by widgets. */
object HaushaltWidgetUpdater {
    private const val PREFS = "haushalt_widget_v4"
    fun update(context: Context, data: HouseholdData) {
        val pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        pref.edit()
            .putInt("shopping", data.shopping.count { !it.archived && !it.bought })
            .putInt("tasks", data.tasks.count { !it.done })
            .putBoolean("available", true).apply()
        renderAll(context)
    }
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        renderAll(context)
    }
    fun renderAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, HaushaltWidgetProvider::class.java))
        ids.forEach { render(context, manager, it) }
    }
    fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val summary = if (pref.getBoolean("available", false)) {
            "${pref.getInt("shopping", 0)} Einkäufe offen · ${pref.getInt("tasks", 0)} Aufgaben"
        } else "Öffne Haushalt+ für die Übersicht"
        val views = RemoteViews(context.packageName, R.layout.haushalt_widget)
        views.setTextViewText(R.id.widget_summary, summary)
        views.setOnClickPendingIntent(R.id.widget_title, intent(context, 0, false))
        views.setOnClickPendingIntent(R.id.widget_shopping, intent(context, 1, true))
        views.setOnClickPendingIntent(R.id.widget_tasks, intent(context, 2, false))
        manager.updateAppWidget(widgetId, views)
    }
    private fun intent(context: Context, tab: Int, quickAdd: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("open_tab", tab).putExtra("quick_add_shopping", quickAdd)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, tab + if (quickAdd) 10 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class HaushaltWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { HaushaltWidgetUpdater.render(context, manager, it) }
    }
}
