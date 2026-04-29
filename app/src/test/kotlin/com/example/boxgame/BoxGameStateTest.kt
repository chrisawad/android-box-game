package com.example.boxgame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxGameStateTest {
    @Test
    fun redPlayerAlwaysStarts() {
        val state = BoxGameState.newGame("rd", "bl")

        assertEquals(PlayerId.Player1, state.currentPlayerId)
        assertEquals("RD", state.player1.initials)
        assertEquals("BL", state.player2.initials)
        assertEquals(PlayerColor.Red, state.player1.color)
        assertEquals(PlayerColor.Blue, state.player2.color)
    }

    @Test
    fun newGameStoresSelectedPlayerColors() {
        val state = BoxGameState.newGame("R", "B", PlayerColor.Green, PlayerColor.Purple)

        assertEquals(PlayerColor.Green, state.player1.color)
        assertEquals(PlayerColor.Purple, state.player2.color)
    }

    @Test
    fun newGameStoresSelectedBoardSize() {
        val boardSize = BoardSize(columns = 6, rows = 4)

        val state = BoxGameState.newGame("R", "B", boardSize = boardSize)

        assertEquals(boardSize, state.boardSize)
        assertEquals(58, state.boardSize.totalLineCount)
        assertEquals(24, state.boardSize.boxCount)
    }

    @Test
    fun moveThatDoesNotCompleteBoxSwitchesTurns() {
        val afterMove = BoxGameState.newGame("R", "B")
            .placeLine(h(0, 0))

        assertEquals(PlayerId.Player2, afterMove.currentPlayerId)
        assertTrue(afterMove.boxes.isEmpty())
    }

    @Test
    fun completingAOneByOneBoxClaimsItAndKeepsTurn() {
        val nearlyClosed = BoxGameState.newGame("R", "B").copy(
            lines = mapOf(
                h(0, 0) to PlayerId.Player1,
                v(0, 0) to PlayerId.Player2,
                v(0, 1) to PlayerId.Player1,
            ),
        )

        val afterMove = nearlyClosed.placeLine(h(1, 0))

        assertEquals(PlayerId.Player1, afterMove.boxes[BoxCell(0, 0)])
        assertEquals(PlayerId.Player1, afterMove.currentPlayerId)
        assertEquals(1, afterMove.player1Score)
        assertEquals(0, afterMove.player2Score)
    }

    @Test
    fun sharedLineCanClaimTwoSeparateOneByOneBoxes() {
        val nearlyClosed = BoxGameState.newGame("R", "B").copy(
            lines = mapOf(
                h(0, 0) to PlayerId.Player2,
                v(0, 0) to PlayerId.Player1,
                v(0, 1) to PlayerId.Player2,
                h(2, 0) to PlayerId.Player1,
                v(1, 0) to PlayerId.Player2,
                v(1, 1) to PlayerId.Player1,
            ),
        )

        val afterMove = nearlyClosed.placeLine(h(1, 0))

        assertEquals(PlayerId.Player1, afterMove.boxes[BoxCell(0, 0)])
        assertEquals(PlayerId.Player1, afterMove.boxes[BoxCell(1, 0)])
        assertEquals(2, afterMove.player1Score)
    }

    @Test
    fun existingLineCannotBePlayedAgain() {
        val state = BoxGameState.newGame("R", "B")
            .placeLine(h(0, 0))

        val afterDuplicateMove = state.placeLine(h(0, 0))

        assertSame(state, afterDuplicateMove)
    }

    @Test
    fun winnerIsPlayerWithMostClaimedBoxesWhenBoardIsFull() {
        val boardSize = BoardSize(DefaultBoardColumns, DefaultBoardRows)
        val cells = buildList {
            repeat(boardSize.rows) { row ->
                repeat(boardSize.columns) { column ->
                    add(BoxCell(row, column))
                }
            }
        }
        val boxes = cells.mapIndexed { index, cell ->
            cell to if (index < 9) PlayerId.Player1 else PlayerId.Player2
        }.toMap()
        val fullBoard = BoxGameState.newGame("R", "B", boardSize = boardSize).copy(
            lines = allEdges(boardSize).associateWith { PlayerId.Player1 },
            boxes = boxes,
        )

        assertTrue(fullBoard.isGameOver)
        assertEquals(PlayerId.Player1, fullBoard.winner)
        assertEquals(9, fullBoard.player1Score)
        assertEquals(7, fullBoard.player2Score)
    }

    @Test
    fun tieGameHasNoWinner() {
        val boardSize = BoardSize(DefaultBoardColumns, DefaultBoardRows)
        val cells = buildList {
            repeat(boardSize.rows) { row ->
                repeat(boardSize.columns) { column ->
                    add(BoxCell(row, column))
                }
            }
        }
        val boxes = cells.mapIndexed { index, cell ->
            cell to if (index < 8) PlayerId.Player1 else PlayerId.Player2
        }.toMap()
        val fullBoard = BoxGameState.newGame("R", "B", boardSize = boardSize).copy(
            lines = allEdges(boardSize).associateWith { PlayerId.Player1 },
            boxes = boxes,
        )

        assertTrue(fullBoard.isTie)
        assertNull(fullBoard.winner)
    }

    private fun h(row: Int, column: Int) = Edge(EdgeOrientation.Horizontal, row, column)

    private fun v(row: Int, column: Int) = Edge(EdgeOrientation.Vertical, row, column)
}
