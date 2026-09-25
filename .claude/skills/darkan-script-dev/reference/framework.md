# Script framework

Source of truth (branch `dev`):

- Script base: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/Script.kt
- Loader and template: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/KotlinScriptLoader.kt
- Executor: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptExecutor.kt
- Config items: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptConfig.kt
- State machines: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/StateMachineScript.kt
- Extension helpers: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/API.kt
- Events: https://gitlab.com/darkanrs/darkan-bot/-/tree/dev/src/main/kotlin/com/darkan/bot/scripts/event
- Sidebar (how scripts are listed, started, configured): https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/ui/ScriptsPanel.kt

## Loading

`KotlinScriptScanner.load(file)` compiles one `.kts` with `BotScriptCompilationConfiguration`:

```kotlin
defaultImports(
    "com.darkan.bot.Bot",
    "com.darkan.bot.Bot.*",
    "com.darkan.bot.scripts.*",
    "com.darkan.bot.scripts.event.*",
    "com.darkan.bot.utils.*",
    "kotlinx.coroutines.*"
)
jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
```

Every `.kts` under `<scripts>/lib/` (recursive, sorted by path) is added as
`importScripts(...)`, so its declarations are in scope for the script being compiled; each
script compiles the lib files again in its own class loader, so a lib class ends up as
`KeepSpecOn$KeepSpecOn` inside every importing script. The whole client classpath is
available, so any `com.jagex.*` class can be imported. After compiling, the loader
**evaluates** the file and takes `returnValue`: with a trailing
expression this is that value; it must be a `Script`. Its class is what gets registered, and
`ScriptExecutor.registerScript` only keeps classes carrying `@ScriptDescription` with
`visible = true`. Scripts are keyed by `"$name v$version by $author"`.

Discovery order on `ScriptExecutor.loadScripts()`: compiled `.jar`/`.class` files in the
scripts folder, scripts compiled into the client, then root `.kts` files (never `lib/`). The
folder is `~/.darkan/scripts` (`System.getProperty("user.home")`). Load runs at client
startup (after `ScriptRepository.sync()` when the bot is enabled), on **Reload scripts** and
after **Update scripts** in the sidebar; `ScriptExecutor.addReloadListener` observes reloads.

## Repository sync (`ScriptRepository.kt`)

https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptRepository.kt

```kotlin
object ScriptRepository {
    const val DEFAULT_REMOTE = "https://gitlab.com/darkanrs/bot-scripts.git"
    const val BRANCH = "dev"
    val remote: String                 // -Dbot.scripts.remote, else DEFAULT_REMOTE
    val syncOnStartup: Boolean         // -Dbot.scripts.sync, default true
    @Volatile var syncing: Boolean
    fun sync(directory: File = getScriptsDirectory()): Outcome
    fun syncAsync(directory: File = getScriptsDirectory(), onDone: (Outcome) -> Unit): Thread
    sealed class Outcome(val message: String) { val ok: Boolean
        class Initialized(directory, val setAside: List<String>); class UpToDate(val commit: String)
        class Updated(val from: String, val to: String, val changedFiles: Int)
        class Skipped(reason: String); class Failed(reason: String, val cause: Throwable?) }
}
fun main(args: Array<String>)          // java -cp darkan-client.jar com.darkan.bot.scripts.ScriptRepositoryKt [dir]
```

Behaviour: no `.git` → init, add origin, fetch, rename colliding files to `.local`, check out
`dev` tracking `origin/dev`. `.git` present → on `dev` with clean tracked files: fetch +
fast-forward only; otherwise fetch and `Skipped` with the reason. Network timeout 30 s.
Uses JGit (bundled), never the system git.

## Script

```kotlin
abstract class Script {
    var started: Boolean; var stopped: Boolean
    abstract suspend fun loop()
    open fun onStart()                       // default prints "<Class> started."
    open fun onStop()
    open fun onEvent(event: Event) {}
    open fun render() {}                     // reserved, not called by the current client
    fun stop()
    fun addParallelScript(script: Script)
    fun removeParallelScript(script: Script, deactivate: Boolean = true)
    fun stopParallelScripts()
    suspend fun delay(time: Int)
    suspend fun delay(mean: Int, variance: Int)                      // gaussian
    suspend fun delayWhile(timeoutMillis: Long? = null, predicate: () -> Boolean)
    suspend fun delayUntil(timeoutMillis: Long? = null, pollingDelayMillis: Int = 100, predicate: () -> Boolean)
    suspend fun waitThenDelayWhile(waitFor: Long, timeoutMillis: Long? = null, predicate: () -> Boolean)
    suspend fun waitThenDelayUntil(waitFor: Long, timeoutMillis: Long? = null, pollingDelayMillis: Int = 100, predicate: () -> Boolean)
    suspend fun waitForEvent(timeoutMillis: Long = 15000, predicate: Predicate<Event>)
    suspend fun waitForXPDrop(skill: Skill? = null, timeoutMillis: Long = 15000)
    suspend fun waitForChatContaining(type: ChatMessageType, text: String, timeoutMillis: Long = 15000)
    fun pauseOthers(): Boolean
    fun resumeOthers(): Boolean
    fun isPaused(): Boolean
    suspend fun pauseOthersFor(durationMs: Long)
}
```

