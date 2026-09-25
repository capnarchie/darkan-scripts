# Bot API snapshot

Every member below is on `object Bot` (`com.darkan.bot.Bot`) unless stated otherwise, and is
callable unqualified from a script thanks to the automatic `com.darkan.bot.Bot.*` import.
Snapshot taken from branch `dev` on 2026-09-15. The live file is the authority:

https://gitlab.com/darkanrs/darkan-bot/-/raw/dev/src/main/kotlin/com/darkan/bot/Bot.kt

Types referenced: `Tile`, `WorldObject`, `Item`, `ContainerWrapper`, `Skill`, `Area`, `Utils`
from `com.darkan.bot.utils` (auto-imported, listed at the end);
`NpcEntity` (`com.jagex.game.runetek5.entity.pathingentity.npc.NpcEntity`),
`PlayerEntity` (`…pathingentity.player.PlayerEntity`), `PathingEntity`
(`…pathingentity.PathingEntity`), `RouteStrategy` (`…movement.pathfinding.RouteStrategy`)
and `TypeLists` (`com.jagex.game.runetek5.config.TypeLists`) need explicit imports when a
script names them.

## State fields

```kotlin
var inventory: ContainerWrapper          // live inventory (interface 679)
var bank: ContainerWrapper               // live bank contents while the bank is open
var username: String; var password: String
var lastAnimated: Long                   // millis of the last observed animation start
val optionsDialogue: Array<String?>      // the up-to-5 options of the open options dialogue
var disableInterfaces: Boolean; var disableIncomingPackets: Boolean
```

## Player

```kotlin
fun getMyPlayer(): PlayerEntity
fun getMyPlayerPosition(): Tile          // absolute world tile with plane
fun getMyPlayerX(): Int; fun getMyPlayerY(): Int
fun getBaseX(): Int; fun getBaseY(): Int // loaded map region origin (absolute coords = local + base)
fun getCurrentAnimation(): Int           // -1 when idle
fun isAnimating(): Boolean
fun isWalking(): Boolean
fun refreshLastAnimated()
fun getTimeSinceLastAnimation(): Long
fun hasntAnimatedFor(time: Int): Boolean
fun myPlayerInCombat(): Boolean
fun inCombat(entity: PathingEntity): Boolean
fun facingMe(entity: PathingEntity): Boolean
fun getFacing(entity: PathingEntity): PathingEntity?
fun getHitbarValue(entity: PathingEntity?, hitbarId: Int): Int
fun getHealth(): Double                  // varbit 7198, current life points
fun getHealthPercent(): Double
fun getPrayer(): Double                  // var 2382
fun usingSpec(): Boolean                 // var 301 == 1
fun specPercent(): Double                // var 300 / 10
fun getVar(varId: Int): Int
fun getVarBit(varBit: Int): Int
fun setRunning(on: Boolean)
fun isLoggedOut(): Boolean
fun sendLogout(lobby: Boolean)
fun loginLobby(un: String, pw: String); fun loginWorld()
fun getPid(entity: PathingEntity): Int
```

## Skills and rates

```kotlin
fun getXp(skillId: Int): Int             // skillId = Skill.X.ordinal
fun getLevel(skillId: Int): Int          // visible (boosted) level
fun getXPForLevel(level: Int): Int
fun getXPToLevel(skillId: Int, level: Int): Int
fun getXPToNextLevel(skillId: Int): Int
fun getXpPerHour(skillId: Int, startingXp: Int, startTime: Long): Int
fun getFormattedXpPerHour(skillId: Int, startingXp: Int, startTime: Long): String
fun getTimeToLevel(skillId: Int, startXP: Int, startTime: Long, level: Int): String
fun getLootPerHour(itemAmount: Int, startingAmount: Int, startTime: Long): Int
fun getFormattedLootPerHour(itemAmount: Int, startingAmount: Int, startTime: Long): String
fun getScriptTime(currTime: Long, startTime: Long): String       // HH:MM:SS
fun millisElapsed(startTime: Long): Long; fun secondsElapsed(startTime: Long): Float
fun minutesElapsed(startTime: Long): Float; fun hoursElapsed(startTime: Long): Float
fun format(number: Int): String
```

