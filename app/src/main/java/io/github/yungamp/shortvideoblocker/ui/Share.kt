package io.github.yungamp.shortvideoblocker.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Share {
    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun files(context: Context, files: List<File>, title: String) {
        if (files.isEmpty()) return
        val uris = files.map { uri(context, it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = if (files.size == 1 && files.first().extension == "zip") "application/zip" else "*/*"
            clipData = ClipData.newRawUri(title, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
