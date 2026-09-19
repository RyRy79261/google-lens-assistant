package com.ryry79261.lensassist

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Staging area for the PNGs handed to Lens, under cacheDir/screens and shared through
 * [FileProvider].
 *
 * Lens may still be reading the previous file when we write the next one, so old files
 * are pruned down to [KEEP] rather than cleared outright.
 */
object ScreenshotCache {

    private const val DIR = "screens"
    private const val KEEP = 2

    fun write(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, DIR)
        require(dir.isDirectory || dir.mkdirs()) { "could not create $dir" }
        prune(dir)

        val file = File(dir, "screen-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            require(bitmap.readable().compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "PNG encoding failed"
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun prune(dir: File) {
        dir.listFiles()
            ?.sortedByDescending(File::lastModified)
            ?.drop(KEEP - 1)
            ?.forEach { it.delete() }
    }
}

/**
 * A bitmap [Bitmap.compress] can actually read.
 *
 * The system usually delivers the assist screenshot in HARDWARE config, whose pixels
 * live in graphics memory; compressing one throws. Copying is the only way out.
 */
fun Bitmap.readable(): Bitmap =
    if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) ?: this else this
