package com.example.spool.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class QueuePlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*NAV_COMMANDS).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in NAV_COMMANDS || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = onNext()
    override fun seekToNextMediaItem() = onNext()

    // Like most players: restart the song if it's past 3 seconds, otherwise go back
    override fun seekToPrevious() {
        if (currentPosition > RESTART_THRESHOLD_MS) seekTo(0L) else onPrevious()
    }
    override fun seekToPreviousMediaItem() = onPrevious()

    companion object {
        private const val RESTART_THRESHOLD_MS = 3000L
        private val NAV_COMMANDS = intArrayOf(
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
    }
}