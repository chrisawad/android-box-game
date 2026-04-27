package com.example.boxgame

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoxGameTheme {
                BoxGameApp()
            }
        }
    }
}

@Composable
private fun BoxGameTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFFF8A80),
            secondary = Color(0xFF8AB4F8),
            background = Color(0xFF101418),
            surface = Color(0xFF18202A),
            surfaceVariant = Color(0xFF243241),
            onPrimary = Color(0xFF2D0505),
            onSecondary = Color(0xFF031A3E),
            onBackground = Color(0xFFE8EEF7),
            onSurface = Color(0xFFE8EEF7),
            outline = Color(0xFF8B98A8),
            outlineVariant = Color(0xFF465567),
        )
    } else {
        lightColorScheme(
            primary = PlayerColor.Red.toComposeColor(),
            secondary = PlayerColor.Blue.toComposeColor(),
            background = Color(0xFFF6F8FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEFF4F8),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF17202A),
            onSurface = Color(0xFF17202A),
            outline = Color(0xFF6B7280),
            outlineVariant = Color(0xFFD8E0EA),
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}

@Composable
private fun BoxGameApp() {
    var player1Initials by rememberSaveable { mutableStateOf("") }
    var player2Initials by rememberSaveable { mutableStateOf("") }
    var player1Color by rememberSaveable { mutableStateOf(PlayerColor.Red) }
    var player2Color by rememberSaveable { mutableStateOf(PlayerColor.Blue) }
    var gameState by remember { mutableStateOf<BoxGameState?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val state = gameState
        if (state == null) {
            SetupScreen(
                player1Initials = player1Initials,
                player2Initials = player2Initials,
                player1Color = player1Color,
                player2Color = player2Color,
                onPlayer1InitialsChange = { player1Initials = normalizeInitials(it) },
                onPlayer2InitialsChange = { player2Initials = normalizeInitials(it) },
                onPlayer1ColorChange = { color ->
                    val previousColor = player1Color
                    player1Color = color
                    if (player2Color == color) {
                        player2Color = previousColor
                    }
                },
                onPlayer2ColorChange = { color ->
                    val previousColor = player2Color
                    player2Color = color
                    if (player1Color == color) {
                        player1Color = previousColor
                    }
                },
                onStart = {
                    gameState = BoxGameState.newGame(player1Initials, player2Initials, player1Color, player2Color)
                },
            )
        } else {
            GameScreen(
                state = state,
                onLineSelected = { edge ->
                    gameState = state.placeLine(edge)
                },
                onPlayAgain = {
                    gameState = BoxGameState.newGame(
                        state.player1.initials,
                        state.player2.initials,
                        state.player1.color,
                        state.player2.color,
                    )
                },
                onChangePlayers = {
                    player1Initials = state.player1.initials
                    player2Initials = state.player2.initials
                    player1Color = state.player1.color
                    player2Color = state.player2.color
                    gameState = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    player1Initials: String,
    player2Initials: String,
    player1Color: PlayerColor,
    player2Color: PlayerColor,
    onPlayer1InitialsChange: (String) -> Unit,
    onPlayer2InitialsChange: (String) -> Unit,
    onPlayer1ColorChange: (PlayerColor) -> Unit,
    onPlayer2ColorChange: (PlayerColor) -> Unit,
    onStart: () -> Unit,
) {
    val player1ComposeColor = player1Color.toComposeColor()
    val player2ComposeColor = player2Color.toComposeColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Box Game",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Set up players",
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        InitialsField(
            value = player1Initials,
            onValueChange = onPlayer1InitialsChange,
            label = "Player 1",
            color = player1ComposeColor,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PlayerColorPicker(
            selectedColor = player1Color,
            onColorSelected = onPlayer1ColorChange,
        )
        Spacer(modifier = Modifier.height(18.dp))
        InitialsField(
            value = player2Initials,
            onValueChange = onPlayer2InitialsChange,
            label = "Player 2",
            color = player2ComposeColor,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PlayerColorPicker(
            selectedColor = player2Color,
            onColorSelected = onPlayer2ColorChange,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onStart,
            enabled = player1Initials.isNotBlank() && player2Initials.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = player1ComposeColor,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "Start game",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitialsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    color: Color,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color, CircleShape),
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = color,
            focusedLabelColor = color,
            cursorColor = color,
        ),
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun PlayerColorPicker(
    selectedColor: PlayerColor,
    onColorSelected: (PlayerColor) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerColor.entries.forEach { option ->
            val isSelected = option == selectedColor
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(option.toComposeColor(), CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onColorSelected(option) }
                    .semantics {
                        contentDescription = "${option.label} color"
                        selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun GameScreen(
    state: BoxGameState,
    onLineSelected: (Edge) -> Unit,
    onPlayAgain: () -> Unit,
    onChangePlayers: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Box Game",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            TextButton(
                onClick = onChangePlayers,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Players")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlayerScorePanel(
                player = state.player1,
                score = state.player1Score,
                isCurrent = state.currentPlayerId == PlayerId.Player1 && !state.isGameOver,
                modifier = Modifier.weight(1f),
            )
            PlayerScorePanel(
                player = state.player2,
                score = state.player2Score,
                isCurrent = state.currentPlayerId == PlayerId.Player2 && !state.isGameOver,
                modifier = Modifier.weight(1f),
            )
        }

        StatusPanel(
            state = state,
            onPlayAgain = onPlayAgain,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            BoxGameBoard(
                state = state,
                onLineSelected = onLineSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Text(
            text = "${state.lines.size}/$TotalLineCount lines  |  ${BoxCount * BoxCount - state.boxes.size} boxes left",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlayerScorePanel(
    player: Player,
    score: Int,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = player.color.toComposeColor()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) color else MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isCurrent) 3.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = player.id.playerLabel(),
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = player.initials,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "$score boxes",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StatusPanel(
    state: BoxGameState,
    onPlayAgain: () -> Unit,
) {
    val statusColor = when {
        state.isTie -> MaterialTheme.colorScheme.onSurface
        state.isGameOver -> state.winner?.let { state.player(it).color.toComposeColor() }
            ?: MaterialTheme.colorScheme.onSurface
        else -> state.currentPlayer.color.toComposeColor()
    }

    val statusText = when {
        state.isTie -> "Tie game"
        state.isGameOver -> "${state.player(state.winner ?: PlayerId.Player1).initials} wins"
        else -> "${state.currentPlayer.initials}'s turn"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            if (state.isGameOver) {
                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = statusColor,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Play again")
                }
            }
        }
    }
}

@Composable
private fun BoxGameBoard(
    state: BoxGameState,
    onLineSelected: (Edge) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f)
    val dotColor = MaterialTheme.colorScheme.onSurface
    val boardBackground = if (isSystemInDarkTheme()) Color(0xFF121A23) else Color(0xFFFDFEFF)

    Canvas(
        modifier = modifier.pointerInput(state.lines, state.isGameOver) {
            detectTapGestures { tap ->
                if (!state.isGameOver) {
                    nearestOpenEdge(
                        tap = tap,
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                        takenLines = state.lines.keys,
                    )?.let(onLineSelected)
                }
            }
        },
    ) {
        val metrics = boardMetrics(size) ?: return@Canvas
        drawRoundRect(
            color = boardBackground,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(20f, 20f),
        )

        state.boxes.forEach { (box, owner) ->
            val topLeft = metrics.dot(box.row, box.column)
            val ownerColor = state.player(owner).color.toComposeColor()
            drawRoundRect(
                color = ownerColor.copy(alpha = 0.14f),
                topLeft = topLeft,
                size = Size(metrics.gap, metrics.gap),
                cornerRadius = CornerRadius(metrics.gap * 0.12f, metrics.gap * 0.12f),
            )
        }

        allEdges().forEach { edge ->
            val (start, end) = edge.points(metrics)
            val owner = state.lines[edge]
            val ownerColor = owner?.let { state.player(it).color.toComposeColor() }
            drawLine(
                color = ownerColor ?: openLineColor,
                start = start,
                end = end,
                strokeWidth = if (owner == null) metrics.gap * 0.07f else metrics.gap * 0.12f,
                cap = StrokeCap.Round,
            )
        }

        repeat(DotCount) { row ->
            repeat(DotCount) { column ->
                drawCircle(
                    color = dotColor,
                    radius = metrics.gap * 0.075f,
                    center = metrics.dot(row, column),
                )
            }
        }

        drawClaimedInitials(state, metrics)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClaimedInitials(
    state: BoxGameState,
    metrics: BoardMetrics,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = metrics.gap * 0.32f
    }

    state.boxes.forEach { (box, owner) ->
        val center = metrics.dot(box.row, box.column) + Offset(metrics.gap / 2f, metrics.gap / 2f)
        paint.color = state.player(owner).color.toComposeColor().toArgb()
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        drawIntoCanvas {
            it.nativeCanvas.drawText(state.player(owner).initials, center.x, baseline, paint)
        }
    }
}

private data class BoardMetrics(
    val left: Float,
    val top: Float,
    val gap: Float,
) {
    fun dot(row: Int, column: Int): Offset =
        Offset(left + column * gap, top + row * gap)
}

private fun boardMetrics(size: Size): BoardMetrics? {
    val boardSize = min(size.width, size.height)
    if (boardSize <= 0f) return null

    val margin = boardSize * 0.1f
    val gap = (boardSize - margin * 2f) / (DotCount - 1)
    val left = (size.width - boardSize) / 2f + margin
    val top = (size.height - boardSize) / 2f + margin
    return BoardMetrics(left = left, top = top, gap = gap)
}

private fun nearestOpenEdge(
    tap: Offset,
    canvasSize: Size,
    takenLines: Set<Edge>,
): Edge? {
    val metrics = boardMetrics(canvasSize) ?: return null
    val threshold = max(22f, metrics.gap * 0.22f)

    return allEdges()
        .asSequence()
        .filterNot { it in takenLines }
        .map { edge ->
            val (start, end) = edge.points(metrics)
            edge to tap.distanceToSegment(start, end)
        }
        .filter { (_, distance) -> distance <= threshold }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun Edge.points(metrics: BoardMetrics): Pair<Offset, Offset> =
    when (orientation) {
        EdgeOrientation.Horizontal -> metrics.dot(row, column) to metrics.dot(row, column + 1)
        EdgeOrientation.Vertical -> metrics.dot(row, column) to metrics.dot(row + 1, column)
    }

private fun Offset.distanceToSegment(start: Offset, end: Offset): Float {
    val segmentX = end.x - start.x
    val segmentY = end.y - start.y
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    if (lengthSquared == 0f) return distanceTo(start)

    val rawProjection = ((x - start.x) * segmentX + (y - start.y) * segmentY) / lengthSquared
    val projection = rawProjection.coerceIn(0f, 1f)
    val closest = Offset(start.x + projection * segmentX, start.y + projection * segmentY)
    return distanceTo(closest)
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private fun PlayerColor.toComposeColor(): Color = Color(argb)

private fun PlayerId.playerLabel(): String = when (this) {
    PlayerId.Player1 -> "Player 1"
    PlayerId.Player2 -> "Player 2"
}

@Preview(showBackground = true)
@Composable
private fun BoxGamePreview() {
    BoxGameTheme {
        GameScreen(
            state = BoxGameState.newGame("R", "B")
                .placeLine(Edge(EdgeOrientation.Horizontal, 0, 0))
                .placeLine(Edge(EdgeOrientation.Vertical, 0, 0))
                .placeLine(Edge(EdgeOrientation.Vertical, 0, 1))
                .placeLine(Edge(EdgeOrientation.Horizontal, 1, 0)),
            onLineSelected = {},
            onPlayAgain = {},
            onChangePlayers = {},
        )
    }
}
