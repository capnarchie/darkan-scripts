@ScriptDescription(
    author = "Trent",
    name = "Combat",
    version = "1.0",
    description = "Attacks the configured NPC and optionally keeps special attack switched on",
    category = ScriptCategory.COMBAT
)
class Combat : BotScript(), ConfigurableScript {
    val target = StringConfigItem(
        name = "Target",
        description = "Select target to attack",
        initialValue = "None"
    )

    val keepSpecOn = BooleanConfigItem(
        name = "Keep special attack on",
        description = "Runs the spec keeper alongside this script, switching the special attack back on whenever it drops off",
        initialValue = true
    )

    private var specKeeper: KeepSpecOn? = null

    override suspend fun loop() {
        syncSpecKeeper()
        if (!myPlayerInCombat()) {
            clickNPC(getClosestNPCNoClipContaining(target.value), "attack")
            waitThenDelayUntil(1200, 6000) { !myPlayerInCombat() }
        }
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

Combat()
