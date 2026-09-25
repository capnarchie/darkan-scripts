---
name: darkan-script-dev
description: Write, review and verify KotlinScript (.kts) bot scripts for the Darkan RuneScape client using the client's public GitLab source as the API reference. Use whenever a script in this repository is created, changed, debugged or installed, or when the user asks how the bot API works.
---

# Darkan bot script development

This repository holds the scripts; the client that compiles and runs them is a different
public GitLab project. Assume the client is **not** cloned locally. Every API question is
answered from GitLab raw URLs (fetch them with WebFetch or `curl`), from
`reference/bot-api.md` (a signature snapshot with a link per source file) and from
`reference/framework.md` (how the client loads and runs a script).

```
Client repository ......... https://gitlab.com/darkanrs/darkan-bot            (branch: dev)
Raw file pattern .......... https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/<package path>/<File>.kt
Browse a package .......... https://gitlab.com/darkanrs/darkan-bot/-/tree/dev/src/main/kotlin/<package path>
Latest dev client jar ..... https://gitlab.com/darkanrs/darkan-bot/-/jobs/artifacts/dev/raw/dist/darkan-client.jar?job=build
Latest release jar ........ https://gitlab.com/darkanrs/darkan-bot/-/releases/permalink/latest/downloads/darkan-client.jar
```

The files that define the scripting surface:

| File | Contains |
|------|----------|
| `com/darkan/bot/Bot.kt` | The `Bot` object: every game action and query (banking, clicking, NPCs, objects, ground items, vars, skills). |
| `com/darkan/bot/scripts/Script.kt` | `Script` base class (`loop`, `onStart`, `onStop`, `onEvent`, waits, parallel scripts), `@ScriptDescription`, `ScriptCategory`. |
| `com/darkan/bot/scripts/KotlinScriptLoader.kt` | `BotScript` template, the automatic imports, how `.kts` files are compiled and which value is registered. |
| `com/darkan/bot/scripts/ScriptConfig.kt` | `ConfigurableScript` and the `*ConfigItem` classes behind the sidebar settings form. |
| `com/darkan/bot/scripts/StateMachineScript.kt` | `StateMachineScript<T>` and `State<T>` for multi-phase scripts. |
| `com/darkan/bot/scripts/API.kt` | `Script` extension helpers such as `findAndPickupItems`. |
| `com/darkan/bot/scripts/ScriptExecutor.kt` | How scripts are ticked, paused and how events are dispatched. |
| `com/darkan/bot/scripts/ScriptCheck.kt` | The compile-check entry point used below. |
| `com/darkan/bot/scripts/event/*.kt` | `Event`, `Chat`, `XPDrop`, `Varp`, `Varpbit`, `Varc`, `Varcbit`. |
| `com/darkan/bot/utils/*.kt` | `Tile`, `Area`, `Item`, `WorldObject`, `ContainerWrapper`, `Skill`, `Utils`. |
| `com/darkan/bot/Util.kt` | Top-level `random`, `gaussian`, elapsed-time and number formatting helpers. |

When the snapshot and the live source disagree, the live source wins. Fetch the raw file
whenever you need a parameter list, a return type or a behaviour detail the snapshot omits.

## 1. What a script file is

One `.kts` file in the repository root is one script. The client compiles each file on its own
with the `BotScript` template, then **evaluates** it and registers the value of the last
expression, which must be an instance of a `Script` subclass carrying `@ScriptDescription`.

```kotlin
import com.jagex.game.runetek5.config.TypeLists          // only com.jagex.* needs imports

@ScriptDescription(
    name = "Iron Miner",
    version = "1.0",
    author = "You",
    description = "Mines iron at the configured rock and banks when full",
    category = ScriptCategory.MINING
)
class IronMiner : BotScript(), ConfigurableScript {
    val rock = StringConfigItem(name = "Rock", description = "Object name to mine", initialValue = "iron")

    override suspend fun loop() {
        if (inventory.freeSlots() <= 0) {
            openClosestBank()
            delayUntil(15000) { interfaceOpen(762) }
            bankAll()
            return
        }
        if (isAnimating()) return
        clickClosestObject(rock.value, "mine")
        delayUntil(2000) { isAnimating() }
    }
}

IronMiner()
```

