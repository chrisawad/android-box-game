package com.chrisawad.boxgame

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

const val MultiplayerJoinCodeLength = 6
private const val MaxCreateAttempts = 8
private const val MaxJoinLookupAttempts = 3
private const val PublicGameLookupLimit = 10
private const val JoinLookupRetryDelayMillis = 300L
private const val NoRoomRejectionReason = "no_room"
private const val RoomRetentionMillis = 24 * 60 * 60 * 1000L
private const val RoomCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val MultiplayerLogTag = "BoxGameMultiplayer"

enum class MultiplayerStatus(
    val wireName: String,
) {
    Waiting("waiting"),
    Active("active"),
    Finished("finished"),
    Abandoned("abandoned");

    companion object {
        fun fromWireName(value: Any?): MultiplayerStatus? {
            val wireName = value as? String
            return entries.firstOrNull { it.wireName == wireName }
        }
    }
}

data class MultiplayerPlayer(
    val uid: String,
    val playerId: PlayerId,
    val initials: String,
    val color: PlayerColor,
    val connected: Boolean,
)

data class MultiplayerRoom(
    val code: String,
    val status: MultiplayerStatus,
    val hostUid: String,
    val guestUid: String?,
    val player1: MultiplayerPlayer,
    val player2: MultiplayerPlayer?,
    val gameState: BoxGameState?,
    val isPublic: Boolean,
) {
    fun playerForUid(uid: String): MultiplayerPlayer? =
        when (uid) {
            player1.uid -> player1
            player2?.uid -> player2
            else -> null
        }

    fun playerForInitials(initials: String): MultiplayerPlayer? {
        val normalizedInitials = normalizeInitials(initials)
        return listOfNotNull(player1, player2)
            .firstOrNull { it.initials == normalizedInitials }
    }

    fun opponentFor(playerId: PlayerId): MultiplayerPlayer? =
        when (playerId) {
            PlayerId.Player1 -> player2
            PlayerId.Player2 -> player1
        }
}

class MultiplayerException(message: String) : Exception(message)

private fun Exception?.toMultiplayerAuthException(): Exception {
    val authCode = (this as? FirebaseAuthException)?.errorCode
    return if (authCode == "CONFIGURATION_NOT_FOUND") {
        MultiplayerException("Firebase Auth is not configured. Enable Authentication and the Anonymous provider in Firebase console.")
    } else {
        this ?: MultiplayerException("Anonymous sign-in failed.")
    }
}

class MultiplayerRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
) {
    private val roomsRef: DatabaseReference = database.getReference("gameRooms")
    private val publicGamesRef: DatabaseReference = database.getReference("publicGames")

    fun createGame(
        playerInitials: String,
        playerColor: PlayerColor,
        boardSize: BoardSize,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        ensureSignedIn { authResult ->
            authResult
                .onSuccess { uid ->
                    tryCreateGame(
                        uid = uid,
                        playerInitials = normalizeInitials(playerInitials),
                        playerColor = playerColor,
                        boardSize = boardSize,
                        isPublic = false,
                        attemptsLeft = MaxCreateAttempts,
                        onResult = onResult,
                    )
                }
                .onFailure { onResult(Result.failure(it)) }
        }
    }

    fun joinGame(
        joinCode: String,
        playerInitials: String,
        playerColor: PlayerColor,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        val code = normalizeJoinCode(joinCode)
        if (code.length != MultiplayerJoinCodeLength) {
            onResult(Result.failure(MultiplayerException("Enter the $MultiplayerJoinCodeLength-character join code.")))
            return
        }

        ensureSignedIn { authResult ->
            authResult
                .onSuccess { uid ->
                    joinExistingRoom(
                        uid = uid,
                        code = code,
                        playerInitials = normalizeInitials(playerInitials),
                        playerColor = playerColor,
                        onResult = onResult,
                    )
                }
                .onFailure { onResult(Result.failure(it)) }
        }
    }

    fun joinPublicGame(
        playerInitials: String,
        playerColor: PlayerColor,
        boardSize: BoardSize,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        ensureSignedIn { authResult ->
            authResult
                .onSuccess { uid ->
                    joinPublicGameOrCreate(
                        uid = uid,
                        playerInitials = normalizeInitials(playerInitials),
                        playerColor = playerColor,
                        boardSize = boardSize,
                        onResult = onResult,
                    )
                }
                .onFailure { onResult(Result.failure(it)) }
        }
    }

    fun restoreSession(
        roomCode: String,
        uid: String,
        localPlayerId: PlayerId,
    ): MultiplayerSession {
        val code = normalizeJoinCode(roomCode)
        return sessionFor(code, uid, localPlayerId)
    }

    private fun ensureSignedIn(onResult: (Result<String>) -> Unit) {
        auth.currentUser?.uid?.let {
            onResult(Result.success(it))
            return
        }

        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                val uid = auth.currentUser?.uid
                if (task.isSuccessful && uid != null) {
                    onResult(Result.success(uid))
                } else {
                    Log.w(MultiplayerLogTag, "Anonymous sign-in failed", task.exception)
                    onResult(
                        Result.failure(
                            task.exception.toMultiplayerAuthException(),
                        ),
                    )
                }
            }
    }

    private fun tryCreateGame(
        uid: String,
        playerInitials: String,
        playerColor: PlayerColor,
        boardSize: BoardSize,
        isPublic: Boolean,
        attemptsLeft: Int,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        if (attemptsLeft <= 0) {
            onResult(Result.failure(MultiplayerException("Could not find an unused join code. Try again.")))
            return
        }

        val code = generateJoinCode()
        val roomRef = roomsRef.child(code)

        roomRef.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val roomHasExpired = currentData.child("expiresAt").value
                        .asLongOrNull()
                        ?.let { it < System.currentTimeMillis() }
                        ?: false
                    if (currentData.value != null && !roomHasExpired) {
                        return Transaction.abort()
                    }

                    currentData.value = initialRoomMap(
                        code = code,
                        uid = uid,
                        playerInitials = playerInitials,
                        playerColor = playerColor,
                        boardSize = boardSize,
                        isPublic = isPublic,
                    )
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?,
                ) {
                    when {
                        error != null -> {
                            Log.w(MultiplayerLogTag, "Create room transaction failed for $code", error.toException())
                            onResult(Result.failure(error.toException()))
                        }
                        committed -> {
                            val session = sessionFor(code, uid, PlayerId.Player1, roomRef)
                            if (isPublic) {
                                publishPublicGame(code, uid, session, onResult)
                            } else {
                                onResult(Result.success(session))
                            }
                        }
                        currentData?.exists() == true -> tryCreateGame(
                            uid = uid,
                            playerInitials = playerInitials,
                            playerColor = playerColor,
                            boardSize = boardSize,
                            isPublic = isPublic,
                            attemptsLeft = attemptsLeft - 1,
                            onResult = onResult,
                        )
                        else -> onResult(Result.failure(MultiplayerException("Game room was not created.")))
                    }
                }
            },
        )
    }

    private fun joinExistingRoom(
        uid: String,
        code: String,
        playerInitials: String,
        playerColor: PlayerColor,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        val roomRef = roomsRef.child(code)
        val rejectionReason = AtomicReference<String?>()
        val joinedPlayerId = AtomicReference<PlayerId?>()
        lookupRoomThenJoin(
            roomRef = roomRef,
            uid = uid,
            code = code,
            playerInitials = playerInitials,
            playerColor = playerColor,
            rejectionReason = rejectionReason,
            joinedPlayerId = joinedPlayerId,
            joinOptions = JoinOptions(),
            attemptsLeft = MaxJoinLookupAttempts,
            onResult = onResult,
        )
    }

    private fun joinPublicGameOrCreate(
        uid: String,
        playerInitials: String,
        playerColor: PlayerColor,
        boardSize: BoardSize,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        publicGamesRef
            .orderByChild("expiresAt")
            .startAt(now.toDouble())
            .limitToFirst(PublicGameLookupLimit)
            .get()
            .addOnCompleteListener { task ->
                when {
                    task.isSuccessful -> {
                        val candidateCodes = task.result?.children
                            ?.mapNotNull { it.toPublicGameCodeOrNull(now) }
                            ?.distinct()
                            .orEmpty()
                        joinPublicCandidatesOrCreate(
                            candidateCodes = candidateCodes,
                            candidateIndex = 0,
                            uid = uid,
                            playerInitials = playerInitials,
                            playerColor = playerColor,
                            boardSize = boardSize,
                            onResult = onResult,
                        )
                    }
                    else -> {
                        val exception = task.exception ?: MultiplayerException("Could not look up public games.")
                        Log.w(MultiplayerLogTag, "Public game lookup failed", exception)
                        onResult(Result.failure(exception))
                    }
                }
            }
    }

    private fun joinPublicCandidatesOrCreate(
        candidateCodes: List<String>,
        candidateIndex: Int,
        uid: String,
        playerInitials: String,
        playerColor: PlayerColor,
        boardSize: BoardSize,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        if (candidateIndex >= candidateCodes.size) {
            tryCreateGame(
                uid = uid,
                playerInitials = playerInitials,
                playerColor = playerColor,
                boardSize = boardSize,
                isPublic = true,
                attemptsLeft = MaxCreateAttempts,
                onResult = onResult,
            )
            return
        }

        val code = candidateCodes[candidateIndex]
        val rejectionReason = AtomicReference<String?>()
        val joinedPlayerId = AtomicReference<PlayerId?>()
        lookupRoomThenJoin(
            roomRef = roomsRef.child(code),
            uid = uid,
            code = code,
            playerInitials = playerInitials,
            playerColor = playerColor,
            rejectionReason = rejectionReason,
            joinedPlayerId = joinedPlayerId,
            joinOptions = JoinOptions(
                allowInitialsReconnect = false,
                requireConnectedHost = true,
            ),
            attemptsLeft = MaxJoinLookupAttempts,
            onResult = { result ->
                result
                    .onSuccess { onResult(Result.success(it)) }
                    .onFailure { exception ->
                        if (exception is MultiplayerException) {
                            publicGamesRef.child(code).removeValue()
                            joinPublicCandidatesOrCreate(
                                candidateCodes = candidateCodes,
                                candidateIndex = candidateIndex + 1,
                                uid = uid,
                                playerInitials = playerInitials,
                                playerColor = playerColor,
                                boardSize = boardSize,
                                onResult = onResult,
                            )
                        } else {
                            onResult(Result.failure(exception))
                        }
                    }
            },
        )
    }

    private fun lookupRoomThenJoin(
        roomRef: DatabaseReference,
        uid: String,
        code: String,
        playerInitials: String,
        playerColor: PlayerColor,
        rejectionReason: AtomicReference<String?>,
        joinedPlayerId: AtomicReference<PlayerId?>,
        joinOptions: JoinOptions,
        attemptsLeft: Int,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        roomRef.get()
            .addOnCompleteListener { task ->
                when {
                    task.isSuccessful && task.result?.exists() == true -> runJoinTransaction(
                        roomRef = roomRef,
                        uid = uid,
                        code = code,
                        playerInitials = playerInitials,
                        playerColor = playerColor,
                        rejectionReason = rejectionReason,
                        joinedPlayerId = joinedPlayerId,
                        joinOptions = joinOptions,
                        prefetchedRoomValue = task.result?.value,
                        emptySnapshotRetriesLeft = attemptsLeft - 1,
                        onResult = onResult,
                    )
                    task.isSuccessful && attemptsLeft > 1 -> retryJoinLookup(
                        roomRef = roomRef,
                        uid = uid,
                        code = code,
                        playerInitials = playerInitials,
                        playerColor = playerColor,
                        rejectionReason = rejectionReason,
                        joinedPlayerId = joinedPlayerId,
                        joinOptions = joinOptions,
                        attemptsLeft = attemptsLeft - 1,
                        onResult = onResult,
                    )
                    task.isSuccessful -> onResult(noRoomResult(code))
                    else -> {
                        val exception = task.exception ?: MultiplayerException("Could not look up that game room.")
                        Log.w(MultiplayerLogTag, "Room lookup failed for $code", exception)
                        onResult(Result.failure(exception))
                    }
                }
            }
    }

    private fun retryJoinLookup(
        roomRef: DatabaseReference,
        uid: String,
        code: String,
        playerInitials: String,
        playerColor: PlayerColor,
        rejectionReason: AtomicReference<String?>,
        joinedPlayerId: AtomicReference<PlayerId?>,
        joinOptions: JoinOptions,
        attemptsLeft: Int,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        Handler(Looper.getMainLooper()).postDelayed(
            {
                lookupRoomThenJoin(
                    roomRef = roomRef,
                    uid = uid,
                    code = code,
                    playerInitials = playerInitials,
                    playerColor = playerColor,
                    rejectionReason = rejectionReason,
                    joinedPlayerId = joinedPlayerId,
                    joinOptions = joinOptions,
                    attemptsLeft = attemptsLeft,
                    onResult = onResult,
                )
            },
            JoinLookupRetryDelayMillis,
        )
    }

    private fun runJoinTransaction(
        roomRef: DatabaseReference,
        uid: String,
        code: String,
        playerInitials: String,
        playerColor: PlayerColor,
        rejectionReason: AtomicReference<String?>,
        joinedPlayerId: AtomicReference<PlayerId?>,
        joinOptions: JoinOptions,
        prefetchedRoomValue: Any?,
        emptySnapshotRetriesLeft: Int,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        roomRef.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.value == null && prefetchedRoomValue != null) {
                        currentData.value = prefetchedRoomValue
                    }
                    val room = currentData.toMultiplayerRoomOrNull(code)
                    if (room == null) {
                        rejectionReason.set(NoRoomRejectionReason)
                        return Transaction.abort()
                    }
                    val existingPlayer = room.playerForUid(uid)
                        ?: if (joinOptions.allowInitialsReconnect) {
                            room.playerForInitials(playerInitials)
                        } else {
                            null
                        }
                    if (existingPlayer != null) {
                        if (existingPlayer.uid != uid && existingPlayer.connected) {
                            rejectionReason.set("That player is already connected.")
                            return Transaction.abort()
                        }
                        joinedPlayerId.set(existingPlayer.playerId)
                        currentData.markPlayerConnected(existingPlayer.playerId, uid)
                        currentData.restoreStatusForReconnect(room)
                        currentData.child("updatedAt").value = ServerValue.TIMESTAMP
                        currentData.extendExpiration()
                        return Transaction.success(currentData)
                    }
                    if (joinOptions.requireConnectedHost && !room.player1.connected) {
                        rejectionReason.set("That public game is no longer waiting for a connected player.")
                        return Transaction.abort()
                    }
                    if (room.status == MultiplayerStatus.Abandoned) {
                        rejectionReason.set("That game is no longer active.")
                        return Transaction.abort()
                    }
                    if (room.status != MultiplayerStatus.Waiting) {
                        rejectionReason.set("That game has already started. Enter the initials you used in this game to rejoin.")
                        return Transaction.abort()
                    }
                    if (room.guestUid != null || room.player2 != null) {
                        rejectionReason.set("That game room is already full.")
                        return Transaction.abort()
                    }

                    currentData.child("guestUid").value = uid
                    currentData.child("status").value = MultiplayerStatus.Active.wireName
                    currentData.child("players").child(PlayerId.Player2.playerKey()).value = playerMap(
                        uid = uid,
                        playerId = PlayerId.Player2,
                        playerInitials = playerInitials,
                        playerColor = playerColor,
                    )
                    joinedPlayerId.set(PlayerId.Player2)
                    currentData.child("updatedAt").value = ServerValue.TIMESTAMP
                    currentData.extendExpiration()
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?,
                ) {
                    when {
                        error != null -> {
                            Log.w(MultiplayerLogTag, "Join room transaction failed for $code", error.toException())
                            onResult(Result.failure(error.toException()))
                        }
                        committed -> {
                            val localPlayerId = joinedPlayerId.get() ?: PlayerId.Player2
                            if (localPlayerId == PlayerId.Player2) {
                                publicGamesRef.child(code).removeValue()
                            }
                            onResult(Result.success(sessionFor(code, uid, localPlayerId, roomRef)))
                        }
                        rejectionReason.get() == NoRoomRejectionReason && emptySnapshotRetriesLeft > 0 -> retryJoinLookup(
                            roomRef = roomRef,
                            uid = uid,
                            code = code,
                            playerInitials = playerInitials,
                            playerColor = playerColor,
                            rejectionReason = rejectionReason,
                            joinedPlayerId = joinedPlayerId,
                            joinOptions = joinOptions,
                            attemptsLeft = emptySnapshotRetriesLeft,
                            onResult = onResult,
                        )
                        else -> onResult(
                            Result.failure(
                                rejectionReason.get()?.let { reason ->
                                    if (reason == NoRoomRejectionReason) {
                                        MultiplayerException("No game room exists for code $code.")
                                    } else {
                                        MultiplayerException(reason)
                                    }
                                } ?: MultiplayerException("Could not join that game."),
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun noRoomResult(code: String): Result<MultiplayerSession> =
        Result.failure(MultiplayerException("No game room exists for code $code."))

    private fun publishPublicGame(
        code: String,
        uid: String,
        session: MultiplayerSession,
        onResult: (Result<MultiplayerSession>) -> Unit,
    ) {
        publicGamesRef.child(code).setValue(publicGameMap(code, uid))
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(Result.success(session))
                } else {
                    val exception = task.exception ?: MultiplayerException("Could not publish the public game.")
                    Log.w(MultiplayerLogTag, "Publish public game failed for $code", exception)
                    onResult(Result.failure(exception))
                }
            }
    }

    private fun sessionFor(
        code: String,
        uid: String,
        localPlayerId: PlayerId,
        roomRef: DatabaseReference = roomsRef.child(code),
    ): MultiplayerSession =
        MultiplayerSession(
            roomCode = code,
            uid = uid,
            localPlayerId = localPlayerId,
            roomRef = roomRef,
            publicGameRef = publicGamesRef.child(code),
            connectionInfoRef = database.getReference(".info/connected"),
        )
}

class MultiplayerSession internal constructor(
    val roomCode: String,
    val uid: String,
    val localPlayerId: PlayerId,
    private val roomRef: DatabaseReference,
    private val publicGameRef: DatabaseReference,
    private val connectionInfoRef: DatabaseReference,
) {
    private val playerRef: DatabaseReference = roomRef.child("players").child(localPlayerId.playerKey())
    private val connectedRef: DatabaseReference = playerRef.child("connected")
    private val lastSeenRef: DatabaseReference = playerRef.child("lastSeen")
    private var roomListener: ValueEventListener? = null
    private var connectionListener: ValueEventListener? = null
    private var closed = false

    fun listen(
        onRoomChanged: (MultiplayerRoom) -> Unit,
        onError: (String) -> Unit,
    ) {
        closed = false
        stopListening()
        startPresence()

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val room = snapshot.toMultiplayerRoomOrNull(roomCode)
                if (room == null) {
                    onError("This game room is no longer available.")
                } else {
                    onRoomChanged(room)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(MultiplayerLogTag, "Room listener cancelled for $roomCode", error.toException())
                onError(error.message)
            }
        }
        roomListener = listener
        roomRef.addValueEventListener(listener)
    }

    fun submitMove(
        edge: Edge,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (closed) {
            onResult(Result.failure(MultiplayerException("You already left this game.")))
            return
        }

        val rejectionReason = AtomicReference<String?>()

        roomRef.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val room = currentData.toMultiplayerRoomOrNull(roomCode)
                    val state = room?.gameState
                    if (room == null || state == null) {
                        rejectionReason.set("Game state is not ready yet.")
                        return Transaction.abort()
                    }
                    if (room.status != MultiplayerStatus.Active) {
                        rejectionReason.set("This game is not active.")
                        return Transaction.abort()
                    }
                    if (room.playerForUid(uid)?.playerId != state.currentPlayerId) {
                        rejectionReason.set("It is not your turn.")
                        return Transaction.abort()
                    }
                    if (edge in state.lines) {
                        rejectionReason.set("That line is already taken.")
                        return Transaction.abort()
                    }

                    val updatedState = state.placeLine(edge)
                    if (updatedState == state) {
                        rejectionReason.set("That move is not valid.")
                        return Transaction.abort()
                    }

                    currentData.child("state").value = updatedState.toFirebaseStateMap()
                    currentData.child("status").value = if (updatedState.isGameOver) {
                        MultiplayerStatus.Finished.wireName
                    } else {
                        MultiplayerStatus.Active.wireName
                    }
                    currentData.child("updatedAt").value = ServerValue.TIMESTAMP
                    currentData.extendExpiration()
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?,
                ) {
                    when {
                        error != null -> {
                            Log.w(MultiplayerLogTag, "Submit move transaction failed for $roomCode", error.toException())
                            onResult(Result.failure(error.toException()))
                        }
                        committed -> onResult(Result.success(Unit))
                        else -> onResult(
                            Result.failure(
                                MultiplayerException(rejectionReason.get() ?: "Move was rejected."),
                            ),
                        )
                    }
                }
            },
        )
    }

    fun leave(onComplete: () -> Unit = {}) {
        closed = true
        stopListening()
        stopPresence()

        roomRef.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val room = currentData.toMultiplayerRoomOrNull(roomCode) ?: return Transaction.abort()
                    currentData.child("players").child(localPlayerId.playerKey()).child("connected").value = false
                    currentData.child("players").child(localPlayerId.playerKey()).child("lastSeen").value = ServerValue.TIMESTAMP
                    if (room.status == MultiplayerStatus.Waiting) {
                        currentData.child("status").value = MultiplayerStatus.Abandoned.wireName
                    }
                    currentData.child("updatedAt").value = ServerValue.TIMESTAMP
                    currentData.extendExpiration()
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?,
                ) {
                    error?.let {
                        Log.w(MultiplayerLogTag, "Leave transaction failed for $roomCode", it.toException())
                    }
                    if (localPlayerId == PlayerId.Player1) {
                        publicGameRef.removeValue()
                    }
                    onComplete()
                }
            },
        )
    }

    fun close() {
        closed = true
        stopListening()
        stopPresence()
        playerRef.updateChildren(
            mapOf(
                "connected" to false,
                "lastSeen" to ServerValue.TIMESTAMP,
            ),
        )
    }

    fun detach() {
        closed = true
        stopListening()
        stopPresence(cancelDisconnect = false)
    }

    private fun startPresence() {
        stopPresence()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.javaObjectType) == true) {
                    connectedRef.onDisconnect().setValue(false)
                    lastSeenRef.onDisconnect().setValue(ServerValue.TIMESTAMP)
                    playerRef.updateChildren(
                        mapOf(
                            "connected" to true,
                            "lastSeen" to ServerValue.TIMESTAMP,
                        ),
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        connectionListener = listener
        connectionInfoRef.addValueEventListener(listener)
    }

    private fun stopListening() {
        roomListener?.let { roomRef.removeEventListener(it) }
        roomListener = null
    }

    private fun stopPresence(cancelDisconnect: Boolean = true) {
        connectionListener?.let { connectionInfoRef.removeEventListener(it) }
        connectionListener = null
        if (cancelDisconnect) {
            connectedRef.onDisconnect().cancel()
            lastSeenRef.onDisconnect().cancel()
        }
    }
}

