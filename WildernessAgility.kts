@ScriptDescription(
    author = "Capnarchie",
    name = "Wilderness Agility",
    version = "1.2",
    description = "Trains Agility at the Wilderness Agility Course with rapid transitions, failure recovery and stage tracking",
    category = ScriptCategory.AGILITY
)
class WildernessAgility : StateMachineScript<WildernessAgility>(), ConfigurableScript {

    val status = InfoDisplayConfigItem(
        name = "Status",
        description = "Current script state and activity",
        initialValue = "Initializing"
    )

    val lapsDisplay = InfoDisplayConfigItem(
        name = "Laps completed",
        description = "Total successful full course laps completed",
        initialValue = "0"
    )

    val failuresDisplay = InfoDisplayConfigItem(
        name = "Failures recovered",
        description = "Number of course failures recovered from",
        initialValue = "0"
    )

    val xpDisplay = InfoDisplayConfigItem(
        name = "XP Gained (XP/hr)",
        description = "Total Agility experience gained and rate per hour",
        initialValue = "0 (0 XP/hr)"
    )

    var pipeDone = false
    var ropeDone = false
    var stonesDone = false
    var logDone = false

    var lapsCompleted = 0
    var failuresRecovered = 0
    var startAgilityXp = 0
    var startTime = 0L

    override fun getStartState(): State<WildernessAgility> = DetermineState

    override fun onStart() {
        startAgilityXp = getXp(Skill.AGILITY.ordinal)
        startTime = System.currentTimeMillis()
        setRunning(true)
        status.value = "Starting"
    }

    fun updateStats() {
        if (startTime <= 0L) return
        val currentXp = getXp(Skill.AGILITY.ordinal)
        val gained = (currentXp - startAgilityXp).coerceAtLeast(0)
        val xpPerHour = getFormattedXpPerHour(Skill.AGILITY.ordinal, startAgilityXp, startTime)
        xpDisplay.value = "$gained ($xpPerHour)"
        lapsDisplay.value = "$lapsCompleted"
        failuresDisplay.value = "$failuresRecovered"
    }

    fun ensureRunning() {
        if (!isWalking() && !isAnimating()) {
            setRunning(true)
        }
    }

    fun findObject(id: Int): WorldObject? {
        val playerPos = getMyPlayerPosition()
        return getObjectsNearby(id).minByOrNull { Utils.distance(playerPos, it) }
    }
}

/**
 * Evaluates the player's current location and determines which course state to transition to.
 */
object DetermineState : State<WildernessAgility>() {
    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        ensureRunning()
        updateStats()

        val pos = getMyPlayerPosition()

        // If underground, climb ladder
        if (pos.y > 9000) {
            return ClimbingLadder
        }

        // Stepping stone fail check: landed at lava bank (3000, 3964)
        if (pos.x in 2998..3001 && pos.y in 3963..3966) {
            return CrossingSteppingStones
        }

        // South entrance area
        if (pos.y < 3939) {
            return EnteringPipe
        }

        // Between pipe and ropeswing
        if (pos.y in 3948..3954 && pos.x in 3002..3007) {
            return CrossingRopeswing
        }

        // If ropeswing was not completed yet, retry it
        if (!ropeDone) {
            return CrossingRopeswing
        }

        // North of ropeswing / near stepping stones entrance
        if (pos.y in 3955..3965 && pos.x >= 3000) {
            return CrossingSteppingStones
        }

        // West side after stepping stones / approaching log balance
        if (pos.x <= 2999 && pos.y in 3946..3965) {
            return CrossingLogBalance
        }

        // West side near cliffside
        if (pos.x <= 2998 && pos.y in 3937..3945) {
            return ClimbingCliffside
        }

        // Default fallback to pipe
        return EnteringPipe
    }

    override suspend fun WildernessAgility.stateLoop() {
        delay(100)
    }
}

/**
 * Handles climbing the underground ladder back to the surface.
 */