Rules that follow from how the loader works (`KotlinScriptLoader.kt`):

- **Automatic imports**: `com.darkan.bot.Bot` and `com.darkan.bot.Bot.*` (so every `Bot`
  member is callable unqualified), `com.darkan.bot.scripts.*`, `com.darkan.bot.scripts.event.*`,
  `com.darkan.bot.utils.*`, `kotlinx.coroutines.*`. Everything else, notably
  `com.jagex.game.runetek5.config.TypeLists` (item/NPC/object definitions),
  `com.jagex.game.runetek5.client.ChatMessageType`,
  `com.jagex.game.runetek5.entity.pathingentity.npc.NpcEntity`, needs an explicit `import` at
  the top of the file.
- **The last expression is the script**. `MyScript()` on the final line. Without it the file
  loads but nothing is registered.
- **Extend `BotScript`** (or `StateMachineScript<T>`; see `reference/framework.md`). Plain
  `Script` also works but `BotScript` is the template the loader expects.
- **The class name must be a valid Kotlin identifier and should equal the file name**
  (`IronMiner.kts` holds `class IronMiner`). Compiled classes are nested under the file's
  script class (`IronMiner$IronMiner`), so two files may reuse a helper class name without
  colliding. Two scripts in the same file are allowed but the file registers only the returned
  one; other classes are helpers.
- **File names must be unique across the whole tree.** The compiled class name comes from the
  file name alone, not its folder, so `Combat.kts` and `private/Combat.kts` both become
  `Combat$Combat`. The client loads the first and skips the second with a message; rename one.
- **Script files cannot see each other, but every file under `lib/` is compiled into every
  script.** Put helpers used by more than one script (a parallel script such as
  `lib/KeepSpecOn.kts`, shared enums, extension functions) in `lib/` and use them unqualified.
  See section 5.
- **Loading instantiates the script.** The client runs the file to get the return value, and it
  does so at startup and on every reload, possibly before the cache or the player exist. Field
  initializers therefore must not touch `TypeLists`, `getMyPlayer()`, the scene or the
  inventory. Use `val x by lazy { … }` or compute inside `loop()`/`onStart()`. A script whose
  initializer throws silently disappears from the list.
- **Every `@ScriptDescription` field is required** except `visible` (default `true`) and
  `category` (default `OTHER`). Scripts without the annotation, or with `visible = false`, are
  not listed. The sidebar shows `name vversion by author` and groups by `category`.
- **No-arg constructor.** The sidebar starts a script with `getDeclaredConstructor().newInstance()`.
  Do not add constructor parameters.
- The client scans the **whole folder tree** for `*.kts` (and `*.jar`/`*.class` for compiled
  scripts), so users may group scripts into folders however they like. Two folders are special:
  `lib/` holds shared code rather than scripts (section 5), and hidden folders plus `.git` are
  skipped. `private/` is ignored by git and skipped by the installers, so it is where a user
  keeps scripts that are theirs alone; they still load.

## 2. How a script runs

Read `reference/framework.md` for the full model. The essentials:

- `ScriptExecutor.mainLogic()` runs once per client pulse (roughly every 20 ms) on the game
  thread and ticks each active script. A script is a coroutine: `onStart()`, then
  `loop()` repeatedly with a 40 ms `delay` between calls, then `onStop()` when stopped.
- All waiting is cooperative. Use `delay(ms)`, `delay(mean, variance)` (gaussian),
  `delayUntil(timeoutMs) { cond }`, `delayWhile(timeoutMs) { cond }`,
  `waitThenDelayUntil(waitMs, timeoutMs) { cond }`, `waitForXPDrop(skill)`,
  `waitForChatContaining(type, text)`, `waitForEvent { it is Varp && it.id == 300 }`. All
  timeouts return normally when they expire; check the condition again afterwards if it
  matters. `Thread.sleep`, `runBlocking` and busy loops freeze the client.
