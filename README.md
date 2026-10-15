# NovaUtils

A client-side utility mod for Minecraft: ESP, Xray, waypoints,
HUD, automation and a clean single-window menu. Everything runs on your side only; nothing has to
be installed on the server.

![Menu](docs/img/menu.png)

**Português:** NovaUtils é um mod de utilidades só do lado do cliente: ESP, Xray,
waypoints, HUD, automações e um menu limpo. Abra o menu com **Shift direito**. Os downloads estão
nas [Releases](https://github.com/Baltazarexe/novautils/releases/latest). Funciona com Sodium/Iris e com Embeddium/Oculus (shaders inclusos).

## Downloads

Grab the jar for your loader and Minecraft version from the
[latest release](https://github.com/Baltazarexe/novautils/releases/latest) (also in [`dist/`](dist)):

| Minecraft | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.3 | `novautils-fabric-0.3.1-mc26.3.jar` | `novautils-neoforge-0.3.1-mc26.3.jar` | |
| 26.2 | `novautils-fabric-0.3.1-mc26.2.jar` | | |
| 26.1.2 | `novautils-fabric-0.3.1-mc26.1.2.jar` | `novautils-neoforge-0.3.1-mc26.1.2.jar` | |
| 1.21.1 | | `novautils-neoforge-0.3.1-beta-mc1.21.1.jar` (beta) | |
| 1.20.1 | | | `novautils-forge-0.3.1-mc1.20.1.jar` |

Fabric needs [Fabric API](https://modrinth.com/mod/fabric-api). Drop the jar in your `mods` folder.

## Using it

- **Right Shift** opens the menu. **Delete** turns every module off (panic key).
- Click a module (or its switch) to toggle it, right-click it to open its settings,
  middle-click it to add it to the **Favorites** tab.
- Two menu styles: the single **Nova** window, or **Classic**, draggable panels per
  category. The button above *Edit HUD* (or next to it, in Classic) switches; your choice is kept.
- Any module can be bound to a key or a mouse button (side buttons too) from its settings.
- **Edit HUD** in the menu lets you drag the HUD elements (coords, armour, minimap, ...) anywhere.
- Settings are saved to `config/novautils.json`.

| Classic menu | Health bars |
|---|---|
| ![Classic menu](docs/img/classic-menu.png) | ![Health bars](docs/img/health-bars.png) |

### Chat commands

Type them in chat; they never reach the server. `.help` lists them.

| Command | |
|---|---|
| `.t <module>` | toggle a module |
| `.bind <module> <key>` | bind a key (`n`, `rshift`, `mouse4`, `none`...) |
| `.set <module> <setting> <value>` | change a setting |
| `.profile save/load/del/list <name>` | save and switch whole setups ("2b2t", "mining"...) |
| `.config export` / `.config import` | copy your config to the clipboard / load it from there |
| `.wp add/del/list <name>` | waypoints |
| `.search add <block> [hex] / del / list / clear` | your own blocks for Search |
| `.friend add/del/list <name>` | friends |
| `.find <item>` | which chest you left it in (ChestTracker) |
| `.echest` | what's in your ender chest |
| `.sort` | sort the open chest or your inventory |
| `.drop add/del/list <item>` | extra items for AutoDrop |
| `.seed <n|2b2t>` | set the world seed for `.locate` and slime chunks |
| `.locate <structure> [n]` | nearest structures from the seed, with a waypoint |
| `.wp add <name> [x y z] / del / list / clear` | waypoints, by coordinates or where you stand |
| `.coords`, `.seed <seed>`, `.panic` | copy coordinates, slime-chunk seed, everything off |

## Compatibility

- **Sodium + Iris** (Fabric) and **Embeddium + Oculus** (Forge 1.20.1): Xray works with both
  renderers, and ESP, tracers, waypoints and labels keep working with a shaderpack on.
- Tested alongside JEI/EMI, FancyMenu and large modpacks.

| Xray with a shaderpack (Forge 1.20.1, Embeddium + Oculus) | ESP, nametags, storage ESP and HUD |
|---|---|
| ![Xray](docs/img/xray-shaders.png) | ![ESP](docs/img/esp-hud.png) |

![ShulkerPeek](docs/img/shulker-peek.png)

## Modules

### Render
| Module | What it does |
|---|---|
| Fullbright | Makes everything fully lit, no more caves or night. |
| Xray | Hides everything but the ores you pick (see-through), or boxes them. |
| Esp | Highlights entities through walls (chams / box). |
| StorageEsp | Highlights chests, shulkers, barrels and more through walls. |
| Search | Finds spawners, portals, beacons and other rare blocks, with an alert and a counter. Add your own blocks with `.search add <block> [hex]`. |
| BlockHighlight | Outlines the block you are looking at (rainbow option). |
| TargetHud | Name, health, armour and distance of the creature you look at (works past survival reach). |
| Compass | A heading bar with your waypoints on it. |
| GhostHand | Right-click chests, levers, doors and villagers through walls (never players; off while sneaking). |
| Tracers | Lines from your crosshair to nearby entities. |
| Nametags | Better name tags: health, ping, distance, totem pops. |
| Waypoints | Named markers with distance, visible through walls. |
| Minimap | A small map with you, mobs, players and waypoints. |
| ShulkerPeek | Hover a shulker box to see what's inside. |
| BreakIndicator | Shows the progress of blocks being broken, yours and other players'. |
| HitsToKill | A mob's health and how many hits it still needs. |
| HealthBars | Health bars over mobs and/or players (filter: mobs, players or both; hostile only). |
| Esp styles | CORNERS, 2D box, 3D box, wireframe, CHAMS (through-wall model, solid/textured/rainbow) or glow. |
| FallPredict | Where you'll land and how much fall damage it will do. |
| ChunkBorders | Chunk outlines and slime chunks. |
| Trajectories | Where pearls, arrows and potions will land. |
| NewChunks | New vs already-loaded chunks (find trails). |
| LightOverlay | Where mobs can spawn, and the best spots for torches. |
| Freecam | Fly the camera freely while your body stays put. |
| Zoom | Zoom in, scroll to zoom further. |
| ViewModel | Size, position and tilt of your hands and items, enchant glint. |
| NoRender | Hides the fire overlay, hurt camera shake and the totem animation. |
| Trail, Breadcrumbs, LogoutSpots, ItemHighlight, HoleEsp, TunnelEsp, VoidEsp | Smaller visual helpers. |
| TimeChanger, WeatherChanger | Change the time and weather you see. |

### Movement
| Module | What it does |
|---|---|
| ElytraFly | Elytra flight: control, boost, 2b2t bounce, auto rockets. |
| BoatFly | Fly with a boat. |
| Flight, Speed, Step, HighJump, LongJump, AirJump | Classic movement. |
| Jesus, Spider, FastClimb | Walk on water, climb walls, faster ladders. |
| Scaffold, SafeWalk, Parkour, Sneak | Building and edge helpers. |
| AutoSprint, AutoWalk, AutoJump | Less key holding. |
| NoFall, NoSlow, Velocity, AntiVoid | Fall damage, item slowdown, knockback, void. |
| InventoryMove | Walk around with menus open. |

### Combat (mobs only)
| Module | What it does |
|---|---|
| AutoFight | Attacks nearby hostile mobs (and neutral ones only when they're angry at you). |
| Criticals | Every hit on a mob is a critical hit. |
| AutoTotem, AutoArmor, Surround, AutoLog | Defensive helpers. |

### Player
| Module | What it does |
|---|---|
| PacketMine | Click a block to mine it in the background; queue several. |
| InventorySort | Sorts the open chest or your inventory (its key works inside chests). |
| AutoDrop | Throws away junk blocks and items. |
| AntiHunger | Sprint without the server charging sprint hunger. |
| AutoTool | Picks the fastest tool, counting Efficiency. Silent mode swaps only while mining, so your hand never shows it. |
| AutoEat, AutoReplenish, AutoMend, ElytraReplace, ChestSwap | Inventory upkeep. |
| FastUse, FastBreak, AutoClicker | Faster using and mining. |

### World
| Module | What it does |
|---|---|
| AutoFarm | Harvests ripe crops and replants them. |
| AutoSmelt | Fills furnaces with ores/food and fuel, takes the output. |
| AutoCompact | Crafts ingots, gems etc. into blocks at a crafting table. |
| AutoTunnel | Digs a straight 1x2 or 3x3 tunnel, stops at lava and drops, places torches. |
| AutoBuild | Builds a platform, wall, bridge or shelter from your hotbar blocks. |
| AirPlace | Place blocks in the air where you look. |
| Nuker | Mines the blocks around you (flatten, ores only, ...). |
| AutoFish, AutoBreed, AutoShearer, AutoNametag | Farm chores. |
| Timer | Changes how fast the game ticks. |

### Misc
| Module | What it does |
|---|---|
| HudInfo, ArmorHud, PotionHud, InventoryHUD, KeyStrokes, TotemCounter, ActiveModules | HUD elements. |
| Notifier | Alerts for players in view, totem pops, joins, full inventory, low food. |
| DurabilityAlert | Warns before armour, elytra or tools break. |
| PlayerRadar | Nearby players and their distance. |
| BetterChat | Timestamps, anti-spam stacking, longer history. |
| DeathPoint | Marks where you died. |
| ChestTracker | Remembers what you saw in every chest; `.find` tells you where something is. |
| AutoCommand | Runs a chat command on a timer (e.g. /rtp every 20s), stops after N runs. |
| Accounts | "Accounts" button on the multiplayer screen: switch to an offline nick (offline-mode servers) and back, no restart. |
| Friends | Middle-click a player to add them as a friend. |
| AutoRespawn, AutoReconnect, AntiAfk | Quality of life. |

## Fair play

NovaUtils deliberately has **no automation that targets other players**: AutoFight and Criticals
only ever work on mobs, and there is no reach or PvP aura. Movement and automation modules can
still get you kicked or banned on servers with anti-cheat; use them where they're allowed.

## Building

Needs JDK 25 (the 26.x builds) and JDK 21 (the 1.20.1 build compiles with it for Java 17).

```
./gradlew :fabric:build :fabric261:build :fabric262:build :neoforge:build :neoforge261:build :neoforge1211:build :forge1201:build
```

- `common/` holds the code shared by the 26.x Fabric and NeoForge builds.
- `forge1201/` is the Forge 1.20.1 port, with its own source tree.
- In-game tests: `./gradlew :fabric:runClientGameTest` (26.3), `:fabric261:runClientGameTest`
  (26.1.2, add `-PwithSodium` for Sodium + Iris), `:fabric262:runClientGameTest` (26.2), `:forge1201:runSelftest` (1.20.1, add
  `-PwithEmbeddium` for Embeddium + Oculus), `:neoforge1211:runSelftest` (1.21.1, `-PwithSodium` for Sodium).

## Contact

Questions, bugs or ideas: Discord **baltazar_exe** (or open an issue here).

**Contato:** dúvidas, bugs ou sugestões, me chama no Discord **baltazar_exe**.
