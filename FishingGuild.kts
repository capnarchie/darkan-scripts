@ScriptDescription(
    author = "Capnarchie",
    name = "Fishing Guild",
    version = "1.3",
    description = "Fishes Sharks, Lobsters, Swordfish/Tuna, or Bass/Cod in the Fishing Guild with automated banking",
    category = ScriptCategory.FISHING
)
class FishingGuild : BotScript(), ConfigurableScript {

    val targetFish = StringConfigItem(
        name = "Fish",
        description = "Type target fish: shark, lobster, swordfish (or tuna), bass (or cod)",
        initialValue = "shark"
    )

    val dockPref = StringConfigItem(
        name = "Dock",
        description = "Type dock: auto, north, or south",
        initialValue = "auto"
    )

    val status = InfoDisplayConfigItem(
        name = "Status",
        description = "Current script activity",
        initialValue = "Initializing"
    )

    val fishCaughtDisplay = InfoDisplayConfigItem(
        name = "Fish caught",
        description = "Total fish caught this session",
        initialValue = "0"
    )

    val banksCompletedDisplay = InfoDisplayConfigItem(
        name = "Banks completed",
        description = "Total banking runs completed",
        initialValue = "0"
    )

    val xpDisplay = InfoDisplayConfigItem(
        name = "XP Gained (XP/hr)",
        description = "Total Fishing XP gained and rate per hour",
        initialValue = "0 (0 XP/hr)"
    )

    private val bankTile = Tile(2586, 3422, 0)
    private var fishCaught = 0
    private var banksCompleted = 0
    private var startFishingXp = 0
    private var startTime = 0L

    override fun onStart() {
        startFishingXp = getXp(Skill.FISHING.ordinal)
        startTime = System.currentTimeMillis()
        setRunning(true)
        status.value = "Starting"
    }

    override fun onEvent(event: Event) {
        if (event is XPDrop && event.skill == Skill.FISHING) {
            fishCaught++
            updateStats()
        }
    }

    private fun updateStats() {
        if (startTime <= 0L) return
        val currentXp = getXp(Skill.FISHING.ordinal)
        val gained = (currentXp - startFishingXp).coerceAtLeast(0)
        val xpPerHour = getFormattedXpPerHour(Skill.FISHING.ordinal, startFishingXp, startTime)
        xpDisplay.value = "$gained ($xpPerHour)"
        fishCaughtDisplay.value = "$fishCaught"
        banksCompletedDisplay.value = "$banksCompleted"
    }

    private fun ensureRunning() {
        if (!isWalking() && !isAnimating()) {
            setRunning(true)
        }
    }

    private fun getTargetNpcId(): Int {
        val input = targetFish.value.trim().lowercase()
        return when {
            input.contains("lobster") -> 312
            input.contains("sword") || input.contains("tuna") -> 312
            input.contains("bass") || input.contains("cod") || input.contains("net") -> 313
            else -> 313 // default: shark
        }
    }

    private fun getTargetAction(): String {
        val input = targetFish.value.trim().lowercase()
        return when {
            input.contains("lobster") -> "cage"
            input.contains("sword") || input.contains("tuna") -> "harpoon"
            input.contains("bass") || input.contains("cod") || input.contains("net") -> "net"
            else -> "harpoon" // default: shark
        }
    }

    private fun getTargetLabel(): String {
        val input = targetFish.value.trim().lowercase()
        return when {
            input.contains("lobster") -> "Lobster"
            input.contains("sword") || input.contains("tuna") -> "Swordfish & Tuna"
            input.contains("bass") || input.contains("cod") || input.contains("net") -> "Bass & Cod"
            else -> "Shark"
        }
    }

    private fun getTargetDockTile(): Tile {
        val pref = dockPref.value.trim().lowercase()
        if (pref.contains("north")) return Tile(2603, 3424, 0)
        if (pref.contains("south")) return Tile(2603, 3415, 0)

        // For auto, check which dock has the matching spot active
        val spot = getFilteredNPCs(getTargetNpcId()).firstOrNull()
        if (spot != null) {
            val y = spot.endZ.toInt() + getBaseY()
            if (y >= 3420) return Tile(2603, 3424, 0)
            if (y < 3420) return Tile(2603, 3415, 0)
        }

        return Tile(2598, 3421, 0)
    }

