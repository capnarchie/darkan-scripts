import com.jagex.game.runetek5.entity.pathingentity.npc.NpcEntity

@ScriptDescription(
    author = "Trent",
    name = "Advanced Combat",
    version = "1.2",
    description = "Attacks targets, eats food at low HP, manages Vampyrism aura, loots charms, keeps target lock, and manages spec",
    category = ScriptCategory.COMBAT
)
class AdvancedCombat : BotScript(), ConfigurableScript {
    val target = StringConfigItem(
        name = "Target",
        description = "Select target to attack (substring match)",
        initialValue = "None"
    )

    val eatHpPercent = IntConfigItem(
        name = "Eat at HP %",
        description = "Player health percentage threshold to eat food",
        initialValue = 50
    )

    val foodName = StringConfigItem(
        name = "Food name",
        description = "Preferred food name (leave blank to auto-detect any food)",
        initialValue = "shark"
    )

    val manageAura = BooleanConfigItem(
        name = "Manage Vampyrism aura",
        description = "Automatically activates Vampyrism aura when off cooldown or depleted",
        initialValue = true
    )

    val pickupCrimson = BooleanConfigItem(
        name = "Pickup crimson charms",
        description = "Picks up crimson charms dropped on the ground",
        initialValue = true
    )

    val pickupBlue = BooleanConfigItem(
        name = "Pickup blue charms",
        description = "Picks up blue charms dropped on the ground",
        initialValue = true
    )

    val pickupGold = BooleanConfigItem(
        name = "Pickup gold charms",
        description = "Picks up gold charms dropped on the ground",
        initialValue = false
    )

    val pickupGreen = BooleanConfigItem(
        name = "Pickup green charms",
        description = "Picks up green charms dropped on the ground",
        initialValue = false
    )

    val keepSpecOn = BooleanConfigItem(
        name = "Keep special attack on",
        description = "Runs the spec keeper alongside this script, switching the special attack back on whenever it drops off",
        initialValue = true
    )

    private var specKeeper: KeepSpecOn? = null
    private var lastEatTime = 0L
    private var lastAuraAttempt = 0L
    private var auraActivatedTime = 0L
    private var auraNeedsActivation = true
    private var previousTargetPid: Int? = null
    private val failedLootAttempts = mutableMapOf<String, Long>()

    private val commonFoods = listOf(
        "rocktail", "shark", "cavefish", "monkfish", "swordfish",
        "lobster", "bass", "tuna", "salmon", "trout", "cooked meat",
        "saradomin brew"
    )

    override fun onStart() {
        super.onStart()
        auraNeedsActivation = true
        lastAuraAttempt = 0L
        auraActivatedTime = 0L
        previousTargetPid = null
        failedLootAttempts.clear()
    }

    override fun onStop() {
        super.onStop()
        specKeeper?.let {
            removeParallelScript(it)
            specKeeper = null
        }
    }

    override fun onEvent(event: Event) {
        if (event is Chat) {
            val msg = event.message.lowercase()
            if (msg.contains("your aura has depleted") || msg.contains("your aura has finished recharging")) {
                auraNeedsActivation = true
                auraActivatedTime = 0L
            } else if (msg.contains("your aura is already activated")) {
                auraNeedsActivation = false
                if (auraActivatedTime == 0L) {
                    auraActivatedTime = System.currentTimeMillis()
                }
            } else if (msg.contains("your aura has not recharged yet")) {
                auraNeedsActivation = false
            } else if (msg.contains("already in combat") || msg.contains("already under attack")) {
                val attacker = getNPCs().firstOrNull { facingMe(it) && isAlive(it) }
                if (attacker != null) {
                    clickNPC(attacker, "attack")
                }
            }
        }
    }

    override suspend fun loop() {
        syncSpecKeeper()
        checkHealthAndEat()

        if (manageAura.value) {
            checkAndActivateAura()
        }

        // 1. Identify active attackers (NPCs targeting our player)
        val attackers = getNPCs().filter { facingMe(it) && isAlive(it) }
        val currentTarget = getFacing(getMyPlayer()) as? NpcEntity
        val isTargetAlive = isAlive(currentTarget)

        // 2. Active combat: if we are facing an alive target we are fighting, stay locked onto it
        if (currentTarget != null && isTargetAlive) {
            if (attackers.isEmpty() || attackers.any { getPid(it) == getPid(currentTarget) }) {
                previousTargetPid = getPid(currentTarget)
                if (!isAnimating() && hasntAnimatedFor(3600)) {
                    clickNPC(currentTarget, "attack")
                }
                delay(600, 100)
                return
            }
        }

        // 3. When our previous target has just died, wait 1.2s for death animation and drops to spawn
        if (previousTargetPid != null) {
            delay(1200, 200)
            previousTargetPid = null
        }

        // 4. Ground charms take absolute priority before engaging any new target
        if (pickupCharms()) {
            return
        }

        // 5. If another NPC is attacking us, retaliate
        if (attackers.isNotEmpty()) {
            val primaryAttacker = attackers.first()
            clickNPC(primaryAttacker, "attack")
            previousTargetPid = getPid(primaryAttacker)
            delay(800, 200)
            return
        }

        // 6. Out of combat: find and attack the next target
        val targetName = target.value.trim()
        if (targetName.isNotEmpty() && !targetName.equals("none", ignoreCase = true)) {
            val nextTarget = findNextTarget(targetName)
            if (nextTarget != null) {
                clickNPC(nextTarget, "attack")
                previousTargetPid = getPid(nextTarget)
                delay(800, 200)
            }
        }
    }

