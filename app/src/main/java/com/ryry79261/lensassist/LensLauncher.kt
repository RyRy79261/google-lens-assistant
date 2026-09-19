package com.ryry79261.lensassist

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log

/** Lens ships inside the Google app; there is no separate Lens package to target. */
const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

private const val TAG = "LensAssist"
private const val MIME = "image/png"

/**
 * The one path that puts an image in front of Lens.
 *
 * Both the assist session and the launcher screen's Test button come through here, so
 * what the Test button proves is what the long-press does.
 */
object LensLauncher {

    sealed interface Result {
        /** [detail] is already human-readable; the launcher screen shows it verbatim. */
        data class Sent(val detail: String) : Result

        data class Failed(val detail: String) : Result
    }

    /**
     * Stages [bitmap] and hands it to the Google app, falling back to the share sheet
     * when the Google app exposes no image receiver.
     *
     * [start] performs the launch, so the caller picks between `context.startActivity`
     * and `VoiceInteractionSession.startAssistantActivity`.
     */
    fun send(context: Context, bitmap: Bitmap, start: (Intent) -> Unit): Result {
        val uri = try {
            ScreenshotCache.write(context, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "could not stage the screenshot", e)
            return Result.Failed("could not write the screenshot (${e.message})")
        }

        // Pick an explicit component rather than letting the system disambiguate. The
        // Google app exposes several image receivers, and an ambiguous intent lands on
        // ResolverActivity, which has to forward the read grant to whatever the user
        // picks — a step that routinely loses it.
        val targets = candidates(context)
        val lens = targets.firstOrNull { it.name.contains("lens", ignoreCase = true) }
            ?: targets.singleOrNull()

        val (intent, detail) = if (lens != null) {
            val component = ComponentName(lens.packageName, lens.name)
            googleAppIntent(uri).setComponent(component) to
                context.getString(R.string.route_direct, component.flattenToShortString())
        } else {
            chooserIntent(context, uri) to
                context.getString(R.string.route_chooser, targets.size)
        }

        return try {
            start(intent)
            Diagnostics.recordRoute(context, detail)
            Result.Sent(detail)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "nothing handled the send", e)
            Result.Failed("no activity accepted the image")
        } catch (e: SecurityException) {
            // Typically a background-activity-launch refusal.
            Log.e(TAG, "launch refused", e)
            Result.Failed("the launch was refused (${e.message})")
        }
    }

    /**
     * Every activity in the Google app that accepts a shared PNG.
     *
     * Resolved at runtime rather than pinned to a class name: Google renames these
     * between releases. There is usually more than one, which is why a plain
     * resolveActivity() on this intent answers with the system ResolverActivity —
     * the "ambiguous, ask the user" sentinel — rather than a real target.
     */
    fun candidates(context: Context): List<ActivityInfo> =
        context.packageManager
            .queryIntentActivitiesCompat(googleAppIntent(Uri.EMPTY))
            .mapNotNull { it.activityInfo }

    fun googleAppInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(GOOGLE_APP_PACKAGE, 0)
        }.isSuccess

    private fun googleAppIntent(uri: Uri): Intent =
        shareIntent(uri).apply { `package` = GOOGLE_APP_PACKAGE }

    /** ACTION_SEND carrying the image as an extra *and* on the clip. */
    private fun shareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        // filterEquals() ignores extras and ClipData, so two sends look identical to
        // the system and the second one just re-surfaces the already-running task with
        // its original image. The identifier is what makes them distinct.
        identifier = uri.toString()
        // Receivers disagree about where to look for the image, and the clip is also
        // what carries the read grant through the share sheet.
        clipData = ClipData(ClipDescription("screenshot", arrayOf(MIME)), ClipData.Item(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun chooserIntent(context: Context, uri: Uri): Intent {
        val base = shareIntent(uri)
        return Intent.createChooser(base, context.getString(R.string.chooser_title)).apply {
            // The chooser has to carry the grant too, or the app the user picks gets
            // a URI it cannot open.
            clipData = base.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> =
    queryIntentActivities(intent, 0)