object ClimbingLadder : State<WildernessAgility>() {
    private val LADDER_TILE = Tile(3005, 10363, 0)
    private const val LADDER_OBJ_ID = 32015

    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        if (getMyPlayerPosition().y < 9000) {
            // Ropeswing failed: retry ropeswing
            if (!ropeDone) {
                return CrossingRopeswing
            }

            // Log balance failed: retry log balance
            if (pipeDone && ropeDone && stonesDone && !logDone) {
                return CrossingLogBalance
            }

            return CrossingSteppingStones
        }
        return null
    }

    override suspend fun WildernessAgility.stateLoop() {
        status.value = "Climbing underground ladder"
        ensureRunning()

        val ladder = findObject(LADDER_OBJ_ID)
        if (ladder != null) {
            clickObject(ladder, 1)
        } else {
            walkTo(LADDER_TILE)
        }

        // Wait until surfaced (Y < 9000)
        delayUntil(6000) { getMyPlayerPosition().y < 9000 }
        if (getMyPlayerPosition().y < 9000) {
            failuresRecovered++
            updateStats()
        }
    }
}

/**
 * Handles entering and crawling through the obstacle pipe (Obstacle 1).
 */
object EnteringPipe : State<WildernessAgility>() {
    private val PIPE_ENTRY = Tile(3004, 3937, 0)
    private const val PIPE_OBJ_ID = 65362

    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        if (getMyPlayerPosition().y > 9000) return ClimbingLadder

        // After squeezing through, player reaches Y >= 3949 and finishes unlocking
        if (getMyPlayerPosition().y >= 3949 && !isAnimating()) {
            return CrossingRopeswing
        }
        return null
    }

    override suspend fun WildernessAgility.stateLoop() {
        status.value = "Squeezing through pipe"
        ensureRunning()
        updateStats()

        val pos = getMyPlayerPosition()

        val pipe = findObject(PIPE_OBJ_ID)
        if (pipe != null) {
            clickObject(pipe, 1)
            // Reset course flags at the start of a fresh lap
            pipeDone = true
            ropeDone = false
            stonesDone = false
            logDone = false

            // Wait until arrived at north side AND animation finished to ensure player is unlocked
            delayUntil(9000) { getMyPlayerPosition().y >= 3949 && !isAnimating() }
        } else {
            if (Utils.distance(pos, PIPE_ENTRY) > 3) {
                walkTo(PIPE_ENTRY)
                delayUntil(4000) { Utils.distance(getMyPlayerPosition(), PIPE_ENTRY) <= 3 }
            } else {
                delay(200)
            }
        }
    }
}

/**
 * Handles swinging across the ropeswing (Obstacle 2).
 */
object CrossingRopeswing : State<WildernessAgility>() {
    private val ROPE_START = Tile(3005, 3953, 0)
    private const val ROPE_OBJ_ID = 64696

    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        val pos = getMyPlayerPosition()
        if (pos.y > 9000) {
            ropeDone = false
            return ClimbingLadder
        }

        // Successfully crossed (landed north of the pit, Y in 3956..3965) and player unlocked
        if (pos.y in 3956..3965 && !isAnimating() && ropeDone) {
            return CrossingSteppingStones
        }

        return null
    }

    override suspend fun WildernessAgility.stateLoop() {
        status.value = "Swinging on rope"
        ensureRunning()
        updateStats()

        val pos = getMyPlayerPosition()

        // If north of the ropeswing (e.g. after ladder recovery), walk back to the south start tile
        if (pos.y > 3953) {
            walkTo(ROPE_START)
            delayUntil(6000) { getMyPlayerPosition().y <= 3953 && getMyPlayerPosition().y < 9000 }
        }

        if (getMyPlayerPosition().y <= 3953) {
            val rope = findObject(ROPE_OBJ_ID)
            if (rope != null) {
                clickObject(rope, 1)

                // Wait for crossing movement / landing or falling underground
                delayUntil(4000) {
                    val current = getMyPlayerPosition()
                    current.y in 3956..3965 || current.y > 9000
                }

                if (getMyPlayerPosition().y in 3956..3965) {
                    ropeDone = true
                    // Wait for landing animation to clear
                    delayUntil(2000) { !isAnimating() }
                }
            } else {
                delay(200)
            }
        }
    }
}

/**
 * Handles traversing the stepping stones across lava (Obstacle 3).
 */
object CrossingSteppingStones : State<WildernessAgility>() {
    private val STONES_START = Tile(3002, 3960, 0)
    private const val STONES_OBJ_ID = 64699

    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        val pos = getMyPlayerPosition()
        if (pos.y > 9000) return ClimbingLadder

        // Successfully reached the west bank (X <= 2996)
        if (pos.x <= 2996 && pos.y in 3958..3963) {
            stonesDone = true
            return CrossingLogBalance
        }

