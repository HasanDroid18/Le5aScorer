package com.hasanDroid.le5ascorer.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object ImageUtil {

    private const val IMAGES_DIR = "le5a_loser_images"

    private fun getImageDirectory(context: Context): File {
        val dir = File(context.filesDir, IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, matchId: Long): String? {
        return try {
            val imageFile = File(getImageDirectory(context), "match_${matchId}.jpg")
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            imageFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}

