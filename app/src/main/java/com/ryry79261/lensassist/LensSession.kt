package com.ryry79261.lensassist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Shown by the system when LensAssist is invoked as the assistant: takes the screenshot
 * the system offers, hands it to Lens, and gets out of the way.
 *
 * It paints nothing. The user should see Lens open over whatever they were looking at,
 * with no flash and no dimmed overlay in between.
 */
class LensSession(context: Context) : VoiceInteractionSession(context) {

    private val main = Handler(Looper.getMainLooper())
    private var finished = false

    /**
     * Backstop only. [onHandleScreenshot] normally arrives well inside this window; the
     * timer is here so a system that goes quiet leaves a Toast rather than a session
     * that hangs on screen.
     */
    private val timeout = Runnable { finish(null, R.string.err_timeout) }

    override fun onCreate() {
        super.onCreate()
        setUiEnabled(false)
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        // Re-asserted per show: otherwise the framework raises its own window and dims
        // what is behind it, which both flashes and lands in the next screenshot.
        setUiEnabled(false)
    }

    /** Empty and transparent, for the case where the framework inflates it anyway. */
    override fun onCreateContentView(): View =
        View(context).apply { setBackgroundColor(Color.TRANSPARENT) }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        finished = false

        if (showFlags and SHOW_WITH_SCREENSHOT == 0) {
            // The flag is the system's call, driven by the assistant's screenshot
            // toggle. Unset means no screenshot is coming at all, so say so now rather
            // than sitting out the timeout first.
            finish(null, R.string.err_screenshot_disabled)
            return
        }
        main.postDelayed(timeout, SCREENSHOT_TIMEOUT_MS)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        main.removeCallbacks(timeout)
        // A null here means the foreground app is FLAG_SECURE, or the toggle changed
        // between onShow and now.
        finish(screenshot, R.string.err_no_screenshot)
    }

    override fun onHide() {
        main.removeCallbacks(timeout)
        super.onHide()
    }

    private fun finish(screenshot: Bitmap?, @StringRes failure: Int) {
        if (finished) return
        finished = true

        if (screenshot == null) {
            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
            hide()
            return
        }

        Diagnostics.recordScreenshot(context, screenshot)

        val result = LensLauncher.send(context, screenshot, starter())
        if (result is LensLauncher.Result.Failed) {
            Toast.makeText(
                context,
                context.getString(R.string.err_launch_failed, result.detail),
                Toast.LENGTH_LONG,
            ).show()
        }

        // Hidden only after the launch: hiding first drops the foreground standing that
        // makes starting an activity legal.
        hide()
    }

    private fun starter(): (Intent) -> Unit =
        when (Diagnostics.launchStrategy(context)) {
            LaunchStrategy.NEW_TASK -> { intent -> context.startActivity(intent) }
            LaunchStrategy.ASSISTANT -> { intent -> startAssistantActivity(intent) }
        }

    private companion object {
        const val SCREENSHOT_TIMEOUT_MS = 1_500L
    }
}
