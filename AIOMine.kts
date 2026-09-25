import com.darkan.bot.scripts.withAction

@ScriptDescription(
    author = "Capnarchie",
    name = "AIOMine",
    version = "1.1",
    description = "Mines the configured rock and banks when the inventory is full",
    category = ScriptCategory.MINING
)
class AIOMine : BotScript(), ConfigurableScript {
    val target = StringConfigItem(
        name = "Target",
        description = "Select target to mine",
        initialValue = "None"
    )

    val option = StringConfigItem(
        name = "Action",
        description = "Select target action",
        initialValue = "mine"
    )

    val bankTile = StringConfigItem(
        name = "Bank tile",
        description = "x,y,plane of the bank (plane 0-3, usually 0). Stand at the bank and press Capture, or type it from the pos command",
        initialValue = ""
    ).withAction("Capture") { getMyPlayerPosition().let { "${it.x},${it.y},${it.z}" } }

    val status = InfoDisplayConfigItem(
        name = "Status",
        description = "What the script is doing",
        initialValue = ""
    )

    val debugSection = ConfigSection("Debug", "Diagnostics", defaultOpen = false)
    val debug = BooleanConfigItem(
        name = "Debug logging",
        description = "Print banking diagnostics to the client console",
        initialValue = false
    )

    private var mineSpot: Tile? = null
    private var bankTries = 0
    private var lastDebug = 0L

    override suspend fun loop() {
        try {
            loopBody()
        } catch (_: NullPointerException) {
            delay(600)
        }
    }

    private suspend fun loopBody() {        if (inventory.freeSlots() <= 0) {
            val tile = with(BankNav) { parseTile(bankTile.value) }
            if (bankTile.value.isNotBlank() && tile == null) {
                status.value = "Bad bank tile: use x,y,plane e.g. 3189,3435,0"
                delay(1000)
                return
            }
            status.value = "Banking"
            if (debug.value && System.currentTimeMillis() - lastDebug > 5000) {
                lastDebug = System.currentTimeMillis()
                println("AIOMine DEBUG " + with(BankNav) { bankDebugLine() })
            }
            val opened = with(BankNav) { ensureBankOpen(tile) }
            if (debug.value) println("AIOMine DEBUG ensureBankOpen=$opened")
            if (!opened) {
                bankTries++
                if (bankTries > 20) {
                    status.value = "Stuck going to bank, stopped"
                    println("AIOMine: could not reach or open a bank after many tries, stopping. Check the Bank tile setting.")
                    stop()
                }
                return
            }
            bankTries = 0
            for (attempt in 1..3) {
                depositAllExcept(5733, 20406, 20407)
                delay(1500)
                if (inventory.freeSlots() > 0) break
            }
            if (inventory.freeSlots() <= 0) {
                status.value = "Deposit failed, stopped"
                println("AIOMine: bank window is open but the ore did not deposit, stopping.")
                stop()
                return
            }
            delay(400)
            closeInterfaces()
            return
        }
        val spot = mineSpot
        if (spot == null) {
            mineSpot = getMyPlayerPosition()
        } else if (Utils.distance(getMyPlayerPosition(), spot) > 8) {
            status.value = "Returning to mine"
            walkTo(spot)
            delayUntil(30000) { !isWalking() }
            return
        }
        status.value = "Mining"
        if (isAnimating()) return
        clickClosestObject(target.value, option.value)
        delayUntil(2000) { isAnimating() }
    }
}

AIOMine()
