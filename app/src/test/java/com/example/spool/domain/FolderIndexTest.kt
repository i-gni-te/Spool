package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderIndexTest {
    private fun song(id: String, title: String, folder: String) = MediaItemEntity(
        uri = id, title = title, artist = "A", album = "", durationMs = 1000L,
        isVideo = false, searchKey = "k $id", kind = MediaClassifier.SONG, folder = folder
    )

    @Test
    fun `songs are grouped by folder and a folder is named by its last part`() {
        val groups = FolderIndex.build(
            listOf(
                song("1", "B", "Music/Afrobeats/"),
                song("2", "A", "Music/Afrobeats/"),
                song("3", "C", "Download/")
            )
        )
        assertEquals(listOf("Afrobeats", "Download"), groups.map { it.name })
        assertEquals(listOf("2", "1"), groups[0].songs.map { it.uri }) // A to Z inside a folder
        assertEquals("Music/Afrobeats", groups[0].path)
    }

    @Test
    fun `files with no folder go to Other`() {
        val groups = FolderIndex.build(listOf(song("1", "A", "")))
        assertEquals("Other", groups[0].name)
        assertEquals(FolderIndex.NO_FOLDER, groups[0].key)
        assertEquals("", groups[0].path)
    }

    @Test
    fun `two folders with the same name stay separate`() {
        val groups = FolderIndex.build(
            listOf(song("1", "A", "Music/Favorites/"), song("2", "B", "Download/Favorites/"))
        )
        assertEquals(listOf("Download/Favorites", "Music/Favorites"), groups.map { it.path })
    }
}