Lifecycle: the first tick starts a coroutine on a `ClientPulseDispatcher` (resumes on the
game thread each pulse) that runs `onStart()`, then `loop(); delay(40)` until `stopped`, then
`onStop()` and stops parallel scripts. `waitThenDelay*` first sleeps `waitFor` with a gaussian
variance of 1000 ms, then polls. A `null` timeout waits forever. Timeouts expire silently.

`waitForEvent` installs a predicate that `_processEvent` tests against every event pushed to
the executor; only one pending predicate exists per script at a time.

### @ScriptDescription and ScriptCategory

```kotlin
annotation class ScriptDescription(
    val name: String, val version: String, val author: String, val description: String,
    val visible: Boolean = true, val category: ScriptCategory = ScriptCategory.OTHER
)
enum class ScriptCategory { COMBAT, MAGIC, PRAYER, SUMMONING, NECROMANCY, MINING, FISHING,
    WOODCUTTING, FARMING, HUNTER, DIVINATION, ARCHAEOLOGY, SMITHING, HERBLORE, COOKING, CRAFTING,
    FIREMAKING, FLETCHING, RUNECRAFTING, CONSTRUCTION, AGILITY, THIEVING, SLAYER, DUNGEONEERING,
    INVENTION, QUESTS, BOSSES, OTHER }
```

## Executor

`ScriptExecutor.mainLogic()` runs from `Bot.pulse()` at the end of every client logic pulse
(the client runs logic at 50 Hz). Per pulse: while any script is active a keep-alive task sends
a click every 4.6 to 5.5 s; every queued `Event` is delivered to every active script's
`_processEvent` (which calls `onEvent` and tests the pending wait predicate); then each active
script is ticked, or only the pausing script when one called `pauseOthers()`.

`ScriptExecutor.activate(script)` / `deactivate(script)` key on `script.javaClass.name`; a
script class can therefore run once at a time, and a `lib/` helper started by two scripts runs
as a single instance. `ScriptExecutor.scripts` (registered metadata)
and `activeScripts` are public.

## Events (`com.darkan.bot.scripts.event`)

```kotlin
interface Event
class Chat(val messageType: ChatMessageType, val cleanSenderName: String?, val crownedSenderName: String?, val formattedSenderName: String?, val message: String) : Event
class XPDrop(val skill: Skill, val gainedXp: Int) : Event
class Varp(val id: Int, val oldValue: Int, val newValue: Int) : Event      // player var changed
class Varpbit(val id: Int, val oldValue: Int, val newValue: Int) : Event   // player varbit changed
class Varc(val id: Int, val oldValue: Int, val newValue: Int) : Event      // client var changed
class Varcbit(val id: Int, val oldValue: Int, val newValue: Int) : Event
```

`ChatMessageType` (`com.jagex.game.runetek5.client.ChatMessageType`, explicit import) values
commonly needed: `GAME`, `PUBLIC_CHAT`, `PRIVATE_MESSAGE`, `FC_CHAT`, `CLAN_CHAT`,
`ITEM_EXAMINE`, `NPC_EXAMINE`, `OBJECT_EXAMINE`, `TRADE_REQUEST`, `DUEL_REQUEST`,
`FILTERABLE`, `UNFILTERABLE`. Full list:
https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/jagex/game/runetek5/client/ChatMessageType.kt

## Config items (`ScriptConfig.kt`)

https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptConfig.kt
(Swing renderer: https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/ui/ScriptConfigWrapper.kt)

```kotlin
interface ConfigurableScript                     // marker: the sidebar builds a settings form
interface ConfigItem<T> { val name: String; val description: String; var value: T; fun reset() }
interface ConfigVisibilityProvider { fun isConfigItemVisible(fieldName: String, item: ConfigItem<*>): Boolean }
interface ActionableConfig<T> { var action: ConfigAction<T>? }
class ConfigAction<T>(val label: String, val run: () -> T?)
fun <T, I> I.withAction(label: String, run: () -> T?): I where I : ConfigItem<T>, I : ActionableConfig<T>

class BooleanConfigItem(name, description, initialValue: Boolean = false)
class IntConfigItem(name, description, initialValue: Int = 0, val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE)
class StringConfigItem(name, description, initialValue: String = "")
class OptionsConfigItem<T>(name, description, val options: Array<T>, initialValue: T)
class EnumConfigItem<T : Enum<T>>(name, description, val enumValues: Array<T>, initialValue: T)
class InfoDisplayConfigItem(name, description, initialValue: String = "")     // read-only status line
class ConfigSection(name, description = "", val defaultOpen: Boolean = true)  // collapsible header, no value

object ScriptConfigStore { fun save(script: Any); fun applyTo(script: Any) }
```

