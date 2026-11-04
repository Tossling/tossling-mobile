package com.kopylovis.tossling.protocol

/**
 * The largest attachments a device downloads into memory, by kind. Senders stay well below them (the Mac sends at most
 * 1 MB of text and scales images down to 15 MB), so anything bigger is broken or hostile and would only run the phone
 * out of memory. Streamed files go to disk in chunks and have no limit here.
 */
object AttachmentLimits {

    const val TEXT: Long = 2L * 1024 * 1024
    const val IMAGE: Long = 32L * 1024 * 1024

    /** An old-style file sealed as one piece (no `x`). */
    const val WHOLE_FILE: Long = 64L * 1024 * 1024

    /** What a notification preview downloads: a few lines of text, an image the notification extension can hold. */
    const val PREVIEW_TEXT: Long = 64L * 1024
    const val PREVIEW_IMAGE: Long = 4L * 1024 * 1024

    /** The limit for [kind], sealing overhead included. */
    fun of(kind: String): Long = when (kind) {
        ClipMeta.TEXT -> TEXT
        ClipMeta.IMAGE -> IMAGE
        else -> WHOLE_FILE
    } + OVERHEAD

    /** True when the size the server gave for the attachment is over [limit]; an unknown size is checked while reading. */
    fun exceeds(size: Long?, limit: Long): Boolean = size != null && size > limit

    private const val OVERHEAD = 28L
}

/** The attachment is bigger than the device downloads into memory. Not a network error: it is not tried again. */
class AttachmentTooLarge(val limit: Long) : Exception("the attachment is larger than $limit bytes")
