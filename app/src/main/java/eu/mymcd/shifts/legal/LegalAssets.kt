package eu.mymcd.shifts.legal

import android.content.Context
import android.content.ContextWrapper

object LegalAssets {

    @Volatile
    private var appContext: Context? = null

    /** Call once from Application.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun read(fileName: String): String {
        val ctx = appContext ?: return ""
        return ctx.assets.open("legal/$fileName").bufferedReader().use { it.readText() }
    }
}
