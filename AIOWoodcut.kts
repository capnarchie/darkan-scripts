@ScriptDescription(
    author = "Trent",
    name = "AIOWoodcut",
    version = "1.0",
    description = "Chops the configured tree, picks up bird nests and banks when the inventory is full",
    category = ScriptCategory.WOODCUTTING
)
class AIOWoodcut : BotScript(), ConfigurableScript {
    val target = StringConfigItem(
        name = "Target",
        description = "Select target to woodcut",
        initialValue = "None"
    )

    val option = StringConfigItem(
        name = "Action",
        description = "Select target action",
        initialValue = "chop down"
    )

    override suspend fun loop() {
        if (inventory.freeSlots() <= 0) {
            openClosestBank()
            delayUntil(15000) { interfaceOpen(762) }
            depositAllExcept(5733, 20316, 20317)
        } else {
            if (findAndPickupItems("nest")) return
            if (isAnimating()) return
            clickClosestObject(target.value, option.value)
            delayUntil(2000) { isAnimating() }
        }
    }
}

AIOWoodcut()
