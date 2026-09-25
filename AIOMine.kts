@ScriptDescription(
    author = "Trent",
    name = "AIOMine",
    version = "1.0",
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

    override suspend fun loop() {
        if (inventory.freeSlots() <= 0) {
            openClosestBank()
            delayUntil(15000) { interfaceOpen(762) }
            depositAllExcept(5733, 20406, 20407)
        } else {
            if (isAnimating()) return
            clickClosestObject(target.value, option.value)
            delayUntil(2000) { isAnimating() }
        }
    }
}

AIOMine()
