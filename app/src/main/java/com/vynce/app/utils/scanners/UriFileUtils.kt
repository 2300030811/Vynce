package com.vynce.app.utils.scanners

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.TreeDocumentFileOt
import java.io.File

fun documentFileFromUri(context: Context, uris: List<Uri>): List<DocumentFile> {
    return uris.map { customDocFileFromTreeTreeUri(context, it) }.filter { it.isDirectory }
}

fun documentFileFromUri(context: Context, uri: Uri): DocumentFile? {
    return customDocFileFromTreeTreeUri(context, uri)
}

fun stringFromUriList(uris: List<Uri>): String {
    if (uris.isEmpty()) return ""
    return uris.distinctBy { it.toString() }.joinToString("\n")
}

fun uriListFromString(str: String): List<Uri> {
    return str.split("\n").map { it.toUri() }.filter { it.toString().isNotBlank() }.distinctBy { it.toString() }
}

fun fileFromUri(context: Context, uri: Uri): File? {
    if (uri.scheme == "file") {
        return uri.path?.let { File(it) }
    }

    if (uri.authority == "com.android.externalstorage.documents") {
        val docId = try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
        } catch (_: Exception) {
            uri.lastPathSegment ?: ""
        }

        val parts = docId.split(":", limit = 2)
        if (parts.size >= 2) {
            val rootId = parts[0]
            val relativePath = parts[1]

            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

            val rootDir = if (rootId.equals("primary", ignoreCase = true)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageManager.primaryStorageVolume.directory
                } else {
                    Environment.getExternalStorageDirectory()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageManager.storageVolumes.firstOrNull {
                        it.uuid != null && it.uuid.equals(rootId, ignoreCase = true)
                    }?.directory
                } else {
                    val secondaryStorage = "/storage/$rootId"
                    if (File(secondaryStorage).exists()) {
                        File(secondaryStorage)
                    } else {
                        null
                    }
                }
            }

            return rootDir?.let { if (relativePath.isEmpty()) it else File(it, relativePath) }
        }
    }

    // Direct filesystem path fallback
    val rawPath = uri.path
    if (rawPath != null) {
        val directFile = File(rawPath)
        if (directFile.exists()) return directFile
    }

    return null
}

fun absoluteFilePathFromUri(context: Context, uri: Uri): String? {
    val dfUri = documentFileFromUri(context, uri)?.uri
    if (dfUri == null) return null
    return fileFromUri(context, dfUri)?.absolutePath
}

private fun customDocFileFromTreeTreeUri(context: Context, uri: Uri) = TreeDocumentFileOt(
    null, context, DocumentsContract.buildDocumentUriUsingTree(
        uri, DocumentsContract.getTreeDocumentId(uri)
    )
)