## Movement and distance

```kotlin
fun walkTo(tile: Tile)                                            // absolute tile; sends the walk packet and computes the route
fun getDistanceTo(tile: Tile): Int                                // route length, -1 if unreachable
fun getDistanceTo(npc: NpcEntity): Int
fun getDistanceTo(obj: WorldObject): Int                          // honours object shape/size/rotation
fun getDistanceTo(strategy: RouteStrategy, findAlternative: Boolean = false): Int
```

`Utils.distance(a: Tile, b: Tile)` is the straight-line alternative.

## Interfaces and buttons

```kotlin
fun interfaceOpen(id: Int): Boolean
fun bankIsOpen(): Boolean                                         // interfaceOpen(762)
fun closeInterfaces()
fun closeMainInterface()
fun clickButton(interfaceId: Int, componentId: Int)               // clickType 1
fun clickButton(interfaceId: Int, componentId: Int, clickType: Int)
fun clickButton(interfaceId: Int, componentId: Int, slotId: Int, clickType: Int)
fun clickButton(interfaceId: Int, componentId: Int, slotId: Int, slotId2: Int, clickType: Int)
fun clickDialogue(id: Int)                                        // IF_CONTINUE on a raw hash
fun clickDialogue(interfaceId: Int, componentId: Int)
fun continueDialogue()                                            // clickDialogue(1184, 18)
fun clickDialogueOption(option: Int)                              // 1-based option of the options dialogue
fun clickDialogueOption(option: String)                           // by option text (substring, case-insensitive)
fun clickSkillDialogue(option: Int)                               // make-x interface 905, component option + 13
fun sendText(text: String)                                        // answer a text-entry dialogue
fun activateQuickprayers()                                        // clickButton(749, 4)
fun interfaceOnInterface(fromIf: Int, toIf: Int, fromSlot: Int, toSlot: Int, fromItem: Int, toItem: Int)
fun buy500ShopItem(slot: Int)
```

`clickType` values used by `clickButton` map to the option index of the component's menu
(1 = first option, 2 = second, …). Interface ids: bank 762, deposit box 11, inventory 679,
make-x/skill dialogue 905, chat continue 1184, quick prayers 749, special attack 884.

## Inventory and items

```kotlin
fun clickItem(itemId: Int)                                        // option 1 on the first matching slot
fun clickItem(itemId: Int, slot: Int)
fun clickItem(itemId: Int, slot: Int, option: Int)
fun clickItem(name: String)                                       // lowercase substring match
fun clickItem(name: String, option: Int)
fun equipItem(itemId: Int, slot: Int)                             // option 2
fun dropItem(itemId: Int, slot: Int)                              // option 8
fun dropAll(vararg ids: Int)
fun dropAllExcept(vararg itemNames: String)                       // substring
fun dropAllExceptExact(vararg itemNames: String)                  // exact name
fun dropWholeInventory()
fun itemOnItem(item1: String, item2: String)
fun itemOnItem(itemId: Int, item: String)
fun itemOnItem(itemId: Int, itemId2: Int)
fun itemOnItem(fromItem: Int, fromSlot: Int, toItem: Int, toSlot: Int)
fun alch(slot: Int)                                               // high alchemy on an inventory slot
fun sendItemOnObject(item: String, obj: WorldObject)
fun sendItemOnObject(item: String, objectId: Int)                 // closest object with that id
fun sendItemOnObject(item: String, objectName: String)
fun sendItemOnObject(itemId: Int, objectId: Int, x: Int, y: Int)
```

`inventory` / `bank` (`ContainerWrapper`):

```kotlin
fun contains(name: String, amount: Int): Boolean      // name is lowercase substring; total amount >= amount
fun contains(itemId: Int, amount: Int): Boolean
fun getSlotByItem(name: String): Int                  // -1 when absent
fun getSlotByItem(itemId: Int): Int
fun getSlotByItemOfGreaterAmount(name: String, minAmount: Int): Int
fun getItem(slot: Int): Int                           // item id, -1 for empty
fun getAmount(slot: Int): Int
fun getItemId(name: String): Int
fun numberOf(itemId: Int): Int; fun numberOf(string: String): Int
fun freeSlots(): Int
val container: ItemContainer                          // raw: container.itemIds, container.amounts
```

