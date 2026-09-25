import com.jagex.game.runetek5.config.TypeLists

@ScriptDescription(
    author = "Trent",
    name = "Alch",
    version = "1.0",
    description = "Alchs items by name",
    category = ScriptCategory.MAGIC
)
class Alch : BotScript(), ConfigurableScript {
    val itemName = StringConfigItem(
        name = "Target",
        description = "Select target to alch",
        initialValue = "None"
    )

    override suspend fun loop() {
        val targetName = itemName.value.trim()

        if (targetName.isEmpty() || targetName.equals("None", ignoreCase = true)) {
            delay(600)
            return
        }

        val itemSlot = inventory.container.itemIds.indexOfFirst { itemId ->
            itemId > 0 && TypeLists.OBJ!!.list(itemId).name.contains(targetName, ignoreCase = true)
        }

        if (itemSlot != -1) {
            alch(itemSlot)
            delay(310)
        } else
            delay(600)
    }
}

Alch()