- `loop()` should do one small step and return. Long `while` loops inside `loop()` are fine
  only if every iteration suspends.
- `onEvent(event)` receives `Chat`, `XPDrop`, `Varp`, `Varpbit`, `Varc`, `Varcbit` for every
  active script, before the tick.
- `addParallelScript(script)` starts a helper script that ticks alongside and stops with the
  parent (`Combat.kts` keeps the special attack on this way). `pauseOthers()` /
  `resumeOthers()` / `pauseOthersFor(ms)` let one script suspend every other active script.
- `stop()` ends the script; the sidebar also stops it. Runtime exceptions from `loop()` are
  printed and end the coroutine, so guard calls that can return `null` or throw.
- `ScriptExecutor.scripts` and `ScriptExecutor.activeScripts` are visible if a script needs to
  reason about other scripts, but this is rarely needed.

## 3. Using the Bot API well

`reference/bot-api.md` lists every public member of `Bot` by topic with the exact
signatures. Patterns that matter:

- **Name matching is substring, case-insensitive, often lowercase-only.**
  `inventory.contains("soft clay", 2)`, `inventory.getSlotByItem("nest")`,
  `getObjectsNearby("tree")` all match `name.lowercase().contains(arg)` or
  `contains(arg, ignoreCase = true)`. Pass lowercase names. `getFilteredNPCs(String)` and
  `getObjectsNearbyExact` are exact (case-insensitive) matches; the `…Containing` variants
  are substring matches.
- **Option strings are the right-click menu text**: `"mine"`, `"chop down"`, `"pickpocket"`,
  `"attack"`, `"use"`, `"form"`. `clickClosestObject(name, option)` and `clickNPC(npc, option)`
  resolve the option index from the definitions; an option that does not exist resolves to
  `-1` and nothing is sent.
- **Clicks are fire-and-forget packets.** They return immediately; the character starts moving
  a pulse later. Always follow a click with `delayUntil(...)` on an observable effect
  (`isAnimating()`, `interfaceOpen(id)`, an inventory change, `!isWalking()`), with a timeout.
- **Distances use the pathfinder.** `getClosestObject(...)`, `getClosestNPC(...)` and
  `getDistanceTo(...)` walk a route and return `-1`/skip targets that are unreachable
  (behind a fence, other plane). `…NoClip` variants use straight-line distance and will pick
  unreachable targets.
- **Interfaces**: bank `762`, deposit box `11`, inventory `679`, skill/make-x dialogue `905`,
  continue dialogue `1184`/`18`, quick prayers `749`/`4`, special attack toggle `884`/`4`.
  `clickButton(interfaceId, componentId[, slot[, item], clickType])` sends the component
  packet; `clickDialogue(interfaceId, componentId)` is the same for dialogue components;
  `clickSkillDialogue(option)` maps to the make-x components of interface `905`.
- **Ground items**: `findGroundItem(name|id, distance)` returns a `Tile` whose `z` field
  holds the **item id**, so pick it up with `takeGroundItem(tile.z, tile)`. The
  `findAndPickupItems(...)` extension does this for you. `getGroundItems()` returns proper
  `Item` objects (`id`, `amount`, `position`).
- **Player state**: `getMyPlayerPosition()`, `isAnimating()`, `isWalking()`,
  `myPlayerInCombat()`, `getHealthPercent()`, `getPrayer()`, `usingSpec()`, `specPercent()`,
  `getVar(id)`, `getVarBit(id)`, `getLevel(skillId)`, `getXp(skillId)` (skill ids are
  `Skill.X.ordinal`).
- **Definitions**: `TypeLists.OBJ!!.list(itemId)` (items, `.name`), `TypeLists.LOC!!.list(objId)`
  (objects, `.name`, `.actions`, `.varpbitId`), `TypeLists.NPC!!.list(npcId)`. `WorldObject`
  and `Item` expose `getDefinitions()`. These need the cache, so never call them in field
  initializers.
- **Chat and commands**: `chat(text, 0, 0)` speaks, `command("…")` sends a `;;` command,
  `sendText(text)` answers a text-entry dialogue.