fun normalizeJoinCode(input: String): String =
    input
        .filter { it.isLetterOrDigit() }
        .uppercase()
        .take(MultiplayerJoinCodeLength)

private fun generateJoinCode(): String =
    buildString(MultiplayerJoinCodeLength) {
        repeat(MultiplayerJoinCodeLength) {
            append(RoomCodeAlphabet[Random.nextInt(RoomCodeAlphabet.length)])
        }
    }

private fun initialRoomMap(
    code: String,
    uid: String,
    playerInitials: String,
    playerColor: PlayerColor,
    boardSize: BoardSize,
    isPublic: Boolean,
): Map<String, Any?> {
    val initialState = BoxGameState.newGame(
        player1Initials = playerInitials,
        player2Initials = "P2",
        player1Color = playerColor,
        player2Color = PlayerColor.Blue,
        boardSize = boardSize,
    )
    return mapOf(
        "code" to code,
        "status" to MultiplayerStatus.Waiting.wireName,
        "public" to isPublic,
        "hostUid" to uid,
        "guestUid" to null,
        "players" to mapOf(
            PlayerId.Player1.playerKey() to playerMap(
                uid = uid,
                playerId = PlayerId.Player1,
                playerInitials = playerInitials,
                playerColor = playerColor,
            ),
        ),
        "state" to initialState.toFirebaseStateMap(),
        "createdAt" to ServerValue.TIMESTAMP,
        "updatedAt" to ServerValue.TIMESTAMP,
        "expiresAt" to newExpirationTimestamp(),
    )
}

