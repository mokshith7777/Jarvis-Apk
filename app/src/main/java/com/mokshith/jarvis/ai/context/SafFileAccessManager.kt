package com.mokshith.jarvis.ai.context

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedReader
import java.io.InputStreamReader

data class SafDocumentEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val uri: Uri
)

class SafFileAccessManager(private val context: Context) {

    fun listDocuments(treeUriString: String, maxFiles: Int = 50): List<SafDocumentEntry> {
        val treeUri = Uri.parse(treeUriString)
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val results = mutableListOf<SafDocumentEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && results.size < maxFiles) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: id
                val mime = cursor.getString(2) ?: "application/octet-stream"
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                results.add(SafDocumentEntry(id, name, mime, docUri))
            }
        }
        return results
    }

    fun readTextFile(uri: Uri, maxChars: Int = 12000): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open selected document URI")
        return input.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(2048)
                while (sb.length < maxChars) {
                    val read = reader.read(buf)
                    if (read <= 0) break
                    val remaining = maxChars - sb.length
                    sb.append(buf, 0, minOf(read, remaining))
                }
                sb.toString()
            }
        }
    }
}
