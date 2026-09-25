# Darkan Bot Scripts

Public KotlinScript (`.kts`) scripts for the bot feature of the Darkan RuneScape client. Players
install these into the client's scripts folder; the client compiles them at runtime.

**Before writing or changing any script, load the `darkan-script-dev` skill**
(`.claude/skills/darkan-script-dev/SKILL.md`). It carries the script contract, the runtime model,
the full Bot API reference and the verification workflow. Everything below is the short form.

## Repository layout

- `*.kts` in the root: one script per file. The client scans only the root for scripts.
- `lib/*.kts`: shared code compiled into every script (parallel helpers such as `KeepSpecOn`,
  shared enums, extension functions). Declarations only, no trailing expression.
- `install.sh` / `install.ps1`: copy every root `.kts` and `lib/*.kts` into the client's
  scripts folder, for checkouts that live somewhere else.
- `.claude/skills/darkan-script-dev/`: the skill and API reference.
- `README.md`: player-facing instructions.

## The client is not here

This repository contains scripts only. The client that runs them is a separate public GitLab
project, and you must not assume it is cloned locally. Read its API from GitLab raw URLs:

| What | URL |
|------|-----|
| `Bot` object (the game API) | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/Bot.kt |
| `Script` base, `ScriptDescription`, `ScriptCategory` | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/Script.kt |
| `BotScript` template and default imports | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/KotlinScriptLoader.kt |
| Config items (`StringConfigItem`, ...) | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptConfig.kt |
| `StateMachineScript` / `State` | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/StateMachineScript.kt |
| Script extension helpers | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/API.kt |
| Scripts-folder sync (startup, Update button, CLI) | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/scripts/ScriptRepository.kt |
| Events | https://gitlab.com/darkanrs/darkan-bot/-/tree/dev/src/main/kotlin/com/darkan/bot/scripts/event |
| Utils (`Tile`, `Skill`, `Item`, `WorldObject`, `ContainerWrapper`, `Area`) | https://gitlab.com/darkanrs/darkan-bot/-/tree/dev/src/main/kotlin/com/darkan/bot/utils |
| Top-level helpers (`random`, `gaussian`, formatting) | https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/Util.kt |
| Browse the whole client | https://gitlab.com/darkanrs/darkan-bot/-/tree/dev/src/main/kotlin |

Any other client file follows the same pattern:
`https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/<package path>/<File>.kt`.
The skill's `reference/bot-api.md` is a snapshot of the signatures; when a call is not there or
looks different, fetch the raw file and trust the source.

## Script contract (short form)

```kotlin
@ScriptDescription(name = "…", version = "1.0", author = "…", description = "…", category = ScriptCategory.OTHER)
class MyScript : BotScript() {
    override suspend fun loop() { /* runs repeatedly while the script is active */ }
}

MyScript()
```

- The file must end with an expression producing the script instance; that value is what the
  client registers.
- `BotScript` extends `Script`. `com.darkan.bot.Bot.*`, `com.darkan.bot.scripts.*`,
  `com.darkan.bot.scripts.event.*`, `com.darkan.bot.utils.*` and `kotlinx.coroutines.*` are
  imported automatically; anything under `com.jagex.*` needs an explicit `import`.
- The client instantiates the script when it loads the file, before the game cache may be ready.
  Never touch `TypeLists`, the player or the scene in a field initializer; use `by lazy` or read
  it inside `loop()`.
- Only `suspend` waits (`delay`, `delayUntil`, `delayWhile`, `waitFor*`). Never `Thread.sleep`
  or busy-loop: scripts run as coroutines on the game pulse.
- Script files cannot see each other's classes, but everything under `lib/` is visible to every
  script. Shared helpers go there, never copied between scripts.

## Workflow

1. Write or edit the `.kts` in the repository root. File name equals the script's class name.
2. Compile-check: with a client jar available run
   `java -cp <darkan-client.jar> com.darkan.bot.scripts.ScriptCheckKt <dir>` (see the skill for
   where the jar comes from). Fix every `FAIL`.
3. Get it into the client's folder. When the bot is enabled the client itself keeps
   `~/.darkan/scripts` as a checkout of this repository's `dev` branch (JGit, fast-forward only,
   never touches local changes), so a checkout there needs nothing. From any other checkout run
   `./install.sh` (Linux/macOS) or `.\install.ps1` (Windows); both overwrite same-named files.
4. Ask the user to press **Reload scripts** in the bot sidebar and to report the console output;
   you cannot drive the game client yourself.

## Conventions

- Kotlin, 4-space indent, no wildcard imports beyond the automatic ones.
- Names and structure carry the meaning; keep comments to the rare unobvious line.
- Fill in every `@ScriptDescription` field and pick the matching `ScriptCategory`.
- Item and NPC name matching in the API is lowercase substring matching; pass lowercase names.
- Do not commit or push unless the user asks.
