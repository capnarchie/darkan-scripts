@ScriptDescription(
    author = "Erx",
    name = "Cooks' Guild Sharks",
    version = "1.2",
    description = "Cooks raw sharks at the Cooks' Guild Range with automatic banking",
    category = ScriptCategory.COOKING
)
class CooksSharks : BotScript() {

    private val cooksGuildRangeTile = Tile(3145, 3453, 0)
    private val standAtRangeTile = Tile(3146, 3452, 0)
    private val bankBoothTile = Tile(3147, 3450, 0)

    override suspend fun loop() {
        try {
            if (hasRawSharks()) {
                cookSharks()
            } else {
                handleBanking()
            }
        } catch (e: Exception) {
            println("Error in CooksSharks: ${e.message}")
            delay(1000)
        }
    }

    private fun hasRawSharks(): Boolean {
        return inventory.contains(383, 1) || inventory.contains("raw shark", 1)
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

    private suspend fun cookSharks() {
        // 1. If actively cooking / animating, just wait
        if (isAnimating()) {
            delay(600)
            return
        }

        // 2. If Make-X dialogue is open, start cooking
        if (interfaceOpen(905)) {
            println("Make-X open, selecting Cook Sharks")
            clickDialogue(905, 14)
            clickSkillDialogue(1)
            delayUntil(5000) { isAnimating() }
            return
        }

        // 3. Make sure we're near the range
        val pos = getMyPlayerPosition()
        if (Utils.distance(pos, standAtRangeTile) > 3) {
            println("Walking to range...")
            walkTo(standAtRangeTile)
            delayUntil(6000) { Utils.distance(getMyPlayerPosition(), standAtRangeTile) <= 2 || !isWalking() }
        }

        // 4. Use raw shark on Range
        val range = getCookingRange()
        val targetTile = range?.let { Tile(it.x, it.y, it.z) } ?: cooksGuildRangeTile
        val targetId = range?.id ?: 24283

        val rawSlot = inventory.getSlotByItem(383)
        if (rawSlot != -1) {
            println("Using raw shark on Range (id=$targetId, tile=$targetTile)...")
            if (range != null) {
                sendItemOnObject("raw shark", range)
            } else {
                val rawItemId = inventory.getItem(rawSlot)
                sendItemOnObject(rawItemId, targetId, targetTile.x, targetTile.y)
            }
            delayUntil(5000) { interfaceOpen(905) || isAnimating() }
        }

        // 5. Select Cook if dialogue opened
        if (interfaceOpen(905)) {
            delay(300)
            println("Make-X opened, selecting Cook Sharks")
            clickDialogue(905, 14)
            clickSkillDialogue(1)
            delayUntil(5000) { isAnimating() }
        }
    }

    private suspend fun handleBanking() {
        // 1. Wait for any residual cooking animation to finish before interacting with bank
        if (isAnimating()) {
            delayUntil(3000) { !isAnimating() }
        }

        // 2. If bank is not open, walk to booth and open it
        if (!bankIsOpen() && !interfaceOpen(762)) {
            val pos = getMyPlayerPosition()
            if (Utils.distance(pos, bankBoothTile) > 2) {
                println("Walking to bank booth...")
                walkTo(bankBoothTile)
                delayUntil(6000) { Utils.distance(getMyPlayerPosition(), bankBoothTile) <= 2 || !isWalking() }
            }

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

        // 3. Deposit everything
        println("Depositing cooked sharks / items...")
        bankAll()
        delayUntil(3000) { inventory.freeSlots() >= 28 }
        delay(300)

        // Wait for bank contents to be ready
        delayUntil(2000) { bank.container.itemIds.any { it != -1 } }

        // 4. Withdraw raw sharks
        println("Withdrawing raw sharks...")
        withdrawAllOfItem("raw shark")
        delayUntil(3000) { hasRawSharks() }

        // Fallback by ID 383 if withdraw by name didn't populate inventory
        if (!hasRawSharks()) {
            withdrawAllOfItem(383)
            delayUntil(3000) { hasRawSharks() }
        }

        // Check if bank is out of raw sharks
        if (!hasRawSharks()) {
            println("Out of raw sharks in bank! Stopping script.")
            closeInterfaces()
            stop()
            return
        }

        delay(300)
        closeInterfaces()
        delayUntil(2000) { !bankIsOpen() && !interfaceOpen(762) }

        // 5. Walk back to range
        println("Heading to range...")
        walkTo(standAtRangeTile)
        delayUntil(6000) { Utils.distance(getMyPlayerPosition(), standAtRangeTile) <= 2 || !isWalking() }
    }
}

CooksSharks()
