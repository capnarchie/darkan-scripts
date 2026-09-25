enum class UrnType(val page1Comp: Int, val page2Comp: Int) {
    STRONG_SMELTING(17, 17),
    DECORATED_MINING(14, 18),
    STRONG_WOODCUTTING(18, 17),
    INFERNAL(19, 16),
    DECORATED_FISHING(16, 18),
    DECORATED_COOKING(15, 18),
}

@ScriptDescription(
    author = "Trent",
    name = "CraftUrns",
    version = "1.0",
    description = "Forms and fires the selected urn from soft clay, banking between batches",
    category = ScriptCategory.CRAFTING
)
class CraftUrns : BotScript(), ConfigurableScript {
    private val urnType = EnumConfigItem(
        name = "Urn Type",
        description = "Select urn type",
        enumValues = UrnType.entries.toTypedArray(),
        initialValue = UrnType.DECORATED_MINING
    )

    override suspend fun loop() {
        if (!inventory.contains("soft clay", 2) && !inventory.contains("(unf)", 1)) {
            if (!interfaceOpen(762)) {
                openClosestBank()
                delayUntil(15000) { interfaceOpen(762) }
                return
            }
            bankAll()
            withdrawAllButOneItem("soft clay")
            delayUntil(15000) { inventory.contains("soft clay", 2) }
        } else {
            if (inventory.contains("(unf)", 1)) {
                if (!interfaceOpen(905)) {
                    clickClosestObject("pottery oven", "use")
                    delayUntil(2000) { interfaceOpen(905) }
                } else {
                    clickDialogue(905, 14)
                    delayUntil(40000) { !inventory.contains("(unf)", 1) }
                }
            } else {
                if (!interfaceOpen(905)) {
                    clickClosestObject("pottery wheel", "form")
                    delayUntil(2000) { interfaceOpen(905) }
                } else {
                    clickDialogue(905, 15)
                    delay(1200)
                    clickDialogue(905, urnType.value.page1Comp)
                    delay(1200)
                    clickDialogue(905, urnType.value.page2Comp)
                    delayUntil(40000) { !inventory.contains("soft clay", 2) }
                }
            }
        }
    }
}

CraftUrns()
