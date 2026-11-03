package com.stanley.foodjournal.recap

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens Android's share sheet with the recap image [file]. Other apps get to read only this file,
 * and only for this share, through the app's file provider.
 */
fun Context.shareRecap(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.recaps", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The clip lets the share sheet show the image as its preview.
    send.clipData = ClipData.newRawUri("", uri)
    startActivity(Intent.createChooser(send, null))
}