        return null
    }

    override suspend fun WildernessAgility.stateLoop() {
        status.value = "Crossing stepping stones"
        ensureRunning()
        updateStats()

        val pos = getMyPlayerPosition()

        // Check if we failed into lava (teleported to 3000, 3964)
        if (pos.x in 2998..3001 && pos.y in 3963..3966) {
            failuresRecovered++
            updateStats()
            stonesDone = false
        }

        // The server strictly checks: player.tile == Tile(3002, 3960, 0)
        if (pos.x != 3002 || pos.y != 3960) {
            walkTo(STONES_START)
            delayUntil(4000) {
                val p = getMyPlayerPosition()
                p.x == 3002 && p.y == 3960
            }
        }

        val current = getMyPlayerPosition()
        if (current.x == 3002 && current.y == 3960) {
            val stones = findObject(STONES_OBJ_ID)
            if (stones != null) {
                clickObject(stones, 1)

                // Wait until we reach the other side or fall into lava
                delayUntil(7000) {
                    val p = getMyPlayerPosition()
                    p.x <= 2996 || (p.x in 2998..3001 && p.y in 3963..3966) || p.y > 9000
                }
            } else {
                delay(200)
            }
        }
    }
}

/**
 * Handles walking across the log balance (Obstacle 4).
 */
object CrossingLogBalance : State<WildernessAgility>() {
    private val LOG_START = Tile(3002, 3945, 0)
    private const val LOG_OBJ_ID = 64698

    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        val pos = getMyPlayerPosition()
        if (pos.y > 9000) {
            logDone = false
            return ClimbingLadder
        }

        // Successfully reached west side (X <= 2995)
        if (pos.x <= 2995 && pos.y in 3942..3948) {
            logDone = true
            return ClimbingCliffside
        }

        return null
    }

    override suspend fun WildernessAgility.stateLoop() {
        status.value = "Crossing log balance"
        ensureRunning()
        updateStats()

        val pos = getMyPlayerPosition()

        // Walk to log start if not already there
        if (Utils.distance(pos, LOG_START) > 1) {
            walkTo(LOG_START)
            delayUntil(4000) { Utils.distance(getMyPlayerPosition(), LOG_START) <= 1 }
        }

        val log = findObject(LOG_OBJ_ID)
        if (log != null) {
            clickObject(log, 1)

            delayUntil(8000) {
                val p = getMyPlayerPosition()
                (p.x <= 2995 && p.y in 3942..3948) || p.y > 9000
            }
        } else {
            delay(200)
        }
    }
}

/**
 * Handles climbing up the cliffside to finish the course loop (Obstacle 5).
 */
object ClimbingCliffside : State<WildernessAgility>() {
    private val CLIFF_BASE = Tile(2993, 3939, 0)
    private const val CLIFF_OBJ_ID = 65734
    private const val PIPE_OBJ_ID = 65362

    override suspend fun WildernessAgility.checkNext(): State<WildernessAgility>? {
        val pos = getMyPlayerPosition()
        if (pos.y > 9000) return ClimbingLadder

        // After climbing to the top (Y <= 3936), head towards the pipe
        if (pos.y <= 3936) {
            return EnteringPipe
        }

        return null
    }

    override suspend fun WildernessAgility.stateLoop() {
        status.value = "Climbing cliffside"
        ensureRunning()
        updateStats()

        val pos = getMyPlayerPosition()

        // Server requires Y == 3939 to climb cliffside
        if (pos.y != 3939 || pos.x > 2994) {
            walkTo(CLIFF_BASE)
            delayUntil(4000) {
                val p = getMyPlayerPosition()
                p.y == 3939 && p.x <= 2994
            }
        }

        val cliff = findObject(CLIFF_OBJ_ID)
        if (cliff != null) {
            clickObject(cliff, 1)

            delayUntil(5000) { getMyPlayerPosition().y <= 3936 }

            if (getMyPlayerPosition().y <= 3936) {
                // If all 4 preceding obstacles were completed, this counts as a full lap!
                if (pipeDone && ropeDone && stonesDone && logDone) {
                    lapsCompleted++
                }

                // Reset flags
                pipeDone = false
                ropeDone = false
                stonesDone = false
                logDone = false

                updateStats()

                // Immediately click the pipe to start the next lap seamlessly
                val pipe = findObject(PIPE_OBJ_ID)
                if (pipe != null) {
                    clickObject(pipe, 1)
                }
            }
        } else {
            delay(200)
        }
    }
}

WildernessAgility()