private data class JoinOptions(
    val allowInitialsReconnect: Boolean = true,
    val requireConnectedHost: Boolean = false,
)

private fun publicGameMap(
    code: String,
    uid: String,
): Map<String, Any?> =
    mapOf(
        "code" to code,
        "hostUid" to uid,
        "createdAt" to ServerValue.TIMESTAMP,
        "expiresAt" to newExpirationTimestamp(),
    )

private fun playerMap(
    uid: String,
    playerId: PlayerId,
    playerInitials: String,
    playerColor: PlayerColor,
): Map<String, Any?> =
    mapOf(
        "uid" to uid,
        "playerId" to playerId.name,
        "initials" to playerInitials,
        "color" to playerColor.name,
        "connected" to true,
        "lastSeen" to ServerValue.TIMESTAMP,
    )

private fun MutableData.markPlayerConnected(playerId: PlayerId, uid: String) {
    child("players").child(playerId.playerKey()).apply {
        child("uid").value = uid
        child("connected").value = true
        child("lastSeen").value = ServerValue.TIMESTAMP
    }
    when (playerId) {
        PlayerId.Player1 -> child("hostUid").value = uid
        PlayerId.Player2 -> child("guestUid").value = uid
    }
}

private fun MutableData.restoreStatusForReconnect(room: MultiplayerRoom) {
    if (room.status != MultiplayerStatus.Abandoned) return

    child("status").value = when {
        room.gameState?.isGameOver == true -> MultiplayerStatus.Finished
        room.player2 != null -> MultiplayerStatus.Active
        else -> MultiplayerStatus.Waiting
    }.wireName
}

