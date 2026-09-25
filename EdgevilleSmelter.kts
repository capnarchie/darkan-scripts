@ScriptDescription(
    author = "Trent",
    name = "Edgeville Smelter",
    version = "2.0",
    description = "Smelts metal bars at the Edgeville furnace with automatic banking",
    category = ScriptCategory.SMITHING
)
class EdgevilleSmelter : BotScript(), ConfigurableScript {

    val targetBarConfig = StringConfigItem(
        name = "Bar to smelt",
        description = "Type bar: bronze, iron, silver, steel, gold, mithril, adamant, or rune",
        initialValue = "steel"
    )

    val status = InfoDisplayConfigItem(
        name = "Status",
        description = "Current script activity",
        initialValue = "Initializing"
    )

    val barsSmeltedDisplay = InfoDisplayConfigItem(
        name = "Bars smelted",
        description = "Total bars smelted this session",
        initialValue = "0"
    )

    // ----- Recipe data -----

    data class BarRecipe(
        val name: String,
        val barId: Int,
        val primaryOreId: Int,
        val primaryPerLoad: Int,
        val secondaryOreId: Int = -1,
        val secondaryPerLoad: Int = 0,
        val perBarPrimary: Int = 1,
        val perBarSecondary: Int = 0,
        val makeXComponent: Int = 14  // component id on interface 905
    )

    private val bankTile = Tile(3096, 3494, 0)

    private var barsSmelted = 0
    private var startSmithingXp = 0
    private var startTime = 0L

    private fun getRecipe(): BarRecipe {
        val input = targetBarConfig.value.trim().lowercase()
        return when {
            input.contains("bronze") -> BarRecipe("Bronze", 2349, 436, 14, 438, 14, 1, 1, 14)
            input.contains("iron") -> BarRecipe("Iron", 2351, 440, 28, makeXComponent = 14)
            input.contains("silver") -> BarRecipe("Silver", 2355, 442, 28, makeXComponent = 14)
            input.contains("steel") -> BarRecipe("Steel", 2353, 440, 9, 453, 18, 1, 2, 14)
            input.contains("gold") -> BarRecipe("Gold", 2357, 444, 28, makeXComponent = 14)
            input.contains("mith") -> BarRecipe("Mithril", 2359, 447, 5, 453, 20, 1, 4, 14)
            input.contains("addy") || input.contains("adamant") -> BarRecipe("Adamant", 2361, 449, 4, 453, 24, 1, 6, 14)
            input.contains("rune") || input.contains("runite") -> BarRecipe("Rune", 2363, 451, 3, 453, 24, 1, 8, 14)
            else -> BarRecipe("Steel", 2353, 440, 9, 453, 18, 1, 2, 14)
        }
    }

    private fun hasOresForOneBar(recipe: BarRecipe): Boolean {
        if (!inventory.contains(recipe.primaryOreId, 1)) return false
        if (recipe.secondaryOreId != -1 && !inventory.contains(recipe.secondaryOreId, recipe.perBarSecondary)) return false
        return true
    }

    override fun onStart() {
        startSmithingXp = getXp(Skill.SMITHING.ordinal)
        startTime = System.currentTimeMillis()
        setRunning(true)
        println("Smelter started")
    }

    override fun onEvent(event: Event) {
        if (event is XPDrop && event.skill == Skill.SMITHING) {
            barsSmelted++
            barsSmeltedDisplay.value = "$barsSmelted"
        }
    }

    // =============================
    //  MAIN LOOP — dead simple
    // =============================

