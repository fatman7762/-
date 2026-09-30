package jp.co.lightpath.reception.ui.layout

import android.content.Context

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ReceptionLayout {
        return receptionLayoutFromJson(prefs.getString(KEY, null)) ?: defaultReceptionLayout()
    }

    fun save(layout: ReceptionLayout) {
        prefs.edit().putString(KEY, layout.toJson()).apply()
    }

    companion object {
        private const val PREFS = "reception_layout"
        private const val KEY = "layout_json"
    }
}
