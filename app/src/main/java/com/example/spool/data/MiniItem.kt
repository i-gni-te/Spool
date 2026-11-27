package com.example.spool.data

/** The last song or video that played, so the mini-player can show it on its own page. */
data class MiniItem(
    val uri: String,
    val title: String,
    val artist: String,
    val isShort: Boolean = false
)