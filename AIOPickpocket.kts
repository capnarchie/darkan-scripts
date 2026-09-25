@ScriptDescription(
    author = "Trent",
    name = "AIO Pickpocket",
    version = "1.0",
    description = "Pickpockets the configured NPC and banks when the inventory is full",
    category = ScriptCategory.THIEVING
)
class AIOPickpocket : BotScript(), ConfigurableScript {
    val target = StringConfigItem(
        name = "Target",
        description = "Select target to pickpocket",
        initialValue = "None"
    )

    override suspend fun loop() {
        if (inventory.freeSlots() <= 0) {
            openClosestBank()
            delayUntil(15000) { interfaceOpen(762) }
            bankAll()
        } else {
            clickNPC(getClosestNPCNoClip(target.value), "pickpocket")
            delay(200)
        }
    }
}

AIOPickpocket()