private fun MutableData.extendExpiration() {
    child("expiresAt").value = newExpirationTimestamp()
}

private fun newExpirationTimestamp(): Long = System.currentTimeMillis() + RoomRetentionMillis

private fun BoxGameState.toFirebaseStateMap(): Map<String, Any?> =
    mapOf(
        "board" to mapOf(
            "columns" to boardSize.columns,
            "rows" to boardSize.rows,
        ),
        "currentPlayer" to currentPlayerId.name,
        "lines" to lines.mapKeys { (edge, _) -> edge.toFirebaseKey() }
            .mapValues { (_, playerId) -> playerId.name },
        "boxes" to boxes.mapKeys { (box, _) -> box.toFirebaseKey() }
            .mapValues { (_, playerId) -> playerId.name },
    )

private fun DataSnapshot.toMultiplayerRoomOrNull(code: String): MultiplayerRoom? =
    decodeMultiplayerRoom(code, value)

private fun MutableData.toMultiplayerRoomOrNull(code: String): MultiplayerRoom? =
    decodeMultiplayerRoom(code, value)

private fun decodeMultiplayerRoom(code: String, rawValue: Any?): MultiplayerRoom? {
    val root = rawValue.asMap() ?: return null
    val status = MultiplayerStatus.fromWireName(root["status"]) ?: return null
    val hostUid = root["hostUid"].asString() ?: return null
    val guestUid = root["guestUid"].asString()
    val players = root["players"].asMap() ?: return null
    val player1 = players[PlayerId.Player1.playerKey()].asMap()
        ?.toMultiplayerPlayer(PlayerId.Player1)
        ?: return null
    val player2 = players[PlayerId.Player2.playerKey()].asMap()
        ?.toMultiplayerPlayer(PlayerId.Player2)
    val state = if (player2 == null) {
        null
    } else {
        root["state"].asMap()?.toBoxGameState(player1, player2)
    }

    return MultiplayerRoom(
        code = code,
        status = status,
        hostUid = hostUid,
        guestUid = guestUid,
        player1 = player1,
        player2 = player2,
        gameState = state,
        isPublic = root["public"] as? Boolean ?: false,
    )
}