    override suspend fun loop() {
        val recipe = getRecipe()

        // ---- PHASE 1: Need ores? Go bank ----
        if (!hasOresForOneBar(recipe)) {
            doBanking(recipe)
            return
        }

        // ---- PHASE 2: Have ores, need to smelt ----

        // If actively animating (smelting), just wait
        if (isAnimating()) {
            status.value = "Smelting"
            delay(600)
            return
        }

        // If Make-X interface is open, click the bar option
        if (interfaceOpen(905)) {
            println("Make-X open, clicking component ${recipe.makeXComponent}")
            clickDialogue(905, recipe.makeXComponent)
            delayUntil(5000) { isAnimating() }
            return
        }

        // Otherwise, click the furnace and wait for the Make-X dialogue
        status.value = "Clicking furnace"
        println("Clicking furnace...")
        clickClosestObject("Furnace", "Smelt")
        delayUntil(5000) { interfaceOpen(905) }

        if (interfaceOpen(905)) {
            println("Make-X opened! Clicking component ${recipe.makeXComponent}")
            delay(600)
            clickDialogue(905, recipe.makeXComponent)
            delayUntil(5000) { isAnimating() }
        } else {
            println("Make-X did NOT open after clicking furnace")
        }
    }

    // =============================
    //  BANKING
    // =============================

    private suspend fun doBanking(recipe: BarRecipe) {
        // Walk to bank if not near
        if (Utils.distance(getMyPlayerPosition(), bankTile) > 5) {
            status.value = "Walking to bank"
            walkTo(bankTile)
            delayUntil(10000) { Utils.distance(getMyPlayerPosition(), bankTile) <= 4 }
            return
        }

        // Open bank if not open
        if (!interfaceOpen(762)) {
            status.value = "Opening bank"
            openClosestBank()
            delayUntil(5000) { interfaceOpen(762) }
            return
        }

        // Bank is open — deposit everything
        status.value = "Banking"
        bankAll()
        delayUntil(3000) { inventory.freeSlots() >= 28 }
        delay(300)

        // Wait for bank contents to load
        delayUntil(2000) { bank.container.itemIds.any { it != -1 } }

        // Check supply
        if (bank.numberOf(recipe.primaryOreId) < recipe.primaryPerLoad) {
            println("Out of primary ore! Stopping.")
            status.value = "Out of ores"
            closeInterfaces()
            stop()
            return
        }
        if (recipe.secondaryOreId != -1 && bank.numberOf(recipe.secondaryOreId) < recipe.secondaryPerLoad) {
            println("Out of secondary ore/coal! Stopping.")
            status.value = "Out of ores"
            closeInterfaces()
            stop()
            return
        }

        // Withdraw ores
        status.value = "Withdrawing ores"
        if (recipe.secondaryOreId == -1) {
            // Single-ore bar: fill inventory
            withdrawAllOfItem(recipe.primaryOreId)
            delayUntil(3000) { inventory.contains(recipe.primaryOreId, 1) }
        } else {
            // Two-ore bar: withdraw exact amounts
            withdrawExact(recipe.primaryOreId, recipe.primaryPerLoad)
            delayUntil(3000) { inventory.contains(recipe.primaryOreId, recipe.primaryPerLoad) }
            withdrawExact(recipe.secondaryOreId, recipe.secondaryPerLoad)
            delayUntil(3000) { inventory.contains(recipe.secondaryOreId, recipe.secondaryPerLoad) }
        }

        delay(300)
        closeInterfaces()
        delayUntil(2000) { !interfaceOpen(762) }

        // Walk to furnace
        val furnaceTile = Tile(3109, 3499, 0)
        status.value = "Walking to furnace"
        walkTo(furnaceTile)
        delayUntil(8000) { Utils.distance(getMyPlayerPosition(), furnaceTile) <= 3 }
    }

    private suspend fun withdrawExact(itemId: Int, amount: Int) {
        var remaining = amount
        while (remaining >= 10) {
            withdraw10Item(itemId)
            delay(250)
            remaining -= 10
        }
        while (remaining >= 5) {
            withdraw5Item(itemId)
            delay(250)
            remaining -= 5
        }
        while (remaining >= 1) {
            withdrawOneItem(itemId)
            delay(200)
            remaining -= 1
        }
    }
}

EdgevilleSmelter()
