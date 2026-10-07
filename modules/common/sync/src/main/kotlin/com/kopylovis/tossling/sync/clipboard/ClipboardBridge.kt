package com.kopylovis.tossling.sync.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.kopylovis.tossling.protocol.ClipKind
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class Outgoing(
    val kind: ClipKind,
    val mime: String,
    val file: File,
    val name: String? = null,
)

class TooLargeException : Exception()

class ClipboardBridge(private val context: Context) {

    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    internal val clipsDir: File get() = File(context.filesDir, CLIPS_DIR).apply { mkdirs() }

    private val outgoingDir: File get() = File(context.cacheDir, OUTGOING_DIR).apply { mkdirs() }

    internal fun writeText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(LABEL, text))
    }

    internal fun writeImage(file: File, mime: String) {
        val uri = shareUri(file = file)
        clipboard.setPrimaryClip(ClipData(LABEL, arrayOf(mime), ClipData.Item(uri)))
    }

    fun readClipboard(): Outgoing? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        val uri = item.uri
        val mime = uri?.let { context.contentResolver.getType(it) } ?: clip.description.getMimeType(0)
        if (uri != null && mime != null && !mime.startsWith("text/")) return fromUri(uri = uri, mime = mime)
        val text = item.coerceToText(context)?.toString().orEmpty()
        if (text.isEmpty()) return null
        return fromText(text = text)
    }

    fun fromText(text: String): Outgoing {
        val bytes = text.toByteArray()
        if (bytes.size > MAX_TEXT) throw TooLargeException()
        val file = File(outgoingDir, UUID.randomUUID().toString()).apply { writeBytes(bytes) }
        return Outgoing(kind = ClipKind.TEXT, mime = ClipDescription.MIMETYPE_TEXT_PLAIN, file = file)
    }

    fun fromUri(uri: Uri, mime: String, asFile: Boolean = false): Outgoing? {
        val isImage = mime.startsWith("image/") && !asFile
        val limit = if (isImage) MAX_IMAGE else MAX_FILE
        if ((declaredSize(uri = uri) ?: 0L) > limit) throw TooLargeException()
        val file = File(outgoingDir, UUID.randomUUID().toString())
        val size = context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> copyLimited(input = input, output = output, limit = limit) }
        } ?: return null
        if (size > limit) {
            file.delete()
            throw TooLargeException()
        }
        val kind = if (isImage) ClipKind.IMAGE else ClipKind.FILE
        return Outgoing(kind = kind, mime = mime, file = file, name = if (isImage) null else displayName(uri = uri))
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) return total
            output.write(buffer, 0, read)
        }
    }

    private fun declaredSize(uri: Uri): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    internal fun saveDownload(name: String, mime: String, bytes: ByteArray): Uri? = saveDownload(name = name, mime = mime) { it.write(bytes) }

    internal fun saveDownload(name: String, mime: String, write: (OutputStream) -> Unit): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_DIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.buffered(COPY_BUFFER)?.use(write) ?: error("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    internal fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)

    internal fun extensionFor(mime: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "png"

    companion object {
        const val MAX_TEXT = 1_000_000
        const val MAX_IMAGE = 15_000_000L
        const val MAX_FILE = 500_000_000L
        private const val DOWNLOADS_DIR = "Tossling"
        private const val LABEL = "Tossling"
        private const val CLIPS_DIR = "clips"
        private const val OUTGOING_DIR = "outgoing"
        private const val AUTHORITY_SUFFIX = ".clips"
        private const val COPY_BUFFER = 1 shl 16
    }
}
