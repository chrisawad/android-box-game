package com.chrisawad.boxgame

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.google.firebase.FirebaseApp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
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
    var setupMode by rememberSaveable { mutableStateOf(SetupMode.Local) }
    var player1Initials by rememberSaveable { mutableStateOf("") }
    var player2Initials by rememberSaveable { mutableStateOf("") }
    var player1Color by rememberSaveable { mutableStateOf(PlayerColor.Red) }
    var player2Color by rememberSaveable { mutableStateOf(PlayerColor.Blue) }
    var boardColumns by rememberSaveable { mutableIntStateOf(DefaultBoardColumns) }
    var boardRows by rememberSaveable { mutableIntStateOf(DefaultBoardRows) }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var gameState by remember { mutableStateOf<BoxGameState?>(null) }
    var savedMultiplayerSession by rememberSaveable { mutableStateOf<SavedMultiplayerSession?>(null) }
    var multiplayerRoom by remember { mutableStateOf<MultiplayerRoom?>(null) }
    var multiplayerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var multiplayerBusy by rememberSaveable { mutableStateOf(false) }
    val multiplayerRepository = remember { MultiplayerRepository() }
    val multiplayerSession = remember(savedMultiplayerSession, multiplayerRepository) {
        savedMultiplayerSession?.let { savedSession ->
            multiplayerRepository.restoreSession(
                roomCode = savedSession.roomCode,
                uid = savedSession.uid,
                localPlayerId = savedSession.localPlayerId,
            )
        }
    }

    DisposableEffect(multiplayerSession) {
        val session = multiplayerSession
        if (session == null) {
            onDispose {}
        } else {
            session.listen(
                onRoomChanged = { room -> multiplayerRoom = room },
                onError = { message -> multiplayerMessage = message },
            )
            onDispose {
                session.detach()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val session = multiplayerSession
        val state = gameState
        if (session != null) {
            MultiplayerGameScreen(
                session = session,
                room = multiplayerRoom,
                message = multiplayerMessage,
                rematchBusy = multiplayerBusy,
                onLineSelected = { edge ->
                    multiplayerMessage = null
                    session.submitMove(edge) { result ->
                        result.onFailure {
                            multiplayerMessage = it.message ?: "Move was rejected."
                        }
                    }
                },
                onLeave = {
                    multiplayerBusy = false
                    multiplayerMessage = null
                    session.leave {
                        multiplayerRoom = null
                        savedMultiplayerSession = null
                    }
                },
                onRequestRematch = {
                    multiplayerBusy = true
                    multiplayerMessage = null
                    session.requestRematch { result ->
                        multiplayerBusy = false
                        result
                            .onSuccess {
                                multiplayerMessage = null
                            }
                            .onFailure {
                                multiplayerMessage = it.message ?: "Could not start a rematch."
                            }
                    }
                },
            )
        } else if (state == null) {
            SetupScreen(
                player1Initials = player1Initials,
                player2Initials = player2Initials,
                player1Color = player1Color,
                player2Color = player2Color,
                boardColumns = boardColumns,
                boardRows = boardRows,
                joinCode = joinCode,
                multiplayerMessage = multiplayerMessage,
                multiplayerBusy = multiplayerBusy,
                setupMode = setupMode,
                onSetupModeChange = { mode ->
                    setupMode = mode
                    multiplayerMessage = null
                },
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
                onBoardColumnsChange = { boardColumns = it },
                onBoardRowsChange = { boardRows = it },
                onJoinCodeChange = { joinCode = normalizeJoinCode(it) },
                onStart = {
                    multiplayerMessage = null
                    gameState = BoxGameState.newGame(
                        player1Initials,
                        player2Initials,
                        player1Color,
                        player2Color,
                        BoardSize(boardColumns, boardRows),
                    )
                },
                onCreateGame = {
                    multiplayerBusy = true
                    multiplayerMessage = null
                    multiplayerRepository.createGame(
                        playerInitials = player1Initials,
                        playerColor = player1Color,
                        boardSize = BoardSize(boardColumns, boardRows),
                    ) { result ->
                        multiplayerBusy = false
                        result
                            .onSuccess { createdSession ->
                                gameState = null
                                multiplayerRoom = null
                                multiplayerMessage = "Share code ${createdSession.roomCode}"
                                savedMultiplayerSession = createdSession.toSavedMultiplayerSession()
                            }
                            .onFailure {
                                multiplayerMessage = it.message ?: "Could not create a game."
                            }
                    }
                },
                onJoinPublicGame = {
                    multiplayerBusy = true
                    multiplayerMessage = "Looking for a public game..."
                    multiplayerRepository.joinPublicGame(
                        playerInitials = player1Initials,
                        playerColor = player1Color,
                        boardSize = BoardSize(boardColumns, boardRows),
                    ) { result ->
                        multiplayerBusy = false
                        result
                            .onSuccess { publicSession ->
                                gameState = null
                                multiplayerRoom = null
                                multiplayerMessage = null
                                savedMultiplayerSession = publicSession.toSavedMultiplayerSession()
                            }
                            .onFailure {
                                multiplayerMessage = it.message ?: "Could not join a public game."
                            }
                    }
                },
                onJoinGame = {
                    multiplayerBusy = true
                    multiplayerMessage = null
                    multiplayerRepository.joinGame(
                        joinCode = joinCode,
                        playerInitials = player1Initials,
                        playerColor = player1Color,
                    ) { result ->
                        multiplayerBusy = false
                        result
                            .onSuccess { joinedSession ->
                                gameState = null
                                multiplayerRoom = null
                                multiplayerMessage = null
                                savedMultiplayerSession = joinedSession.toSavedMultiplayerSession()
                            }
                            .onFailure {
                                multiplayerMessage = it.message ?: "Could not join that game."
                            }
                    }
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
                        state.boardSize,
                    )
                },
                onChangePlayers = {
                    player1Initials = state.player1.initials
                    player2Initials = state.player2.initials
                    player1Color = state.player1.color
                    player2Color = state.player2.color
                    boardColumns = state.boardSize.columns
                    boardRows = state.boardSize.rows
                    gameState = null
                },
            )
        }
    }
}