## 4. Settings (ConfigurableScript)

Mark the class with `ConfigurableScript` and declare config-item properties. The sidebar builds
a Swing form from them by reflecting over the script's declared fields, in declaration order,
and writes into `item.value` immediately, even while the script runs. Read `item.value` fresh in
`loop()`, never cache it in `onStart()`. `private val` is fine. Enums used by `EnumConfigItem`
may be declared at the top of the same file or in `lib/`.

```kotlin
class Miner : BotScript(), ConfigurableScript {
    val rock = StringConfigItem("Rock", "Object name to mine", "iron")
    val radius = IntConfigItem("Radius", "How far to search, in tiles", 10, min = 1, max = 64)
    val destination = EnumConfigItem("Destination", "Where the ore goes", Destination.entries.toTypedArray(), Destination.BANK)
    val food = OptionsConfigItem("Food", "Item to eat", arrayOf("shark", "rocktail"), "shark")
    val powerMine = BooleanConfigItem("Power mine", "Drop the ore instead of banking", false)
}
```

Item types. All take `name`, `description` and the declared initial value, which is what
"Reset to defaults" restores:

| Item | Renders as |
|------|-----------|
| `BooleanConfigItem(name, description, initialValue)` | checkbox |
| `IntConfigItem(name, description, initialValue, min, max)` | spinner clamped to `min`/`max` |
| `StringConfigItem(name, description, initialValue)` | text field |
| `OptionsConfigItem<T>(name, description, options, initialValue)` | drop-down over `options` |
| `EnumConfigItem<E>(name, description, enumValues, initialValue)` | drop-down over the enum |
| `InfoDisplayConfigItem(name, description, initialValue)` | read-only status line |
| `ConfigSection(name, description, defaultOpen)` | collapsible header, no value |

**Sections.** A `ConfigSection` is a valueless marker: every item declared after it, up to the
next section, renders under a collapsible header. Items declared before the first section stay
at the top level.

```kotlin
val bankSection = ConfigSection("Banking", "What to do when the inventory fills up")
val bankAll = BooleanConfigItem("Bank everything", "Deposit the whole inventory", false)
val debugSection = ConfigSection("Debug", "Diagnostics", defaultOpen = false)
val verbose = BooleanConfigItem("Verbose logging", "Print every decision", false)
```

**Actions.** `withAction(label) { … }` puts a button beside any item. The lambda runs when the
button is pressed and returns the item's new value, or `null` to leave it unchanged. It pairs
naturally with `InfoDisplayConfigItem` to show something the script captured.

```kotlin
import com.darkan.bot.scripts.withAction

val position = InfoDisplayConfigItem("Position", "Where the player is standing")
    .withAction("Capture") { getMyPlayerPosition().let { "${it.x}, ${it.y}, ${it.z}" } }
```

The lambda runs on the UI thread, so keep it to reads and cheap work. Never send packets or
block in it; set a flag that `loop()` acts on instead.

**Conditional visibility.** Implement `ConfigVisibilityProvider` to hide settings that do not
apply to the current choices. It is re-evaluated after every change, so the form updates live.

```kotlin
class Miner : BotScript(), ConfigurableScript, ConfigVisibilityProvider {
    override fun isConfigItemVisible(fieldName: String, item: ConfigItem<*>) =
        fieldName != "bankKeep" || !powerMine.value
}
```

**Change callback.** Declare `fun onConfigUpdated()` (no override; it is found by reflection)
and it runs after every change and after a reset, on the UI thread. Use it to recompute cached
values, not to act in the game.

**Persistence.** Values are remembered per script class name for the session, surviving stop,
restart and **Reload scripts**, so users do not lose settings when scripts recompile. Nothing is
written to disk.

Reacting to a toggle while the script runs is the script's own job, on the game thread.
`Combat.kts` syncs its spec keeper at the top of `loop()`:

```kotlin
private fun syncSpecKeeper() {
    val running = specKeeper
    if (keepSpecOn.value && running == null) {
        specKeeper = KeepSpecOn().also { addParallelScript(it) }
    } else if (!keepSpecOn.value && running != null) {
        removeParallelScript(running)
        specKeeper = null
    }
}
```

