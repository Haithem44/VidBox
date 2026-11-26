package com.vidbox.app

import android.net.Uri

data class VideoItem(
    val title: String,
    val uri: Uri,
    val duration: Long = 0L
)
