package com.example.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object ReportShareHelper {

    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun shareReportFile(context: Context, file: File, mimeType: String, subject: String) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(
                Intent.EXTRA_TEXT,
                "$subject — Παράχθηκε από την εφαρμογή Hotel Pulse."
            )
            clipData = ClipData.newRawUri(subject, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.share_chooser_title)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun copyFileToSafUri(context: Context, sourceFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: error("Αδυναμία εγγραφής στο επιλεγμένο αρχείο.")
                Unit
            }
        }
}
