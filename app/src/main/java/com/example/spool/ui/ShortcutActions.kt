package com.example.spool.ui

object ShortcutActions {
    const val SHUFFLE_SONGS = "com.example.spool.action.SHUFFLE_SONGS"
    const val SHUFFLE_VIDEOS = "com.example.spool.action.SHUFFLE_VIDEOS"
    const val RESUME = "com.example.spool.action.RESUME"
    val ALL = setOf(SHUFFLE_SONGS, SHUFFLE_VIDEOS, RESUME)
}