package com.chrisawad.boxgame

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

const val DefaultBoardColumns = 4
const val DefaultBoardRows = 4
const val MinBoardBoxes = 2
const val MaxBoardBoxes = 10

@Parcelize
enum class EdgeOrientation : Parcelable {
    Horizontal,
    Vertical,
}

@Parcelize
enum class PlayerId : Parcelable {
    Player1,
    Player2,
}

@Parcelize
enum class PlayerColor(
    val label: String,
    val argb: Long,
) : Parcelable {
    Red("Red", 0xFFC62828),
    Blue("Blue", 0xFF1D4ED8),
    Green("Green", 0xFF15803D),
    Purple("Purple", 0xFF7E22CE),
    Orange("Orange", 0xFFEA580C),
    Pink("Pink", 0xFFBE185D),
    Teal("Teal", 0xFF0F766E),
    Cyan("Cyan", 0xFF0891B2),
    Indigo("Indigo", 0xFF4338CA),
    Lime("Lime", 0xFF65A30D),
    Amber("Amber", 0xFFD97706),
    Slate("Slate", 0xFF475569),
}

@Parcelize
data class BoardSize(
    val columns: Int,
    val rows: Int,
) : Parcelable {
    init {
        require(columns in MinBoardBoxes..MaxBoardBoxes) {
            "Board columns must be between $MinBoardBoxes and $MaxBoardBoxes"
        }
        require(rows in MinBoardBoxes..MaxBoardBoxes) {
            "Board rows must be between $MinBoardBoxes and $MaxBoardBoxes"
        }
    }

    val dotColumns: Int
        get() = columns + 1

    val dotRows: Int
        get() = rows + 1

    val boxCount: Int
        get() = columns * rows

    val totalLineCount: Int
        get() = dotRows * columns + rows * dotColumns
}

@Parcelize
data class Player(
    val id: PlayerId,
    val initials: String,
    val color: PlayerColor,
) : Parcelable

@Parcelize
data class Edge(
    val orientation: EdgeOrientation,
    val row: Int,
    val column: Int,
) : Parcelable {
    fun isValidFor(boardSize: BoardSize): Boolean =
        when (orientation) {
            EdgeOrientation.Horizontal -> row in 0 until boardSize.dotRows && column in 0 until boardSize.columns
            EdgeOrientation.Vertical -> row in 0 until boardSize.rows && column in 0 until boardSize.dotColumns
        }
}

@Parcelize
data class BoxCell(
    val row: Int,
    val column: Int,
) : Parcelable

@Parcelize
data class BoxGameState(
    val player1: Player,
    val player2: Player,
    val boardSize: BoardSize = BoardSize(DefaultBoardColumns, DefaultBoardRows),
    val currentPlayerId: PlayerId = PlayerId.Player1,
    val lines: Map<Edge, PlayerId> = emptyMap(),
    val boxes: Map<BoxCell, PlayerId> = emptyMap(),
) : Parcelable {
    val currentPlayer: Player
        get() = player(currentPlayerId)

    val isGameOver: Boolean
        get() = lines.size == boardSize.totalLineCount

    val player1Score: Int
        get() = boxes.count { it.value == PlayerId.Player1 }

    val player2Score: Int
        get() = boxes.count { it.value == PlayerId.Player2 }

    val winner: PlayerId?
        get() = when {
            !isGameOver -> null
            player1Score > player2Score -> PlayerId.Player1
            player2Score > player1Score -> PlayerId.Player2
            else -> null
        }

    val isTie: Boolean
        get() = isGameOver && player1Score == player2Score

    fun player(playerId: PlayerId): Player = when (playerId) {
        PlayerId.Player1 -> player1
        PlayerId.Player2 -> player2
    }

    fun placeLine(edge: Edge): BoxGameState {
        if (!edge.isValidFor(boardSize) || edge in lines || isGameOver) return this

        val updatedLines = lines + (edge to currentPlayerId)
        val newlyCompletedBoxes = adjacentBoxes(edge)
            .filterNot { it in boxes }
            .filter { it.isComplete(updatedLines) }

        val updatedBoxes = boxes + newlyCompletedBoxes.associateWith { currentPlayerId }
        val nextPlayer = if (newlyCompletedBoxes.isEmpty()) currentPlayerId.next() else currentPlayerId

        return copy(
            currentPlayerId = nextPlayer,
            lines = updatedLines,
            boxes = updatedBoxes,
        )
    }

    private fun BoxCell.isComplete(placedLines: Map<Edge, PlayerId>): Boolean {
        val top = Edge(EdgeOrientation.Horizontal, row, column)
        val bottom = Edge(EdgeOrientation.Horizontal, row + 1, column)
        val left = Edge(EdgeOrientation.Vertical, row, column)
        val right = Edge(EdgeOrientation.Vertical, row, column + 1)
        return top in placedLines && bottom in placedLines && left in placedLines && right in placedLines
    }

    private fun adjacentBoxes(edge: Edge): List<BoxCell> = when (edge.orientation) {
        EdgeOrientation.Horizontal -> listOfNotNull(
            boxOrNull(edge.row - 1, edge.column),
            boxOrNull(edge.row, edge.column),
        )

        EdgeOrientation.Vertical -> listOfNotNull(
            boxOrNull(edge.row, edge.column - 1),
            boxOrNull(edge.row, edge.column),
        )
    }

    private fun boxOrNull(row: Int, column: Int): BoxCell? =
        if (row in 0 until boardSize.rows && column in 0 until boardSize.columns) BoxCell(row, column) else null

    companion object {
        fun newGame(
            player1Initials: String,
            player2Initials: String,
            player1Color: PlayerColor = PlayerColor.Red,
            player2Color: PlayerColor = PlayerColor.Blue,
            boardSize: BoardSize = BoardSize(DefaultBoardColumns, DefaultBoardRows),
        ): BoxGameState =
            BoxGameState(
                player1 = Player(PlayerId.Player1, normalizeInitials(player1Initials), player1Color),
                player2 = Player(PlayerId.Player2, normalizeInitials(player2Initials), player2Color),
                boardSize = boardSize,
            )
    }
}

fun PlayerId.next(): PlayerId = when (this) {
    PlayerId.Player1 -> PlayerId.Player2
    PlayerId.Player2 -> PlayerId.Player1
}

fun allEdges(boardSize: BoardSize = BoardSize(DefaultBoardColumns, DefaultBoardRows)): List<Edge> =
    buildList {
        repeat(boardSize.dotRows) { row ->
            repeat(boardSize.columns) { column ->
                add(Edge(EdgeOrientation.Horizontal, row, column))
            }
        }
        repeat(boardSize.rows) { row ->
            repeat(boardSize.dotColumns) { column ->
                add(Edge(EdgeOrientation.Vertical, row, column))
            }
        }
    }

fun normalizeInitials(input: String): String =
    input
        .filter { it.isLetterOrDigit() }
        .take(3)
        .uppercase()