## 5. Shared code: the `lib/` folder

Every `.kts` file under `lib/` (recursively) is imported into each script's compilation as a
Kotlin *imported script* (`ScriptCompilationConfiguration.importScripts`), so its top-level
classes, objects, functions and values resolve unqualified from any script anywhere in the
tree. `lib/` is the one folder that is not scanned for scripts.

```kotlin
// lib/KeepSpecOn.kts: a parallel helper, no @ScriptDescription, no trailing expression
class KeepSpecOn : BotScript() {
    override suspend fun loop() {
        if (!usingSpec() && specPercent() >= 25) {
            clickButton(884, 4)
            delayUntil(2000) { usingSpec() }
        }
    }
}

// Combat.kts
class Combat : BotScript() {
    override fun onStart() { addParallelScript(KeepSpecOn()) }
    …
}
```

Rules for `lib/` files:

- Same automatic imports as scripts; the same explicit-import rule for `com.jagex.*`.
- Declarations only. No trailing expression (the loader would instantiate it once per script),
  and no `@ScriptDescription`: helpers are not listed in the sidebar, and `lib/` is never
  scanned for scripts.
- Field initializers in a lib file run when *each* script loads, so the cache rule applies
  here too.
- Names must be unique across `lib/`; two files declaring `class Foo` break every script with
  an ambiguity error. Prefer one file per helper, named after it.
- Only the top-level `lib/` is shared. A nested folder called `lib` anywhere else holds
  ordinary scripts.
- A compile error in any lib file breaks every script. `ScriptCheck` compiles each lib file on
  its own first and reports its errors with `file:line:col`; the same error then repeats under
  every script without a location. Fix `lib/` first.
- The executor keys running scripts by class name, so a helper started by two scripts at
  once runs as one instance (the second start replaces the first). That is the desired
  behaviour for helpers like a spec keeper.
- Prefer a shared helper in `lib/` over copying code between scripts, and prefer plain
  functions or small classes over inheritance hierarchies.
- A parallel helper that not every user wants should be opt-in through a `BooleanConfigItem`
  rather than started unconditionally; see `Combat.kts` and section 4.

## 6. Multi-phase scripts

For anything with more than two or three states, extend `StateMachineScript<Self>` and
declare each state as an `object : State<Self>()` with `checkNext()` (return the next state
or `null` to stay) and `stateLoop()`. See `reference/framework.md` and `Nex.kts`.

## 7. Verification workflow

You cannot run the game client or click in it. Verify in this order:

1. **Read the API you call.** For every `Bot` member used, confirm it in
   `reference/bot-api.md` or the raw `Bot.kt`. Do not invent members.
2. **Compile-check with a real client jar.** The client ships an entry point that compiles a
   folder of `.kts` files exactly as the client does and reports per-file results:

   ```sh
   java -cp "$JAR" com.darkan.bot.scripts.ScriptCheckKt /path/to/this/repo
   ```

   `OK`/`FAIL` per file, a summary line, exit code 1 if anything failed. Compiler
   diagnostics are printed above the `FAIL` line. Find `$JAR` in this order:
   - the jar the launcher installed: `~/.darkanrs/client/darkan-client.jar`
     (`$DARKAN_HOME/client/darkan-client.jar` if `DARKAN_HOME` is set;
     `%USERPROFILE%\.darkanrs\client\darkan-client.jar` on Windows);
   - otherwise download the latest dev build into a temp folder:
     `curl -L -o darkan-client.jar "https://gitlab.com/darkanrs/darkan-bot/-/jobs/artifacts/dev/raw/dist/darkan-client.jar?job=build"`.
   Java 17 or newer is required. If the class is missing (`ClassNotFoundException`), the jar
   predates the checker; download the dev build. Scripts that touch the cache during load
   fail here with an exception in the diagnostics; that is the field-initializer rule above.
