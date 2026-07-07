package com.rajasudhan.taskmind.data.source.understanding

import android.net.Uri

/**
 * A single media input for the multimodal extraction seam (#211, Gemma 3n migration Phase 0): the
 * content [uri] of an image or audio clip plus its [mimeType] (an "image" or "audio" MIME type). A
 * vision-capable engine reads the bytes lazily from the uri via the content resolver, so nothing is
 * decoded until an engine actually consumes it — which, until a vision engine lands, is never.
 */
data class MediaInput(
    val uri: Uri,
    val mimeType: String,
)
