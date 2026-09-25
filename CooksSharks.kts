data class FishInfo(
    val displayName: String,
    val rawId: Int,
    val rawName: String
)

@ScriptDescription(
    author = "Erx",
    name = "Cooks' Guild Cooker",
    version = "2.2",
    description = "Cooks raw fish at the Cooks' Guild Range with automated banking",
    category = ScriptCategory.COOKING
)
class CooksSharks : BotScript(), ConfigurableScript {

    val targetFish = StringConfigItem(
        name = "Fish to cook",
        description = "Type fish: shark, rocktail, monkfish, swordfish, lobster, bass, tuna, salmon, trout",
        initialValue = "shark"
    )

    val status = InfoDisplayConfigItem(
        name = "Status",
        description = "Current script activity",
        initialValue = "Initializing"
    )

    val cookedCountDisplay = InfoDisplayConfigItem(
        name = "Fish cooked",
        description = "Total fish cooked this session",
        initialValue = "0"
    )

    val xpDisplay = InfoDisplayConfigItem(
        name = "XP Gained (XP/hr)",
        description = "Cooking XP gained and hourly rate",
        initialValue = "0 (0 XP/hr)"
    )

    private val cooksGuildRangeTile = Tile(3145, 3453, 0)
    private val standAtRangeTile = Tile(3146, 3452, 0)
    private val bankBoothTile = Tile(3147, 3450, 0)

    private var startCookingXp = 0
    private var startTime = 0L
    private var fishCooked = 0

    private fun getSelectedFish(): FishInfo {
        val input = targetFish.value.trim().lowercase()
        return when {
            input.contains("rock") -> FishInfo("Rocktail", 15270, "raw rocktail")
            input.contains("manta") -> FishInfo("Manta ray", 389, "raw manta ray")
            input.contains("turtle") -> FishInfo("Sea turtle", 395, "raw sea turtle")
            input.contains("cave") || input.contains("eel") -> FishInfo("Cave eel", 5001, "raw cave eel")
            input.contains("monk") -> FishInfo("Monkfish", 7944, "raw monkfish")
            input.contains("sword") -> FishInfo("Swordfish", 371, "raw swordfish")
            input.contains("lob") -> FishInfo("Lobster", 377, "raw lobster")
            input.contains("bass") -> FishInfo("Bass", 363, "raw bass")
            input.contains("tuna") -> FishInfo("Tuna", 359, "raw tuna")
            input.contains("salm") -> FishInfo("Salmon", 329, "raw salmon")
            input.contains("trout") -> FishInfo("Trout", 335, "raw trout")
            input.contains("pike") -> FishInfo("Pike", 349, "raw pike")
            input.contains("herr") -> FishInfo("Herring", 345, "raw herring")
            input.contains("mack") -> FishInfo("Mackerel", 353, "raw mackerel")
            input.contains("sard") -> FishInfo("Sardine", 327, "raw sardine")
            input.contains("anch") -> FishInfo("Anchovies", 321, "raw anchovies")
            input.contains("shrimp") -> FishInfo("Shrimp", 317, "raw shrimp")
            input.contains("cray") -> FishInfo("Crayfish", 13435, "raw crayfish")
            else -> FishInfo("Shark", 383, "raw shark")
        }
    }

    override fun onStart() {
        startCookingXp = getXp(Skill.COOKING.ordinal)
        startTime = System.currentTimeMillis()
        fishCooked = 0
        setRunning(true)
        status.value = "Starting"
        val fish = getSelectedFish()
        println("Cooks' Guild Cooker started. Target: ${fish.displayName}")
    }

    override fun onEvent(event: Event) {
        if (event is XPDrop && event.skill == Skill.COOKING) {
            fishCooked++
            cookedCountDisplay.value = "$fishCooked"
            updateStats()
        }
    }

    private fun updateStats() {
        if (startTime <= 0L) return
        val currentXp = getXp(Skill.COOKING.ordinal)
        val gained = (currentXp - startCookingXp).coerceAtLeast(0)
        val xpPerHour = getFormattedXpPerHour(Skill.COOKING.ordinal, startCookingXp, startTime)
        xpDisplay.value = "$gained ($xpPerHour)"
    }

    override suspend fun loop() {
        try {
            updateStats()
            val fish = getSelectedFish()
            if (hasRawFish(fish)) {
                cookFish(fish)
            } else {
                handleBanking(fish)
            }
        } catch (e: Exception) {
            println("Error in CooksSharks: ${e.message}")
            delay(1000)
        }
    }

    private fun hasRawFish(fish: FishInfo): Boolean {
        return inventory.contains(fish.rawId, 1) || inventory.contains(fish.rawName, 1)
    }

    private fun getCookingRange(): WorldObject? {
        val pos = getMyPlayerPosition()
        return getNearbyObjects().filter { obj ->
            obj.z == pos.z && (
                obj.id == 24283 ||
                obj.getDefinitions().name.equals("Range", ignoreCase = true) ||
                obj.getDefinitions().name.equals("Cooking range", ignoreCase = true)
            ) && !obj.getDefinitions().name.contains("chimney", ignoreCase = true)
        }.minByOrNull { Utils.distance(pos, it) }
    }