## Banking

```kotlin
fun openClosestBank(): Boolean                        // clicks the nearest bank booth/chest/banker; true if something was clicked
fun openClosestDepositBox(): Boolean
fun bankAll()                                         // deposit whole inventory (bank must be open)
fun bankAllExcept(itemId: Int)
fun bankAllInDepositBox()
fun bankEquipment()
fun depositItem(itemId: Int)                          // deposit 1
fun depositAllOfItem(itemId: Int); fun depositAllOfItem(itemName: String)
fun depositAllOfItemBySlot(slot: Int)
fun depositAllExcept(vararg itemIds: Int)
fun withdrawOneItem(itemId: Int); fun withdrawOneItem(name: String)
fun withdraw5Item(itemId: Int); fun withdraw5Item(name: String)
fun withdraw10Item(itemId: Int); fun withdraw10Item(name: String)
fun withdrawCustomItem(itemId: Int); fun withdrawCustomItem(name: String)   // the bank's "X" amount
fun withdrawAllButOneItem(itemId: Int); fun withdrawAllButOneItem(name: String)
fun withdrawAllOfItem(itemId: Int); fun withdrawAllOfItem(name: String)
```

Bank operations require `interfaceOpen(762)`; poll for it after `openClosestBank()`.

## Objects (locs)

```kotlin
fun getNearbyObjects(): List<WorldObject>                          // every interactive object on the player's plane in the loaded region
fun getObjectsNearby(id: Int): List<WorldObject>
fun getObjectsNearby(id: String): List<WorldObject>                // name substring, case-insensitive
fun getObjectsNearbyExact(id: String): List<WorldObject>
fun getObjectsNearby(id: Int, option: String): List<WorldObject>
fun getObjectsNearby(id: String, option: String): List<WorldObject>
fun getNearbyObjectsWithOption(option: String): List<WorldObject>
fun getObjectAt(tile: Tile): WorldObject?
fun getClosestObject(id: Int): WorldObject?                        // by route distance; null if none reachable
fun getClosestObject(vararg ids: Int): WorldObject?
fun getClosestObject(id: String): WorldObject?
fun getClosestObject(id: Int, option: String): WorldObject?
fun getClosestObject(id: String, option: String): WorldObject?
fun getClosestObjectExact(id: String): WorldObject?
fun getClosestObjectWithOption(option: String): WorldObject?
fun getClosestObjectNoClip(id: String): WorldObject?               // straight-line distance
fun clickObject(obj: WorldObject?, action: String)                 // null-safe; option looked up by text
fun clickObject(obj: WorldObject?, option: Int)
fun clickObject(obj: WorldObject?)                                 // option 2
fun clickObject(objectId: Int, tile: Tile, option: Int, walk: Boolean = true)
fun clickClosestObject(name: String, action: String)
fun clickClosestObject(name: String, action: Int)
fun clickClosestObject(name: String)
fun clickClosestObject(name: Int, action: String)
fun clickClosestObject(name: Int, action: Int)
fun clickClosestObject(name: IntArray, action: Int)
fun clickClosestObject(name: Int)
fun clickObjectWithNearestOption(name: String, optionRequired: String, action: String)
```

`WorldObject : Tile` exposes `id`, `rotation`, `type`, `x`, `y`, `z`, `containsOption(option): Int`
(1-based index or -1) and `getDefinitions(): ObjectType` (`name`, `actions`, `varpbitId`, …).

## NPCs