    override suspend fun loop() {
        ensureRunning()
        updateStats()

        // 1. Banking Phase: Inventory full
        if (inventory.freeSlots() <= 0) {
            handleBanking()
            return
        }

        val label = getTargetLabel()

        // 2. Fishing Phase: While animating, let the player fish
        if (isAnimating()) {
            status.value = "Fishing $label"
            delay(600)
            return
        }

        // 3. If player is in or near the bank, walk back to the dock first
        val pos = getMyPlayerPosition()
        if (Utils.distance(pos, bankTile) <= 6) {
            val dockTile = getTargetDockTile()
            status.value = "Returning to dock"
            walkTo(dockTile)
            delayUntil(8000) { Utils.distance(getMyPlayerPosition(), dockTile) <= 4 || isAnimating() }
            return
        }

        // 4. Find and click the best matching fishing spot
        status.value = "Finding $label spot"
        val spot = findTargetSpot()
        if (spot == null) {
            // If on the wrong dock or spot moved, walk to the dock area
            val dockTile = getTargetDockTile()
            if (Utils.distance(pos, dockTile) > 5) {
                status.value = "Walking to dock"
                walkTo(dockTile)
                delayUntil(6000) { Utils.distance(getMyPlayerPosition(), dockTile) <= 4 }
                return
            }

            status.value = "Waiting for $label spot to spawn"
            delay(1000)
            return
        }

        status.value = "Interacting with $label spot"
        clickNPC(spot, getTargetAction())
        delayUntil(3000) { isAnimating() || isWalking() }
        delayUntil(8000) { isAnimating() || (!isWalking() && inventory.freeSlots() <= 0) }
    }

    private suspend fun handleBanking() {
        status.value = "Heading to bank"
        val pos = getMyPlayerPosition()

        if (Utils.distance(pos, bankTile) > 5) {
            walkTo(bankTile)
            delayUntil(8000) { Utils.distance(getMyPlayerPosition(), bankTile) <= 5 || !isWalking() }
        }

        if (!bankIsOpen() && !interfaceOpen(762)) {
            status.value = "Opening bank"
            val bankBooth = getNearbyObjects().find {
                it.getDefinitions().name.contains("bank", ignoreCase = true) && it.containsOption("bank") != -1
            }

            if (bankBooth != null) {
                clickObject(bankBooth, "bank")
            } else {
                openClosestBank()
            }

            delayUntil(5000) { bankIsOpen() || interfaceOpen(762) }
        }

        if (bankIsOpen() || interfaceOpen(762)) {
            status.value = "Depositing fish"
            bankAll()
            delayUntil(3000) { inventory.freeSlots() >= 28 }
            if (inventory.freeSlots() >= 28) {
                banksCompleted++
                updateStats()
            }
            delay(300)
            closeInterfaces()
            delayUntil(2000) { !bankIsOpen() && !interfaceOpen(762) }

            // Immediately walk back towards the fishing dock
            val dockTile = getTargetDockTile()
            status.value = "Returning to dock"
            walkTo(dockTile)
            delayUntil(8000) { Utils.distance(getMyPlayerPosition(), dockTile) <= 4 || isAnimating() }
        }
    }

    private fun findTargetSpot(): com.jagex.game.runetek5.entity.pathingentity.npc.NpcEntity? {
        val npcId = getTargetNpcId()
        val pref = dockPref.value.trim().lowercase()
        val playerPos = getMyPlayerPosition()

        val matchingSpots = getFilteredNPCs(npcId).filter { npc ->
            val y = npc.endZ.toInt() + getBaseY()
            when {
                pref.contains("north") -> y >= 3420
                pref.contains("south") -> y < 3420
                else -> true // auto
            }
        }

        return matchingSpots.minByOrNull { npc ->
            val tile = Tile(npc.endX.toInt() + getBaseX(), npc.endZ.toInt() + getBaseY())
            Utils.distance(playerPos, tile)
        }
    }
}

FishingGuild()