    private fun isAlive(npc: NpcEntity?): Boolean {
        if (npc == null || npc.definitions == null) return false
        val hp = getHitbarValue(npc, 0)
        if (hp == 0) return false
        return getNPCs().any { getPid(it) == getPid(npc) }
    }

    private fun isEngagedWithOther(npc: NpcEntity): Boolean {
        if (npc.faceEntity != -1 && !facingMe(npc)) {
            return true
        }
        val hp = getHitbarValue(npc, 0)
        if (hp in 1..99 && !facingMe(npc)) {
            return true
        }
        return false
    }

    private fun findNextTarget(targetSubstring: String): NpcEntity? {
        val myPos = getMyPlayerPosition()
        val candidates = getFilteredNPCsContaining(targetSubstring).filter { npc ->
            isAlive(npc) && !isEngagedWithOther(npc)
        }
        return candidates.minByOrNull { npc ->
            val dist = getDistanceTo(npc)
            if (dist >= 0) dist else Utils.distance(myPos, Tile(npc.endX + getBaseX(), npc.endZ + getBaseY()))
        }
    }

    private suspend fun checkHealthAndEat() {
        if (getHealthPercent() <= eatHpPercent.value) {
            val now = System.currentTimeMillis()
            if (now - lastEatTime < 1800) {
                return
            }

            val preferred = foodName.value.trim().lowercase()
            var foodSlot = -1

            if (preferred.isNotEmpty() && inventory.contains(preferred, 1)) {
                foodSlot = inventory.getSlotByItem(preferred)
            }

            if (foodSlot == -1) {
                for (food in commonFoods) {
                    if (inventory.contains(food, 1)) {
                        foodSlot = inventory.getSlotByItem(food)
                        break
                    }
                }
            }

            if (foodSlot != -1) {
                clickItem(inventory.getItem(foodSlot), foodSlot)
                lastEatTime = now
                delay(600, 200)
            }
        }
    }

    private suspend fun checkAndActivateAura() {
        val now = System.currentTimeMillis()
        val oneHourMillis = 3600000L

        val expiredByTimer = (auraActivatedTime > 0 && now - auraActivatedTime >= oneHourMillis)
        if (auraNeedsActivation || expiredByTimer) {
            if (now - lastAuraAttempt >= 10000) {
                lastAuraAttempt = now
                clickButton(387, 36, 14, 22298, 2)
                auraNeedsActivation = false
                auraActivatedTime = now
                delay(600, 200)
            }
        }
    }

    private fun getTargetCharmIds(): Set<Int> {
        val ids = mutableSetOf<Int>()
        if (pickupCrimson.value) ids.add(12160)
        if (pickupBlue.value) ids.add(12163)
        if (pickupGold.value) ids.add(12158)
        if (pickupGreen.value) ids.add(12159)
        return ids
    }

    private suspend fun pickupCharms(): Boolean {
        val targetIds = getTargetCharmIds()
        if (targetIds.isEmpty()) return false

        val myPos = getMyPlayerPosition()
        val now = System.currentTimeMillis()

        failedLootAttempts.entries.removeIf { now - it.value > 30000 }

        val charms = getGroundItems().filter { item ->
            item.id in targetIds &&
            Utils.distance(myPos, item.position) <= 15 &&
            getDistanceTo(item.position) != -1 &&
            !failedLootAttempts.containsKey("${item.id}_${item.position.x}_${item.position.y}")
        }

        val charm = charms.minByOrNull { Utils.distance(myPos, it.position) } ?: return false

        if (inventory.freeSlots() <= 0 && !inventory.contains(charm.id, 1)) {
            return false
        }

        val charmKey = "${charm.id}_${charm.position.x}_${charm.position.y}"
        val charmX = charm.position.x
        val charmY = charm.position.y
        val charmId = charm.id

        takeGroundItem(charm)

        delayUntil(3500, 200) {
            getGroundItems().none { it.id == charmId && it.position.x == charmX && it.position.y == charmY }
        }

        val stillThere = getGroundItems().any { it.id == charmId && it.position.x == charmX && it.position.y == charmY }
        if (stillThere) {
            failedLootAttempts[charmKey] = now
        }

        delay(300, 100)
        return true
    }

    private fun syncSpecKeeper() {
        val running = specKeeper
        if (keepSpecOn.value && running == null) {
            specKeeper = KeepSpecOn().also { addParallelScript(it) }
        } else if (!keepSpecOn.value && running != null) {
            removeParallelScript(running)
            specKeeper = null
        }
    }
}

AdvancedCombat()
