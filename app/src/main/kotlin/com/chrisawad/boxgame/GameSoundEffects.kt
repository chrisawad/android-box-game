package com.chrisawad.boxgame

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Collections

private enum class GameSound(
    val resId: Int,
) {
    Player1Move(R.raw.move_player_1),
    Player2Move(R.raw.move_player_2),
    BoxCompleted(R.raw.box_completed),
    GameWon(R.raw.game_won),
    GameLost(R.raw.game_lost),
    PlayerJoined(R.raw.player_joined),
    PlayerLeft(R.raw.player_left),
}

internal class GameSoundEffects(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val loadedSoundIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds: Map<GameSound, Int>
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds += sampleId
            }
        }
        soundIds = GameSound.entries.associateWith { sound ->
            soundPool.load(appContext, sound.resId, 1)
        }
    }

    fun playMove(playerId: PlayerId) {
        when (playerId) {
            PlayerId.Player1 -> play(GameSound.Player1Move)
            PlayerId.Player2 -> play(GameSound.Player2Move)
        }
    }

    fun playBoxCompleted() {
        play(GameSound.BoxCompleted, volume = 0.95f)
    }

    fun playWin() {
        play(GameSound.GameWon, delayMillis = 150L, volume = 0.9f)
    }

    fun playLoss() {
        play(GameSound.GameLost, delayMillis = 150L, volume = 0.85f)
    }

    fun playPlayerJoined() {
        play(GameSound.PlayerJoined, volume = 0.8f)
    }

    fun playPlayerLeft() {
        play(GameSound.PlayerLeft, volume = 0.8f)
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }

    private fun play(
        sound: GameSound,
        delayMillis: Long = 0L,
        volume: Float = 0.88f,
    ) {
        if (released) return

        if (delayMillis > 0L) {
            handler.postDelayed({ playNow(sound, volume) }, delayMillis)
        } else {
            playNow(sound, volume)
        }
    }

    private fun playNow(
        sound: GameSound,
        volume: Float,
    ) {
        if (released) return
        val soundId = soundIds[sound] ?: return
        if (soundId !in loadedSoundIds) return

        soundPool.play(
            soundId,
            volume,
            volume,
            1,
            0,
            1f,
        )
    }
}

@Composable
internal fun rememberGameSoundEffects(): GameSoundEffects {
    val context = LocalContext.current
    val soundEffects = remember(context.applicationContext) {
        GameSoundEffects(context)
    }

    DisposableEffect(soundEffects) {
        onDispose {
            soundEffects.release()
        }
    }

    return soundEffects
}

internal fun GameSoundEffects.playGameStateSounds(
    previousState: BoxGameState,
    currentState: BoxGameState,
    localPlayerId: PlayerId? = null,
) {
    if (currentState.lines.size <= previousState.lines.size) return

    val moveOwner = currentState.lines.entries
        .firstOrNull { (edge, _) -> edge !in previousState.lines }
        ?.value
        ?: previousState.currentPlayerId
    playMove(moveOwner)

    if (currentState.boxes.size > previousState.boxes.size) {
        playBoxCompleted()
    }

    if (!previousState.isGameOver && currentState.isGameOver) {
        if (localPlayerId == null || currentState.winner == null || currentState.winner == localPlayerId) {
            playWin()
        } else {
            playLoss()
        }
    }
}

internal fun GameSoundEffects.playMultiplayerRoomSounds(
    previousRoom: MultiplayerRoom?,
    currentRoom: MultiplayerRoom,
    localPlayerId: PlayerId,
) {
    if (previousRoom == null || previousRoom.code != currentRoom.code) return

    val previousOpponent = previousRoom.opponentFor(localPlayerId)
    val currentOpponent = currentRoom.opponentFor(localPlayerId)

    if ((previousOpponent == null || !previousOpponent.connected) && currentOpponent?.connected == true) {
        playPlayerJoined()
    }

    if (previousOpponent?.connected == true &&
        (currentOpponent?.connected != true || currentRoom.status == MultiplayerStatus.Abandoned)
    ) {
        playPlayerLeft()
    }

    val previousState = previousRoom.gameState
    val currentState = currentRoom.gameState
    if (previousState != null && currentState != null) {
        playGameStateSounds(
            previousState = previousState,
            currentState = currentState,
            localPlayerId = localPlayerId,
        )
    }
}