3. **Get it into the client's folder.** On a machine where the bot has been enabled at least
   once, the scripts folder (`~/.darkan/scripts`, `%USERPROFILE%\.darkan\scripts`) *is* a
   checkout of this repository on branch `dev` (section 9), so if you are working there the
   file is already in place. Otherwise run the installer from this checkout:
   `./install.sh` (Linux/macOS, POSIX sh) or `powershell -ExecutionPolicy Bypass -File .\install.ps1`
   (Windows). Both copy every root `.kts` and `lib/*.kts` into the detected folder,
   overwriting same-named files; `--clean`/`-Clean` also removes scripts not in the
   repository; `--dest DIR`/`-Dest DIR` or `DARKAN_SCRIPTS_DIR` overrides the folder. When the
   repository itself is the scripts folder the installer says so and does nothing.
4. **Hand over to the user** with exact steps: press *Reload scripts* in the bot sidebar,
   start the script, watch the client console (or the sidebar's logs tab) and paste anything
   printed. Diagnose from their paste; do not guess at runtime behaviour.

## 8. Reviewing a script

Check, in this order: last line returns the instance; `@ScriptDescription` complete and the
category right; no cache/player access in initializers; every click followed by a bounded
wait; no blocking sleeps; `null` from `getClosest…` handled (the `click…` overloads accept
`null` and do nothing, so a missing target must not spin without a `delay`); names lowercase;
interface ids correct; helper classes in `lib/` rather than copied; config values read in `loop()`;
optional parallel helpers behind a `BooleanConfigItem`.

## 9. The scripts folder is a checkout of this repository

When the client starts with the bot enabled (`-Dbot.enabled=true`, the launcher's "Enable
bot" box) it runs `ScriptRepository.sync()` on the scripts folder before loading scripts, and
the sidebar's **Update scripts** button runs the same thing on demand. Source:
https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptRepository.kt

What it does, in order:

- No `.git` in the folder: initialises a repository there, adds `origin` =
  `https://gitlab.com/darkanrs/bot-scripts.git`, fetches, and checks out `dev` tracking
  `origin/dev`. Files already in the folder that collide with repository paths are renamed
  to `<name>.local` (then `.local2`, …) so nothing the player wrote is lost; the sample
  script and README that older clients generated are deleted. Untracked files (a player's
  own scripts) stay untracked and keep loading.
- `.git` present, on branch `dev`, clean tracked files: fetch and fast-forward only.
- On another branch, with modified tracked files, or with local commits not on
  `origin/dev`: fetch only and report why it did not update. Nothing is ever reset,
  stashed or overwritten.
- Offline or no `dev` on the remote yet: reports the failure, scripts load from disk as they
  are.

Flags: `-Dbot.scripts.sync=false` disables the startup sync (the button still works);
`-Dbot.scripts.remote=<url>` points at a fork or a local `file://` repository. With the
launcher these go in "extra JVM args". JGit is bundled, so players do not need git installed.

CLI (same code, for you and for players who prefer a terminal):

```sh
java -cp "$JAR" com.darkan.bot.scripts.ScriptRepositoryKt            # sync ~/.darkan/scripts
java -cp "$JAR" com.darkan.bot.scripts.ScriptRepositoryKt /some/dir  # sync another folder
```

Consequences for you:

- Editing in `~/.darkan/scripts` means editing a git checkout. `git status` shows a player's
  own scripts as untracked; that is the normal state. The startup sync skips a folder with
  uncommitted changes to *tracked* files, so tell the user when you have modified one of the
  repository's own scripts in place and they still want updates.
- To contribute, work on a branch in that checkout (`git switch -c my-script`), push to a fork
  and open a merge request against `dev`; the sync skips the folder while it is on another
  branch and resumes once it is back on `dev`. A Discord upload flow with automated review is
  planned; until then merge requests are the route.
- Scripts and `lib/` files must work from a fresh checkout: no absolute paths, no reliance on
  files outside the repository.

## 10. Style for this repository

Kotlin, 4-space indentation, one script per file, file name equals class name, no comments
except the rare unobvious line, no wildcard imports of your own. Do not commit or push unless
the user asks.