    private suspend fun cookFish(fish: FishInfo) {
        // 1. If actively cooking / animating, wait
        if (isAnimating()) {
            status.value = "Cooking ${fish.displayName}"
            delay(600)
            return
        }

        // 2. If Make-X dialogue is open, start cooking
        if (interfaceOpen(905)) {
            status.value = "Starting cooking batch"
            println("Make-X open, selecting Cook ${fish.displayName}")
            clickDialogue(905, 14)
            clickSkillDialogue(1)
            delayUntil(5000) { isAnimating() }
            return
        }

        // 3. Make sure we're near the range
        val pos = getMyPlayerPosition()
        if (Utils.distance(pos, standAtRangeTile) > 3) {
            status.value = "Walking to range"
            println("Walking to range...")
            walkTo(standAtRangeTile)
            delayUntil(6000) { Utils.distance(getMyPlayerPosition(), standAtRangeTile) <= 2 || !isWalking() }
        }

        // 4. Use raw fish on Range
        status.value = "Using fish on range"
        val range = getCookingRange()
        val targetTile = range?.let { Tile(it.x, it.y, it.z) } ?: cooksGuildRangeTile
        val targetId = range?.id ?: 24283

        val rawSlot = inventory.getSlotByItem(fish.rawId)
        if (rawSlot != -1) {
            println("Using ${fish.rawName} on Range (id=$targetId, tile=$targetTile)...")
            sendItemOnObject(fish.rawId, targetId, targetTile.x, targetTile.y)
            delayUntil(5000) { interfaceOpen(905) || isAnimating() }
        }

        // 5. Select Cook if dialogue opened
        if (interfaceOpen(905)) {
            delay(300)
            status.value = "Selecting Cook in Make-X"
            println("Make-X opened, selecting Cook ${fish.displayName}")
            clickDialogue(905, 14)
            clickSkillDialogue(1)
            delayUntil(5000) { isAnimating() }
        }
    }

    private suspend fun handleBanking(fish: FishInfo) {
        // 1. Wait for any residual cooking animation to finish before interacting with bank
        if (isAnimating()) {
            delayUntil(3000) { !isAnimating() }
        }

        // 2. If bank is not open, walk to booth and open it
        if (!bankIsOpen() && !interfaceOpen(762)) {
            val pos = getMyPlayerPosition()
            if (Utils.distance(pos, bankBoothTile) > 2) {
                status.value = "Walking to bank"
                println("Walking to bank booth...")
                walkTo(bankBoothTile)
                delayUntil(6000) { Utils.distance(getMyPlayerPosition(), bankBoothTile) <= 2 || !isWalking() }
            }

            status.value = "Opening bank"
            println("Opening bank booth...")
            val booth = getNearbyObjects().find { it.id == 19230 }
            if (booth != null) {
                val op = booth.containsOption("bank")
                clickObject(booth, if (op != -1) op else 2)
            } else {
                clickClosestObject(19230, 2)
            }
            delayUntil(5000) { bankIsOpen() || interfaceOpen(762) }

            // Fallback if not opened yet
            if (!bankIsOpen() && !interfaceOpen(762)) {
                openClosestBank()
                delayUntil(5000) { bankIsOpen() || interfaceOpen(762) }
            }

            if (!bankIsOpen() && !interfaceOpen(762)) {
                println("Failed to open bank, retrying...")
                delay(1000)
                return
            }
        }

        // 3. Deposit everything if inventory has items
        if (inventory.freeSlots() < 28) {
            status.value = "Depositing items"
            println("Depositing items...")
            bankAll()
            delayUntil(3000) { inventory.freeSlots() >= 28 }
            delay(300)
        }

        // 4. Wait for bank contents to populate
        delayUntil(3000) { bank.container.itemIds.any { it != -1 } }

        // Check if bank has the fish
        status.value = "Withdrawing ${fish.displayName}"
        println("Withdrawing ${fish.rawName}...")

        // Wait up to 5s for the fish to be visible in the bank slots
        delayUntil(5000) { bank.getSlotByItem(fish.rawId) != -1 }

        withdrawAllOfItem(fish.rawId)
        delayUntil(4000) { hasRawFish(fish) }

        // Fallback by name if withdraw by ID didn't pull items
        if (!hasRawFish(fish)) {
            withdrawAllOfItem(fish.rawName)
            delayUntil(3000) { hasRawFish(fish) }
        }

        // Check if out of fish
        if (!hasRawFish(fish)) {
            println("Out of ${fish.rawName} in bank! Stopping script.")
            status.value = "Out of fish"
            closeInterfaces()
            stop()
            return
        }

        delay(300)
        closeInterfaces()
        delayUntil(2000) { !bankIsOpen() && !interfaceOpen(762) }

        // 5. Walk back to range
        status.value = "Walking to range"
        println("Heading to range...")
        walkTo(standAtRangeTile)
        delayUntil(6000) { Utils.distance(getMyPlayerPosition(), standAtRangeTile) <= 2 || !isWalking() }
    }
}

CooksSharks()