Every item except `ConfigSection` implements `ActionableConfig`, so `withAction` works on all of
them. `reset()` restores the value the item was declared with, which is what the form's "Reset to
defaults" calls.

`ScriptConfigWrapper` reflects over `script::class.java.declaredFields` whose type is a
`ConfigItem` (any visibility) in declaration order, filters them through
`ConfigVisibilityProvider` when the script implements it, groups them by `ConfigSection`, and
binds each editor directly to `item.value`. After every change it calls `ScriptConfigStore.save`,
invokes the script's `onConfigUpdated()` if it declares one, and rebuilds the form when the
script provides visibility rules.

`ScriptConfigStore` keys values by `script.javaClass.name` (stable across recompiles) and is
applied by `ScriptInstances.instantiate`, so settings survive stopping, restarting and reloading.
`InfoDisplayConfigItem` and `ConfigSection` values are not stored. Enum values are rebound by
name, because a reloaded script's enum is a different class in a new classloader.

## State machines (`StateMachineScript.kt`)

```kotlin
abstract class StateMachineScript<T : StateMachineScript<T>> : Script() {
    abstract fun getStartState(): State<T>
    // loop(): if currentState.checkNext() returns a state, switch to it and return; else run stateLoop()
    // onEvent(): forwarded to currentState.onStateEvent
}
abstract class State<T : StateMachineScript<T>> {
    abstract suspend fun T.checkNext(): State<T>?
    abstract suspend fun T.stateLoop()
    open fun T.onStateEvent(event: Event) {}
}
```

Pattern:

```kotlin
class Miner : StateMachineScript<Miner>() {
    override fun getStartState() = Mine
}
object Mine : State<Miner>() {
    override suspend fun Miner.checkNext() = if (inventory.freeSlots() == 0) Bank else null
    override suspend fun Miner.stateLoop() { /* … */ }
}
object Bank : State<Miner>() {
    override suspend fun Miner.checkNext() = if (inventory.freeSlots() > 0) Mine else null
    override suspend fun Miner.stateLoop() { /* … */ }
}
Miner()
```

`getStartState()` is called from the base constructor, so it must not depend on the
subclass's own fields (return an `object`). Exceptions in a state are printed and swallowed,
and the loop continues.

## Extension helpers (`API.kt`)

```kotlin
suspend fun Script.findAndPickupItems(vararg items: String): Boolean        // nearest matching ground item within 15 tiles; true if one was taken
suspend fun Script.findAndPickupItems(distance: Int, vararg items: String): Boolean
suspend fun Script.findAndPickupItems(vararg items: Int)                     // by item id, loops until none remain or inventory is full
```

## Top-level helpers (`com.darkan.bot.Util.kt`)

These live in package `com.darkan.bot` as top-level functions and are **not** covered by the
automatic imports (only the `Bot` object is). Import what you use, e.g.
`import com.darkan.bot.random` / `import com.darkan.bot.gaussian`:

```kotlin
fun random(max: Int): Int; fun random(min: Int, max: Int): Int; fun random(max: Long): Long; fun random(min: Long, max: Long): Long
fun randomInclusive(max: Int): Int; fun randomInclusive(min: Int, max: Int): Int
fun randomD(max: Double): Double; fun randomD(min: Double, max: Double): Double; fun randomD(): Double
fun gaussian(mean: Int, variance: Int): Int; fun gaussian(mean: Long, variance: Long): Long
fun getAngleTo(from: Tile, to: Tile): Int; fun getDirDelta(angle: Int): Pair<Int, Int>?
fun hashFromInterface(interfaceId: Int, componentId: Int): Int; fun interfaceIdFromHash(hash: Int): Int; fun componentIdFromHash(hash: Int): Int
fun millisElapsed(startTime: Long): Long; fun secondsElapsed(startTime: Long): Float; fun minutesElapsed(startTime: Long): Float; fun hoursElapsed(startTime: Long): Float
fun getXpPerHour(startingXp: Int, currentXp: Int, startTime: Long): Int; fun getFormattedXpPerHour(startingXp: Int, currentXp: Int, startTime: Long): String
fun getUnitsPerHour(unitsGained: Int, startTime: Long): Int; fun getFormattedUnitsPerHour(unitsGained: Int, startTime: Long): String
fun format(number: Int): String; fun format(number: Float, decimals: Int = 2): String; fun format(number: Double, decimals: Int = 2): String
fun formatElapsedTime(currTime: Long, startTime: Long): String              // HH:MM:SS
```
