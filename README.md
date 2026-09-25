# Darkan Bot Scripts

Public KotlinScript (`.kts`) scripts for the Darkan client's bot feature. They only work on
worlds that allow botting.

## Installing

Nothing to do: when the client starts with the bot enabled it turns the scripts folder into a
checkout of this repository (branch `dev`) and fast-forwards it on every later launch. Press
**Update scripts** in the bot sidebar to fetch the latest at any time, and **Reload scripts** to
recompile after editing. Your own scripts in the folder are left alone; if a file of yours has the
same name as one in the repository it is kept as `<name>.local`.

| OS            | Scripts folder                     |
|---------------|------------------------------------|
| Linux / macOS | `~/.darkan/scripts/`               |
| Windows       | `%USERPROFILE%\.darkan\scripts\`   |

Manual alternatives:

```sh
git clone https://gitlab.com/darkanrs/bot-scripts.git ~/.darkan/scripts   # a plain clone works too
./install.sh                     # from a checkout elsewhere: copy scripts + lib/ into the folder (Linux / macOS)
.\install.ps1                    # same on Windows (PowerShell)
java -cp ~/.darkanrs/client/darkan-client.jar com.darkan.bot.scripts.ScriptRepositoryKt   # the client's sync, from a terminal
```

The sync never resets, stashes or overwrites: with local changes to the repository's own files,
on another branch, or with local commits it only fetches and tells you why it stopped. Launch flags:
`-Dbot.scripts.sync=false` turns the startup sync off, `-Dbot.scripts.remote=<url>` points it at a
fork (both go in the launcher's extra JVM args).

Every `.kts` file in the folder is compiled on reload, so edits take effect without a restart.
Compiled scripts (`.jar` / `.class`) are also picked up from the same folder.

## Writing a script

A script is a class extending `BotScript`, annotated with `@ScriptDescription`, and the file
must end with an expression that returns an instance of it. The Bot API, the script helpers,
the event classes and `com.darkan.bot.utils.*` are imported automatically.

```kotlin
@ScriptDescription(
    name = "Simple Woodcutter",
    version = "1.0",
    author = "Me",
    description = "Cuts trees and banks logs",
    category = ScriptCategory.WOODCUTTING
)
class SimpleWoodcutter : BotScript() {
    override suspend fun loop() {
        if (inventory.freeSlots() <= 0) {
            openClosestBank()
            delayUntil(5000) { interfaceOpen(762) }
            bankAll()
        } else {
            if (isAnimating()) return
            clickClosestObject("tree", "chop down")
            delayUntil(2000) { isAnimating() }
        }
    }
}

SimpleWoodcutter()
```

`loop()` runs continuously while the script is active. `onStart()`, `onStop()`, `onEvent(event)`
and `render()` are optional overrides.

### Shared code

Files under `lib/` are compiled into every script, so anything more than one script needs (a
parallel helper like `lib/KeepSpecOn.kts`, an enum, an extension function) lives there once and is
used unqualified. Lib files hold declarations only: no `@ScriptDescription`, no trailing expression.

### Settings

Implement `ConfigurableScript` and declare config-item properties. The sidebar builds a settings
form from them, and changes apply immediately, even while the script is running:
`StringConfigItem`, `IntConfigItem`, `BooleanConfigItem`, `OptionsConfigItem`, `EnumConfigItem`,
`InfoDisplayConfigItem` for a read-only status line, and `ConfigSection` to group what follows it
under a collapsible header. `withAction("Label") { … }` adds a button beside any item,
`ConfigVisibilityProvider` hides settings that do not apply, and a `fun onConfigUpdated()` is
called after every change. Settings are remembered for the session, including across reloads.

### Useful API

- **Movement**: `walkTo(tile)`, `getDistanceTo(...)`, `isWalking()`
- **Inventory**: `inventory.contains(name, amount)`, `inventory.freeSlots()`, `clickItem(name, option)`
- **Banking**: `openClosestBank()`, `bankAll()`, `depositAllExcept(ids)`, `withdrawAllOfItem(name)`
- **Objects**: `clickClosestObject(name, action)`, `clickObject(id, tile, option)`
- **NPCs**: `clickNPC(npc, option)`, `getClosestNPC(id / name)`
- **Ground items**: `findAndPickupItems(names)`, `takeGroundItem(item)`
- **Interfaces**: `interfaceOpen(id)`, `clickButton(interface, component)`, `clickDialogue(interface, component)`
- **Skills**: `getXp(skillId)`, `getLevel(skillId)`, `getVarBit(id)`
- **Waiting**: `delay(ms)`, `delay(mean, variance)`, `delayUntil(timeout) { }`, `delayWhile { }`, `waitThenDelayUntil(wait, timeout) { }`
- **Events**: `waitForXPDrop(skill)`, `waitForChatContaining(type, text)`, `waitForEvent { }`
- **Composition**: `addParallelScript(script)` runs a helper script alongside; `StateMachineScript` with `State` objects for multi-phase scripts

Compilation errors are printed to the client console and the sidebar's logs tab. Set
`visible = false` in `@ScriptDescription` to keep a work-in-progress script out of the list.

## Checking a script without the client

The client jar can compile a folder of scripts and report problems without opening the game:

```sh
java -cp ~/.darkanrs/client/darkan-client.jar com.darkan.bot.scripts.ScriptCheckKt .
```

The launcher keeps the jar at `~/.darkanrs/client/darkan-client.jar`
(`%USERPROFILE%\.darkanrs\client\` on Windows); the latest dev build is at
https://gitlab.com/darkanrs/darkan-bot/-/jobs/artifacts/dev/raw/dist/darkan-client.jar?job=build.

## Developing with Claude Code

The repository ships a `darkan-script-dev` skill and a `CLAUDE.md`. A Claude Code session opened in
this folder reads the client's API straight from GitLab, compile-checks scripts with the jar above and
installs them with the installer, so the client source does not need to be cloned.

## Contributing

Scripts are welcome. The route today:

1. Work in your scripts folder, which is already a checkout: `git switch -c my-script`, add your
   `.kts` (file name equal to the class name, every `@ScriptDescription` field filled in, shared code
   in `lib/`).
2. Check it compiles: `java -cp ~/.darkanrs/client/darkan-client.jar com.darkan.bot.scripts.ScriptCheckKt ~/.darkan/scripts`.
3. Push the branch to a fork of https://gitlab.com/darkanrs/bot-scripts and open a merge request
   against `dev`. Switch back to `dev` afterwards so the client keeps updating your folder.

Coming next: upload a `.kts` in the Darkan Discord, an automated review checks it for anything
unsafe, and approved scripts land in this repository for everyone's next update.
