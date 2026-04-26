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

        assertEquals(PlayerId.Red, state.currentPlayerId)
        assertEquals("RD", state.redPlayer.initials)
        assertEquals("BL", state.bluePlayer.initials)
        assertEquals(PlayerColor.Red, state.redPlayer.color)
        assertEquals(PlayerColor.Blue, state.bluePlayer.color)
    }

    @Test
    fun newGameStoresSelectedPlayerColors() {
        val state = BoxGameState.newGame("R", "B", PlayerColor.Green, PlayerColor.Purple)

        assertEquals(PlayerColor.Green, state.redPlayer.color)
        assertEquals(PlayerColor.Purple, state.bluePlayer.color)
    }

    @Test
    fun moveThatDoesNotCompleteBoxSwitchesTurns() {
        val afterMove = BoxGameState.newGame("R", "B")
            .placeLine(h(0, 0))

        assertEquals(PlayerId.Blue, afterMove.currentPlayerId)
        assertTrue(afterMove.boxes.isEmpty())
    }

    @Test
    fun completingAOneByOneBoxClaimsItAndKeepsTurn() {
        val nearlyClosed = BoxGameState.newGame("R", "B").copy(
            lines = mapOf(
                h(0, 0) to PlayerId.Red,
                v(0, 0) to PlayerId.Blue,
                v(0, 1) to PlayerId.Red,
            ),
        )

        val afterMove = nearlyClosed.placeLine(h(1, 0))

        assertEquals(PlayerId.Red, afterMove.boxes[BoxCell(0, 0)])
        assertEquals(PlayerId.Red, afterMove.currentPlayerId)
        assertEquals(1, afterMove.redScore)
        assertEquals(0, afterMove.blueScore)
    }

    @Test
    fun sharedLineCanClaimTwoSeparateOneByOneBoxes() {
        val nearlyClosed = BoxGameState.newGame("R", "B").copy(
            lines = mapOf(
                h(0, 0) to PlayerId.Blue,
                v(0, 0) to PlayerId.Red,
                v(0, 1) to PlayerId.Blue,
                h(2, 0) to PlayerId.Red,
                v(1, 0) to PlayerId.Blue,
                v(1, 1) to PlayerId.Red,
            ),
        )

        val afterMove = nearlyClosed.placeLine(h(1, 0))

        assertEquals(PlayerId.Red, afterMove.boxes[BoxCell(0, 0)])
        assertEquals(PlayerId.Red, afterMove.boxes[BoxCell(1, 0)])
        assertEquals(2, afterMove.redScore)
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
        val cells = buildList {
            repeat(BoxCount) { row ->
                repeat(BoxCount) { column ->
                    add(BoxCell(row, column))
                }
            }
        }
        val boxes = cells.mapIndexed { index, cell ->
            cell to if (index < 9) PlayerId.Red else PlayerId.Blue
        }.toMap()
        val fullBoard = BoxGameState.newGame("R", "B").copy(
            lines = allEdges().associateWith { PlayerId.Red },
            boxes = boxes,
        )

        assertTrue(fullBoard.isGameOver)
        assertEquals(PlayerId.Red, fullBoard.winner)
        assertEquals(9, fullBoard.redScore)
        assertEquals(7, fullBoard.blueScore)
    }

    @Test
    fun tieGameHasNoWinner() {
        val cells = buildList {
            repeat(BoxCount) { row ->
                repeat(BoxCount) { column ->
                    add(BoxCell(row, column))
                }
            }
        }
        val boxes = cells.mapIndexed { index, cell ->
            cell to if (index < 8) PlayerId.Red else PlayerId.Blue
        }.toMap()
        val fullBoard = BoxGameState.newGame("R", "B").copy(
            lines = allEdges().associateWith { PlayerId.Red },
            boxes = boxes,
        )

        assertTrue(fullBoard.isTie)
        assertNull(fullBoard.winner)
    }

    private fun h(row: Int, column: Int) = Edge(EdgeOrientation.Horizontal, row, column)

    private fun v(row: Int, column: Int) = Edge(EdgeOrientation.Vertical, row, column)
}
