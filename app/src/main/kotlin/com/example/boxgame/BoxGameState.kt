package com.example.boxgame

const val DotCount = 5
const val BoxCount = DotCount - 1
const val TotalLineCount = DotCount * BoxCount * 2

enum class EdgeOrientation {
    Horizontal,
    Vertical,
}

enum class PlayerId {
    Player1,
    Player2,
}

enum class PlayerColor(
    val label: String,
    val argb: Long,
) {
    Red("Red", 0xFFC62828),
    Blue("Blue", 0xFF1D4ED8),
    Green("Green", 0xFF15803D),
    Purple("Purple", 0xFF7E22CE),
    Orange("Orange", 0xFFEA580C),
    Pink("Pink", 0xFFBE185D),
}

data class Player(
    val id: PlayerId,
    val initials: String,
    val color: PlayerColor,
)

data class Edge(
    val orientation: EdgeOrientation,
    val row: Int,
    val column: Int,
) {
    init {
        val valid = when (orientation) {
            EdgeOrientation.Horizontal -> row in 0 until DotCount && column in 0 until BoxCount
            EdgeOrientation.Vertical -> row in 0 until BoxCount && column in 0 until DotCount
        }
        require(valid) { "Invalid $orientation edge at row=$row column=$column" }
    }
}

data class BoxCell(
    val row: Int,
    val column: Int,
) {
    init {
        require(row in 0 until BoxCount && column in 0 until BoxCount) {
            "Invalid box cell at row=$row column=$column"
        }
    }
}

data class BoxGameState(
    val player1: Player,
    val player2: Player,
    val currentPlayerId: PlayerId = PlayerId.Player1,
    val lines: Map<Edge, PlayerId> = emptyMap(),
    val boxes: Map<BoxCell, PlayerId> = emptyMap(),
) {
    val currentPlayer: Player
        get() = player(currentPlayerId)

    val isGameOver: Boolean
        get() = lines.size == TotalLineCount

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
        if (edge in lines || isGameOver) return this

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
        if (row in 0 until BoxCount && column in 0 until BoxCount) BoxCell(row, column) else null

    companion object {
        fun newGame(
            player1Initials: String,
            player2Initials: String,
            player1Color: PlayerColor = PlayerColor.Red,
            player2Color: PlayerColor = PlayerColor.Blue,
        ): BoxGameState =
            BoxGameState(
                player1 = Player(PlayerId.Player1, normalizeInitials(player1Initials), player1Color),
                player2 = Player(PlayerId.Player2, normalizeInitials(player2Initials), player2Color),
            )
    }
}

fun PlayerId.next(): PlayerId = when (this) {
    PlayerId.Player1 -> PlayerId.Player2
    PlayerId.Player2 -> PlayerId.Player1
}

fun allEdges(): List<Edge> =
    buildList {
        repeat(DotCount) { row ->
            repeat(BoxCount) { column ->
                add(Edge(EdgeOrientation.Horizontal, row, column))
            }
        }
        repeat(BoxCount) { row ->
            repeat(DotCount) { column ->
                add(Edge(EdgeOrientation.Vertical, row, column))
            }
        }
    }

fun normalizeInitials(input: String): String =
    input
        .filter { it.isLetterOrDigit() }
        .take(3)
        .uppercase()