@Parcelize
private enum class SetupMode : Parcelable {
    Local,
    Multiplayer,
}

@Parcelize
private data class SavedMultiplayerSession(
    val roomCode: String,
    val uid: String,
    val localPlayerId: PlayerId,
) : Parcelable

private fun MultiplayerSession.toSavedMultiplayerSession(): SavedMultiplayerSession =
    SavedMultiplayerSession(
        roomCode = roomCode,
        uid = uid,
        localPlayerId = localPlayerId,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    player1Initials: String,
    player2Initials: String,
    player1Color: PlayerColor,
    player2Color: PlayerColor,
    boardColumns: Int,
    boardRows: Int,
    joinCode: String,
    multiplayerMessage: String?,
    multiplayerBusy: Boolean,
    setupMode: SetupMode,
    onSetupModeChange: (SetupMode) -> Unit,
    onPlayer1InitialsChange: (String) -> Unit,
    onPlayer2InitialsChange: (String) -> Unit,
    onPlayer1ColorChange: (PlayerColor) -> Unit,
    onPlayer2ColorChange: (PlayerColor) -> Unit,
    onBoardColumnsChange: (Int) -> Unit,
    onBoardRowsChange: (Int) -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onStart: () -> Unit,
    onCreateGame: () -> Unit,
    onJoinPublicGame: () -> Unit,
    onJoinGame: () -> Unit,
) {
    val player1ComposeColor = player1Color.toComposeColor()
    val player2ComposeColor = player2Color.toComposeColor()
    var colorPickerTarget: PlayerId? by remember { mutableStateOf<PlayerId?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
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
            text = if (setupMode == SetupMode.Local) "Local game" else "Multiplayer",
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SetupModePicker(
            selectedMode = setupMode,
            onModeSelected = onSetupModeChange,
        )
        Spacer(modifier = Modifier.height(22.dp))
        InitialsField(
            value = player1Initials,
            onValueChange = onPlayer1InitialsChange,
            label = if (setupMode == SetupMode.Local) "Player 1" else "Your initials",
            color = player1ComposeColor,
            onColorClick = { colorPickerTarget = PlayerId.Player1 },
        )
        if (setupMode == SetupMode.Local) {
            Spacer(modifier = Modifier.height(18.dp))
            InitialsField(
                value = player2Initials,
                onValueChange = onPlayer2InitialsChange,
                label = "Player 2",
                color = player2ComposeColor,
                onColorClick = { colorPickerTarget = PlayerId.Player2 },
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        BoardSizePicker(
            columns = boardColumns,
            rows = boardRows,
            onColumnsChange = onBoardColumnsChange,
            onRowsChange = onBoardRowsChange,
        )
        Spacer(modifier = Modifier.height(28.dp))
        if (setupMode == SetupMode.Local) {
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
        } else {
            Button(
                onClick = onCreateGame,
                enabled = player1Initials.isNotBlank() && !multiplayerBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = player1ComposeColor,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Create Game",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onJoinPublicGame,
                enabled = player1Initials.isNotBlank() && !multiplayerBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Join Public Game",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = joinCode,
                onValueChange = onJoinCodeChange,
                label = { Text("Join code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                ),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onJoinGame,
                enabled = player1Initials.isNotBlank() && joinCode.length == MultiplayerJoinCodeLength && !multiplayerBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = player1ComposeColor,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Join Game",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            if (multiplayerBusy) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        multiplayerMessage?.let { message ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }

    when (colorPickerTarget) {
        PlayerId.Player1 -> PlayerColorDialog(
            title = if (setupMode == SetupMode.Local) "Player 1 color" else "Your player color",
            selectedColor = player1Color,
            onColorSelected = {
                onPlayer1ColorChange(it)
                colorPickerTarget = null
            },
            onDismiss = { colorPickerTarget = null },
        )

        PlayerId.Player2 -> PlayerColorDialog(
            title = "Player 2 color",
            selectedColor = player2Color,
            onColorSelected = {
                onPlayer2ColorChange(it)
                colorPickerTarget = null
            },
            onDismiss = { colorPickerTarget = null },
        )

        null -> Unit
    }

}

@Composable
private fun SetupModePicker(
    selectedMode: SetupMode,
    onModeSelected: (SetupMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SetupMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Button(
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = when (mode) {
                            SetupMode.Local -> "Local Game"
                            SetupMode.Multiplayer -> "Multiplayer"
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
            }
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
    onColorClick: () -> Unit,
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
                    .size(28.dp)
                    .background(color, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(onClick = onColorClick)
                    .semantics {
                        contentDescription = "Choose $label color"
                    },
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
private fun PlayerColorDialog(
    title: String,
    selectedColor: PlayerColor,
    onColorSelected: (PlayerColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerColor.entries.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowColors.forEach { option ->
                            val isSelected = option == selectedColor
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(option.toComposeColor(), CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
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
                                                .size(14.dp)
                                                .background(Color.White, CircleShape),
                                        )
                                    }
                                }
                                Text(
                                    text = option.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }

                        repeat(4 - rowColors.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun BoardSizePicker(
    columns: Int,
    rows: Int,
    onColumnsChange: (Int) -> Unit,
    onRowsChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Board size",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoardDimensionStepper(
                    value = columns,
                    onValueChange = onColumnsChange,
                    modifier = Modifier.weight(1f),
                )
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "X",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                BoardDimensionStepper(
                    value = rows,
                    onValueChange = onRowsChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BoardDimensionStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onValueChange((value - 1).coerceAtLeast(MinBoardBoxes)) },
                enabled = value > MinBoardBoxes,
                shape = CircleShape,
            ) {
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            TextButton(
                onClick = { onValueChange((value + 1).coerceAtMost(MaxBoardBoxes)) },
                enabled = value < MaxBoardBoxes,
                shape = CircleShape,
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MultiplayerGameScreen(
    session: MultiplayerSession,
    room: MultiplayerRoom?,
    message: String?,
    rematchBusy: Boolean,
    onLineSelected: (Edge) -> Unit,
    onLeave: () -> Unit,
    onRequestRematch: () -> Unit,
) {
    val state = room?.gameState
    val opponent = room?.opponentFor(session.localPlayerId)

    if (room == null || state == null || room.status == MultiplayerStatus.Waiting) {
        WaitingRoomScreen(
            roomCode = session.roomCode,
            statusText = when {
                room?.status == MultiplayerStatus.Abandoned -> "Game closed"
                room == null -> "Connecting"
                room.isPublic -> "Waiting for public player"
                else -> "Waiting for opponent"
            },
            message = message,
            onLeave = onLeave,
        )
        return
    }

    val opponentConnected = opponent?.connected == true
    val canMove = room.status == MultiplayerStatus.Active &&
        opponentConnected &&
        state.currentPlayerId == session.localPlayerId &&
        !state.isGameOver
    val canRequestRematch = room.status == MultiplayerStatus.Finished &&
        opponentConnected &&
        state.winner != null &&
        state.winner != session.localPlayerId
    val rematchPromptKey = if (canRequestRematch) {
        finishedGameKey(room, state)
    } else {
        null
    }
    var dismissedRematchPromptKey by remember(rematchPromptKey) { mutableStateOf<String?>(null) }
    var showingRematchPromptFromButton by remember(rematchPromptKey) { mutableStateOf(false) }
    val showRematchPrompt = canRequestRematch &&
        rematchPromptKey != null &&
        (showingRematchPromptFromButton || dismissedRematchPromptKey != rematchPromptKey)

    if (showRematchPrompt) {
        RematchPromptDialog(
            roomCode = room.code,
            rematchBusy = rematchBusy,
            onConfirm = {
                dismissedRematchPromptKey = rematchPromptKey
                showingRematchPromptFromButton = false
                onRequestRematch()
            },
            onDismiss = {
                dismissedRematchPromptKey = rematchPromptKey
                showingRematchPromptFromButton = false
            },
        )
    }

    GameScreen(
        state = state,
        onLineSelected = onLineSelected,
        onPlayAgain = { showingRematchPromptFromButton = true },
        onChangePlayers = onLeave,
        title = "Room ${room.code}",
        changePlayersLabel = "Leave",
        statusOverride = multiplayerStatusText(room, session.localPlayerId, state),
        supportingStatus = multiplayerSupportingStatus(room, session.localPlayerId, message),
        canSelectLines = canMove,
        showPlayAgain = canRequestRematch,
        playAgainLabel = "Rematch",
        playAgainEnabled = !rematchBusy,
    )
}

@Composable
private fun RematchPromptDialog(
    roomCode: String,
    rematchBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rematch?",
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Text("Start a new game in room $roomCode with the same players and board size?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !rematchBusy,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Start rematch")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !rematchBusy,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Not now")
            }
        },
    )
}

@Composable
private fun WaitingRoomScreen(
    roomCode: String,
    statusText: String,
    message: String?,
    onLeave: () -> Unit,
) {
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
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = roomCode,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(
                    onClick = onLeave,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Leave")
                }
            }
        }
    }
}

private fun multiplayerStatusText(
    room: MultiplayerRoom,
    localPlayerId: PlayerId,
    state: BoxGameState,
): String {
    val opponent = room.opponentFor(localPlayerId)
    return when {
        room.status == MultiplayerStatus.Abandoned -> "Opponent left the game"
        opponent == null -> "Waiting for opponent"
        !opponent.connected && room.status == MultiplayerStatus.Active -> "Opponent disconnected"
        state.isTie -> "Tie game"
        state.isGameOver && state.winner == localPlayerId -> "You win"
        state.isGameOver -> "${state.player(state.winner ?: localPlayerId.next()).initials} wins"
        state.currentPlayerId == localPlayerId -> "Your turn"
        else -> "Opponent's turn"
    }
}

private fun multiplayerSupportingStatus(
    room: MultiplayerRoom,
    localPlayerId: PlayerId,
    message: String?,
): String =
    listOfNotNull(
        "Code ${room.code}",
        localPlayerId.playerLabel(),
        message,
    ).joinToString("  |  ")

private fun finishedGameKey(
    room: MultiplayerRoom,
    state: BoxGameState,
): String = listOf(
    room.code,
    state.winner?.name.orEmpty(),
    state.player1Score.toString(),
    state.player2Score.toString(),
    state.lines.hashCode().toString(),
    state.boxes.hashCode().toString(),
).joinToString(":")

@Composable
private fun GameScreen(
    state: BoxGameState,
    onLineSelected: (Edge) -> Unit,
    onPlayAgain: () -> Unit,
    onChangePlayers: () -> Unit,
    title: String = "Box Game",
    changePlayersLabel: String = "Players",
    statusOverride: String? = null,
    supportingStatus: String? = null,
    canSelectLines: Boolean = true,
    showPlayAgain: Boolean = true,
    playAgainLabel: String = "Play again",
    playAgainEnabled: Boolean = true,
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
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            TextButton(
                onClick = onChangePlayers,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(changePlayersLabel)
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
            statusOverride = statusOverride,
            supportingStatus = supportingStatus,
            showPlayAgain = showPlayAgain,
            playAgainLabel = playAgainLabel,
            playAgainEnabled = playAgainEnabled,
        )

        val boardAspectRatio = state.boardSize.columns.toFloat() / state.boardSize.rows.toFloat()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val availableAspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val boardModifier = if (availableAspectRatio > boardAspectRatio) {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(boardAspectRatio)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(boardAspectRatio)
            }

            Surface(
                modifier = boardModifier,
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                BoxGameBoard(
                    state = state,
                    onLineSelected = onLineSelected,
                    enabled = canSelectLines,
                    modifier = Modifier
                        .fillMaxSize(),
                )
            }
        }

        Text(
            text = "${state.lines.size}/${state.boardSize.totalLineCount} lines  |  ${state.boardSize.boxCount - state.boxes.size} boxes left",
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
    statusOverride: String? = null,
    supportingStatus: String? = null,
    showPlayAgain: Boolean = true,
    playAgainLabel: String = "Play again",
    playAgainEnabled: Boolean = true,
) {
    val statusColor = when {
        state.isTie -> MaterialTheme.colorScheme.onSurface
        state.isGameOver -> state.winner?.let { state.player(it).color.toComposeColor() }
            ?: MaterialTheme.colorScheme.onSurface
        else -> state.currentPlayer.color.toComposeColor()
    }

    val statusText = statusOverride ?: when {
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                supportingStatus?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (state.isGameOver && showPlayAgain) {
                Button(
                    onClick = onPlayAgain,
                    enabled = playAgainEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = statusColor,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(playAgainLabel)
                }
            }
        }
    }
}

@Composable
private fun BoxGameBoard(
    state: BoxGameState,
    onLineSelected: (Edge) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val openLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f)
    val dotColor = MaterialTheme.colorScheme.onSurface
    val boardBackground = if (isSystemInDarkTheme()) Color(0xFF121A23) else Color(0xFFFDFEFF)

    Canvas(
        modifier = modifier.pointerInput(enabled, state.lines, state.isGameOver, state.boardSize) {
            detectTapGestures { tap ->
                if (enabled && !state.isGameOver) {
                    nearestOpenEdge(
                        tap = tap,
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                        boardSize = state.boardSize,
                        takenLines = state.lines.keys,
                    )?.let(onLineSelected)
                }
            }
        },
    ) {
        val metrics = boardMetrics(size, state.boardSize) ?: return@Canvas
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

        allEdges(state.boardSize).forEach { edge ->
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

        repeat(state.boardSize.dotRows) { row ->
            repeat(state.boardSize.dotColumns) { column ->
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

private fun DrawScope.drawClaimedInitials(
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

private fun boardMetrics(size: Size, boardSize: BoardSize): BoardMetrics? {
    val availableWidth = size.width
    val availableHeight = size.height
    if (availableWidth <= 0f || availableHeight <= 0f) return null

    val rawGap = min(availableWidth / boardSize.columns, availableHeight / boardSize.rows)
    val margin = rawGap * 0.08f
    val drawableWidth = (availableWidth - margin * 2f).coerceAtLeast(1f)
    val drawableHeight = (availableHeight - margin * 2f).coerceAtLeast(1f)
    val gap = min(drawableWidth / boardSize.columns, drawableHeight / boardSize.rows)
    val boardWidth = gap * boardSize.columns
    val boardHeight = gap * boardSize.rows
    val left = (availableWidth - boardWidth) / 2f
    val top = (availableHeight - boardHeight) / 2f
    return BoardMetrics(left = left, top = top, gap = gap)
}

private fun nearestOpenEdge(
    tap: Offset,
    canvasSize: Size,
    boardSize: BoardSize,
    takenLines: Set<Edge>,
): Edge? {
    val metrics = boardMetrics(canvasSize, boardSize) ?: return null
    val threshold = max(22f, metrics.gap * 0.22f)

    return allEdges(boardSize)
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
            state = BoxGameState.newGame("A", "B", boardSize = BoardSize(6,8))
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


@Preview(showBackground = true)
@Composable
private fun SetupScreenPreview() {
    BoxGameTheme {
        SetupScreen(
            player1Initials="A",
            player2Initials="B",
            player1Color=PlayerColor.Red,
            player2Color=PlayerColor.Blue,
            boardColumns=8,
            boardRows=10,
            joinCode="ABC123",
            multiplayerMessage=null,
            multiplayerBusy=false,
            setupMode=SetupMode.Local,
            onSetupModeChange={},
            onPlayer1InitialsChange={ },
            onPlayer2InitialsChange={ },
            onPlayer1ColorChange={},
            onPlayer2ColorChange={},
            onBoardColumnsChange={},
            onBoardRowsChange={},
            onJoinCodeChange={},
            onStart={},
            onCreateGame={},
            onJoinPublicGame={},
            onJoinGame={},
        )
    }
}