```kotlin
fun getNPCs(): List<NpcEntity>
fun getFilteredNPCs(id: Int): List<NpcEntity>
fun getFilteredNPCs(id: String): List<NpcEntity>                   // exact name, case-insensitive
fun getFilteredNPCsContaining(id: String): List<NpcEntity>         // substring
fun getFilteredNPCsByOption(id: String): List<NpcEntity>
fun getClosestNPC(id: Int): NpcEntity?                             // route distance
fun getClosestNPC(id: String): NpcEntity?
fun getClosestNPCWithOption(option: String): NpcEntity?
fun getClosestNPCNoClip(id: Int): NpcEntity?                       // straight-line
fun getClosestNPCNoClip(id: String): NpcEntity?
fun getClosestNPCNoClipContaining(id: String): NpcEntity?
fun getClosestOutOfCombatNPC(id: Int): NpcEntity?
fun getClosestOutOfCombatNPC(id: String): NpcEntity?
fun getClosestOutOfCombatNPCContaining(id: String): NpcEntity?
fun getNPCByIndex(index: Int): NpcEntity?
fun getNPCByIndexExcluding(index: Int, exclude: Int): NpcEntity?
fun getNpcIndexById(id: Int): Int; fun getNpcIndexByName(name: String): Int
fun clickNPC(npc: NpcEntity?, option: Int)                         // null-safe
fun clickNPC(npc: NpcEntity?, option: String)
fun clickNpc(name: String, option: Int)                            // closest by exact name
fun clickNpc(npcId: Int, option: Int)
fun clickNPCWithNearestOption(name: String, optionRequired: String, action: String)
fun npcOption(index: Int, option: Int)
```

`NpcEntity.definitions!!` gives `name`, `actions`, `type` (the NPC id), `combatLevel`;
`npc.endX`/`npc.endZ` are local coordinates (add `getBaseX()`/`getBaseY()`).

## Players

```kotlin
fun getPlayer(username: String): PlayerEntity?
fun getPlayerPid(username: String): Int
fun playerInList(username: String): Boolean
fun playerOption(username: String, option: Int)
fun tradePlayer(username: String)                                  // playerOption(username, 4)
fun addResponsePlayer(username: String); fun removeResponsePlayer(username: String); fun clearResponsePlayers()
val responsePlayers: MutableList<String>
```

## Ground items

```kotlin
fun getGroundItems(): List<Item>                                   // on the player's plane, loaded region
fun getClosestGroundItem(): Item?
fun getNearbyGroundItemsWithIds(vararg ids: Int): List<Item>
fun findGroundItem(id: String, distance: Int = 15): Tile?          // NOTE: returned Tile.z is the ITEM ID
fun findGroundItem(id: Int, distance: Int = 10): Tile?
fun takeGroundItem(item: Item)
fun takeGroundItem(itemId: Int, tile: Tile)                        // pair with findGroundItem: takeGroundItem(t.z, t)
fun clickGroundItem(item: Item, op: Int)                           // op 3 = take
```

`Item` has `id`, `amount`, `position: Tile`, `getName()`, `getDefinitions()`.

## Chat and misc

```kotlin
fun chat(message: String, color: Int, effect: Int)
fun command(message: String)                                       // sends ";;message"
fun sendClick()                                                    // anti-idle click packet
fun handleConsoleCommand(cmd: Array<String>)                       // client console, not for scripts
```

## Utils (`com.darkan.bot.utils`, auto-imported)

```kotlin
open class Tile(var x: Int, var y: Int, var z: Int = 0) { fun equals(x: Int, y: Int): Boolean }
class Area(var bottomLeft: Tile, var topRight: Tile) { fun within(tile: Tile): Boolean }
class Item(id, amount, position) / Item(id, position)
open class WorldObject : Tile
class ContainerWrapper(var container: ItemContainer)
enum class Skill { ATTACK, DEFENSE, STRENGTH, CONSTITUTION, RANGED, PRAYER, MAGIC, COOKING, WOODCUTTING,
    FLETCHING, FISHING, FIREMAKING, CRAFTING, SMITHING, MINING, HERBLORE, AGILITY, THIEVING, SLAYER,
    FARMING, RUNECRAFTING, HUNTER, CONSTRUCTION, SUMMONING, DUNGEONEERING }        // ordinal == skill id
object Utils {
    fun distance(t1: Tile, t2: Tile): Int
    fun random(maxValue: Int): Int; fun random(min: Int, max: Int): Int; fun random(min: Double, max: Double): Double
    fun getRandom(maxValue: Int): Int
    fun stringContainsIgnoreCase(options: Array<String?>?, option: String): Int   // 1-based index or -1
}
```

Sources: https://gitlab.com/darkanrs/darkan-bot/-/tree/dev/src/main/kotlin/com/darkan/bot/utils
