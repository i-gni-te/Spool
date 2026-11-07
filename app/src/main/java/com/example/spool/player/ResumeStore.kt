package com.example.spool.player

import com.example.spool.domain.RandomQueue
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Saves the queue to a small file. Position is kept separately in Prefs. */
object ResumeStore {

    fun writeQueue(file: File, state: RandomQueue.State) {
        runCatching {
            val json = JSONObject()
                .put("all", JSONArray(state.all))
                .put("unplayed", JSONArray(state.unplayed))
                .put("history", JSONArray(state.history))
                .put("index", state.index)
                .put("upNext", JSONArray(state.upNext))
                .put("shuffle", state.shuffle)
            // Write to a temp file first, so a crash can't leave a half-written queue
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.toString())
            temp.renameTo(file)
        }
    }

    fun readQueue(file: File): RandomQueue.State? = runCatching {
        if (!file.exists()) return@runCatching null
        val json = JSONObject(file.readText())
        RandomQueue.State(
            all = json.getJSONArray("all").toStrings(),
            unplayed = json.getJSONArray("unplayed").toStrings(),
            history = json.getJSONArray("history").toStrings(),
            index = json.getInt("index"),
            // Older save files don't have these two
            upNext = json.optJSONArray("upNext")?.toStrings() ?: emptyList(),
            shuffle = json.optBoolean("shuffle", true)
        )
    }.getOrNull()

    private fun JSONArray.toStrings(): List<String> = List(length()) { getString(it) }
}