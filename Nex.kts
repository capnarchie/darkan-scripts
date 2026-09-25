@ScriptDescription(
    author = "Trent",
    name = "Nex",
    version = "1.0",
    description = "Kills Nex",
    category = ScriptCategory.BOSSES
)
class Nex : StateMachineScript<Nex>(), ConfigurableScript {
    override fun getStartState() = Fight

    override fun onStart() {
        addParallelScript(KeepSpecOn())
    }
}

object Fight : State<Nex>() {
    override suspend fun Nex.checkNext() = null
    override suspend fun Nex.stateLoop() {
    }
}

Nex()
