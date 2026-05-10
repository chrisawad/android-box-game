package com.chrisawad.boxgame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultiplayerRematchTest {
    @Test
    fun selectedRematchRequesterUsesStoredPlayerForTieGame() {
        val state = finishedState { index ->
            if (index < DefaultBoardColumns * DefaultBoardRows / 2) PlayerId.Player1 else PlayerId.Player2
        }
        val room = multiplayerRoom(
            gameState = state,
            rematchRequester = PlayerId.Player2,
        )

        assertEquals(PlayerId.Player2, room.selectedRematchRequester(state))
    }

    @Test
    fun selectedRematchRequesterFallsBackToLoserForFinishedGameWithoutStoredRequester() {
        val state = finishedState { index ->
            if (index < 9) PlayerId.Player1 else PlayerId.Player2
        }
        val room = multiplayerRoom(
            gameState = state,
            rematchRequester = null,
        )

        assertEquals(PlayerId.Player2, room.selectedRematchRequester(state))
    }

    @Test
    fun selectedRematchRequesterFallsBackToAPlayerForLegacyTieWithoutStoredRequester() {
        val state = finishedState { index ->
            if (index < DefaultBoardColumns * DefaultBoardRows / 2) PlayerId.Player1 else PlayerId.Player2
        }
        val room = multiplayerRoom(
            gameState = state,
            rematchRequester = null,
        )

        assertNotNull(room.selectedRematchRequester(state))
    }

    private fun multiplayerRoom(
        gameState: BoxGameState,
        rematchRequester: PlayerId?,
    ): MultiplayerRoom =
        MultiplayerRoom(
            code = "ABC123",
            status = MultiplayerStatus.Finished,
            hostUid = "host",
            guestUid = "guest",
            player1 = MultiplayerPlayer(
                uid = "host",
                playerId = PlayerId.Player1,
                initials = "P1",
                color = PlayerColor.Red,
                connected = true,
            ),
            player2 = MultiplayerPlayer(
                uid = "guest",
                playerId = PlayerId.Player2,
                initials = "P2",
                color = PlayerColor.Blue,
                connected = true,
            ),
            gameState = gameState,
            isPublic = false,
            rematchRequester = rematchRequester,
        )

    private fun finishedState(boxOwnerForIndex: (Int) -> PlayerId): BoxGameState {
        val boardSize = BoardSize(DefaultBoardColumns, DefaultBoardRows)
        val cells = buildList {
            repeat(boardSize.rows) { row ->
                repeat(boardSize.columns) { column ->
                    add(BoxCell(row, column))
                }
            }
        }

        return BoxGameState.newGame("P1", "P2", boardSize = boardSize).copy(
            lines = allEdges(boardSize).associateWith { PlayerId.Player1 },
            boxes = cells.mapIndexed { index, cell -> cell to boxOwnerForIndex(index) }.toMap(),
        )
    }
}