private fun DataSnapshot.toPublicGameCodeOrNull(now: Long): String? {
    val root = value.asMap() ?: return null
    val expiresAt = root["expiresAt"].asLongOrNull() ?: return null
    if (expiresAt < now) return null

    val code = normalizeJoinCode(root["code"].asString() ?: key.orEmpty())
    return code.takeIf { it.length == MultiplayerJoinCodeLength }
}

private fun Map<*, *>.toMultiplayerPlayer(playerId: PlayerId): MultiplayerPlayer? {
    val uid = this["uid"].asString() ?: return null
    return MultiplayerPlayer(
        uid = uid,
        playerId = playerId,
        initials = normalizeInitials(this["initials"].asString().orEmpty()).ifBlank { playerId.defaultInitials() },
        color = PlayerColor.entries.firstOrNull { it.name == this["color"].asString() } ?: playerId.defaultColor(),
        connected = this["connected"] as? Boolean ?: false,
    )
}

private fun Map<*, *>.toBoxGameState(
    player1: MultiplayerPlayer,
    player2: MultiplayerPlayer,
): BoxGameState? {
    val board = this["board"].asMap() ?: return null
    val columns = board["columns"].asIntOrNull() ?: return null
    val rows = board["rows"].asIntOrNull() ?: return null
    val boardSize = runCatching { BoardSize(columns, rows) }.getOrNull() ?: return null
    val currentPlayer = PlayerId.entries.firstOrNull { it.name == this["currentPlayer"].asString() } ?: PlayerId.Player1
    val lines = this["lines"].asMap()
        ?.mapNotNull { (key, value) ->
            val edge = key.asString()?.toEdgeOrNull()
            val owner = PlayerId.entries.firstOrNull { it.name == value.asString() }
            if (edge != null && owner != null) edge to owner else null
        }
        ?.toMap()
        .orEmpty()
    val boxes = this["boxes"].asMap()
        ?.mapNotNull { (key, value) ->
            val box = key.asString()?.toBoxCellOrNull()
            val owner = PlayerId.entries.firstOrNull { it.name == value.asString() }
            if (box != null && owner != null) box to owner else null
        }
        ?.toMap()
        .orEmpty()

    return BoxGameState(
        player1 = Player(
            id = PlayerId.Player1,
            initials = player1.initials,
            color = player1.color,
        ),
        player2 = Player(
            id = PlayerId.Player2,
            initials = player2.initials,
            color = player2.color,
        ),
        boardSize = boardSize,
        currentPlayerId = currentPlayer,
        lines = lines,
        boxes = boxes,
    )
}

