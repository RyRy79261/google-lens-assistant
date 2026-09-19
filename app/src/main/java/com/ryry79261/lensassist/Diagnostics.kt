package com.ryry79261.lensassist

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap

/** Which call starts Lens. The two differ in where Back goes, so it is switchable. */
enum class LaunchStrategy {
    /** `context.startActivity` — Lens gets its own task. */
    NEW_TASK,

    /** `VoiceInteractionSession.startAssistantActivity` — launch into the assistant stack. */
    ASSISTANT,
}

/**
 * What the last invocation actually did, so the launcher screen can report it.
 *
 * The assist session and the launcher activity share a process, so plain
 * SharedPreferences is enough to get values from one to the other.
 */
object Diagnostics {

    private const val PREFS = "diagnostics"
    private const val KEY_STRATEGY = "launch_strategy"
    private const val KEY_WIDTH = "shot_width"
    private const val KEY_HEIGHT = "shot_height"
    private const val KEY_CONFIG = "shot_config"
    private const val KEY_ROUTE = "route"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun launchStrategy(context: Context): LaunchStrategy =
        runCatching { LaunchStrategy.valueOf(prefs(context).getString(KEY_STRATEGY, null)!!) }
            .getOrDefault(LaunchStrategy.NEW_TASK)

    fun setLaunchStrategy(context: Context, strategy: LaunchStrategy) {
        prefs(context).edit().putString(KEY_STRATEGY, strategy.name).apply()
    }

    /** Records the dimensions the system handed us — the number that decides OCR quality. */
    fun recordScreenshot(context: Context, bitmap: Bitmap) {
        prefs(context).edit()
            .putInt(KEY_WIDTH, bitmap.width)
            .putInt(KEY_HEIGHT, bitmap.height)
            .putString(KEY_CONFIG, bitmap.config?.name ?: "unknown")
            .apply()
    }

    fun lastScreenshot(context: Context): Triple<Int, Int, String>? {
        val p = prefs(context)
        val width = p.getInt(KEY_WIDTH, 0)
        val height = p.getInt(KEY_HEIGHT, 0)
        if (width == 0 || height == 0) return null
        return Triple(width, height, p.getString(KEY_CONFIG, "unknown") ?: "unknown")
    }

    fun recordRoute(context: Context, detail: String) {
        prefs(context).edit().putString(KEY_ROUTE, detail).apply()
    }

    fun lastRoute(context: Context): String? = prefs(context).getString(KEY_ROUTE, null)
}