private fun PlayerId.playerKey(): String = when (this) {
    PlayerId.Player1 -> "player1"
    PlayerId.Player2 -> "player2"
}

private fun PlayerId.defaultInitials(): String = when (this) {
    PlayerId.Player1 -> "P1"
    PlayerId.Player2 -> "P2"
}

private fun PlayerId.defaultColor(): PlayerColor = when (this) {
    PlayerId.Player1 -> PlayerColor.Red
    PlayerId.Player2 -> PlayerColor.Blue
}

private fun Edge.toFirebaseKey(): String {
    val prefix = when (orientation) {
        EdgeOrientation.Horizontal -> "h"
        EdgeOrientation.Vertical -> "v"
    }
    return "${prefix}_${row}_${column}"
}

private fun BoxCell.toFirebaseKey(): String = "${row}_${column}"

private fun String.toEdgeOrNull(): Edge? {
    val parts = split("_")
    if (parts.size != 3) return null
    val orientation = when (parts[0]) {
        "h" -> EdgeOrientation.Horizontal
        "v" -> EdgeOrientation.Vertical
        else -> return null
    }
    val row = parts[1].toIntOrNull() ?: return null
    val column = parts[2].toIntOrNull() ?: return null
    return Edge(orientation, row, column)
}

private fun String.toBoxCellOrNull(): BoxCell? {
    val parts = split("_")
    if (parts.size != 2) return null
    val row = parts[0].toIntOrNull() ?: return null
    val column = parts[1].toIntOrNull() ?: return null
    return BoxCell(row, column)
}

private fun Any?.asMap(): Map<*, *>? = this as? Map<*, *>

private fun Any?.asString(): String? = this as? String

private fun Any?.asIntOrNull(): Int? = when (this) {
    is Int -> this
    is Long -> toInt()
    is Double -> toInt()
    is String -> toIntOrNull()
    else -> null
}

private fun Any?.asLongOrNull(): Long? = when (this) {
    is Int -> toLong()
    is Long -> this
    is Double -> toLong()
    is String -> toLongOrNull()
    else -> null
}
