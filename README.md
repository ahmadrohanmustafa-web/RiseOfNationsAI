# Rise of Nations: AI Civilization (Minecraft 26.3, Fabric)

A story-driven civilization campaign mod. **Early development.** This branch is a clean rebuild; the old
1.21.11 mod is kept untouched in the `mc-1.21.11` copy/branch.

## Status — be honest about what exists

| Area | State |
|---|---|
| Build for Minecraft 26.3 (Java 25, Fabric, Loom) | Compiles and launches (confirmed by the project owner on GitHub Actions + the game) |
| Found a civilization (name, leader, color, government) with validation | **New in phase 1.** Logic tested; screen **not yet run in-game** |
| Civilization overview screen (name, leader, color, government, treasury, population, settlements, stage, economy) | **New.** Not yet run in-game |
| Rename civilization | **New.** Logic tested; screen not yet run in-game |
| Saving with the world (vanilla saved data, schema version 2, refuses newer data) | **New.** Format tested; the Minecraft hookup is not yet run in-game |
| Server-side validation and rate limiting of every create/rename request | **New.** Logic tested |
| `/ron` and `/ron civilization` open the screen; `/ron about` unchanged | **New / kept.** Not yet run in-game |
| Money ledger, wages with cooldown and daily cap | Done and tested (earlier phase). Wages are not connected to gameplay yet |
| Story engine, chapters 1–3 | Done and tested (earlier phase). **Not connected to gameplay events yet** |
| **Capital settlement**: `/ron settlement`, `/ron settlement create`, a small physical marker, saved with the world (schema 3) | **New in phase 2.** Logic tested; world placement and commands **not yet run in-game** |
| Overview screen shows the capital (status, name, location, dimension, level, founded vs simulated settlements, marker status) | **New in phase 2.** Not yet run in-game |
| Keybinding to open the screen | **Not done.** The import path of Fabric's new key mapping helper could not be verified |
| Reset / recreate civilization | **Not done on purpose.** Unsafe until treasury and settlements have rules |
| NPCs, jobs, shops, trade | Not started |
| Nations (50), territory, capitals | Not started |
| Diplomacy, government rules, royal families | Not started (the government choice is only stored and shown) |
| Armies, battles, conquest | Not started |

"Tested" means the pure-Java unit tests pass (112 tests). Nothing in the Minecraft layer
(screens, packets, saved data, commands) has been run in a real game by the author of this phase.

## What a civilization is

- One civilization per player, owned by the player's UUID (taken from the connection, never from a packet).
- **Treasury** is the balance of the ledger account `civ:<uuid>`, minted once at creation from `startingSettlementTreasury` in the config (default 5000). There is no separate treasury number anywhere.
- **Population (100) and settlements (1)** are simulated numbers. They are not villagers or buildings in the world.
- **Government** is stored and shown. It has no gameplay effect yet.
- Names: 3–32 characters; letters of any language, digits, single spaces, `'` `-` `.`. Formatting codes, control and invisible characters, emoji and symbols are rejected. Duplicate names are refused regardless of case, spaces and punctuation, across the whole world.

## Capital settlements (phase 2)

- `/ron settlement` shows your civilization, whether a capital is founded, its name, coordinates, dimension, level, and whether the marker is still standing.
- `/ron settlement create` founds your capital where you stand. Rules, all checked on the server:
  you need a civilization; only one capital per civilization; Overworld only; not in spectator mode;
  the marker needs empty space next to you and solid, dry ground below. Nothing is ever replaced.
- **What appears in the world:** a two-block pillar (chiseled stone bricks with a lightning rod on top) placed next to you.
  That is all. There is no building, village or border yet. Breaking the marker does not delete the capital; the
  capital stays as data and the screen/command will report the marker as "missing".
- **What is saved:** id, owner UUID, name (`<Civilization> Capital`), dimension, your block position, level (1),
  creation time, and the marker's position and base block. The settlement is linked to the civilization through the
  owner UUID, so renaming the civilization does not break it.
- **Simulated vs real:** the civilization still has its starting simulated "settlements" number (1). The overview now
  shows it next to the number of settlements actually founded in the world (0 until you found the capital).
- Save schema is now **3**. Schema 1 and 2 saves load unchanged. Saves from a newer schema are still refused and left untouched.

### Manual test plan for phase 2 (in Minecraft 26.3)

1. Start the world from phase 1 (or a new one). `/ron settlement` with no civilization: tells you to use `/ron`.
2. Found a civilization with `/ron`. `/ron settlement` now says "Capital: Not founded yet" and how to create it.
3. Open `/ron`: the Capital block says "Not Founded" and shows the command; Settlements shows "1 simulated, 0 founded".
4. Stand on flat ground in the Overworld, facing open space. Run `/ron settlement create`. Expect a success message with the
   capital name and coordinates, and a small pillar appearing next to you.
5. Run `/ron settlement create` again: it must be refused ("already has a capital"), and no second pillar may appear.
6. `/ron settlement` and `/ron` show the capital, coordinates, "Overworld", level 1, marker "present", and "1 simulated, 1 founded".
7. Save and quit, reopen the world: the capital, coordinates and the pillar are still there; `/ron settlement create` is still refused.
8. Walk far away (marker unloaded) and run `/ron settlement`: marker shows "not checked". Come back: "present".
9. Break the pillar's base block, run `/ron settlement`: marker "missing" (the capital data stays).
10. Rejected cases (use a second test world or account, since a civilization can have only one capital):
    stand in the Nether or End (refused, Overworld only); stand in water or with a block in front on all four sides
    (refused, nothing placed); stand in the air above the ground (refused, no ground); switch to spectator (refused).
11. Dedicated server: start it, join, repeat steps 2–7. The server must reach `Done` with no client-class errors.

## Manual test plan for phase 1 (in Minecraft 26.3)

1. Install the JAR and Fabric API; start a **new singleplayer world**. In the log look for `Rise of Nations: story loaded (3 chapters, 10 quests).`
2. `/ron about` still prints the version.
3. `/ron` opens the screen and says you have no civilization. Click **Found a Civilization**.
4. Try bad input and watch the red message and the disabled confirm button: empty name, `ab`, 40 letters, a name with `§`, a name with an emoji.
5. Enter a good name and leader, click the color and government buttons to cycle, then **Found Civilization**. The overview should open: treasury 5,000 coins, population 100 (simulated), settlements 1, Settled, Stable.
6. Click **Rename**, try the same name (error), then a new name (works). Close and run `/ron` again: the new name is shown.
7. Run `/ron` again after creating: it must show the existing civilization, not a new form.
8. Save and quit to the title screen, reopen the world, run `/ron`: everything must be the same.
9. Create a second **new** world: `/ron` must say you have no civilization.
10. (Multiplayer, optional) With two accounts try the same name: the second must get "Another civilization already uses that name."
11. Dedicated server: start a server with the JAR and Fabric API. It must reach `Done` with no `client` class errors; then join and repeat steps 3–8.

## Toolchain (and where each number came from)

| Item | Value | Source |
|---|---|---|
| Minecraft | 26.3 | Stable release on Modrinth's Fabric API page; build confirmed working |
| Java | 25 | skill baseline table; build confirmed working |
| Fabric Loader | 0.19.5 | skill baseline table; log confirmed |
| Fabric API | 0.161.0+26.3 | skill baseline table; build confirmed working |
| Loom plugin | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT | confirmed working by the build |
| Gradle | 9.1.0 | confirmed working by the build |

## Where the Minecraft API calls come from

The screens, packets and saved data were written from the official Fabric documentation for 26.2
(the newest published; 26.3 is not documented separately), not from the 26.3 game files:
custom screens (`gui.setScreen`, `extractRenderState`, `GuiGraphicsExtractor.text`), networking
(`PayloadTypeRegistry.clientboundPlay/serverboundPlay`, `StreamCodec.composite`, `ServerPlayNetworking`,
`ClientPlayNetworking`), and saved data (`SavedDataType` with a codec, `getDataStorage().computeIfAbsent`).
If GitHub Actions reports a missing class or method, that is the first place to look.
Phase 2 added these Minecraft calls, written from earlier-version knowledge and **not verified against 26.3**
(only `isOutsideBuildHeight(BlockPos)` was confirmed in Mojang's mappings for 1.21.3–1.21.5): `Level.OVERWORLD/NETHER/END`,
`Level.dimension()`, `getBlockState`, `setBlock`, `hasChunkAt`, `Blocks.CHISELED_STONE_BRICKS/LIGHTNING_ROD/AIR`,
`BlockState.isAir/getFluidState/is`, `FluidState.isEmpty`, `BlockPos` (`above`, `below`, `relative`, `getX/Y/Z`),
`Direction` (`getClockWise`, `getCounterClockWise`, `getOpposite`), `Entity.getDirection`, `Entity.blockPosition`,
`Player.isSpectator`, `CommandSourceStack.getServer` and `sendFailure`. If the build reports one of them, that is the cause.


## How to build (GitHub Actions)

Push, open the **Actions** tab, and download the artifact **RiseOfNationsAI-Minecraft-26.3** when it is green.
The workflow runs the 112 tests as part of `gradle build`; a failing test fails the build.

## Where things are

- `core/` — game logic with **no Minecraft code**: `civ/` (civilizations, names, wire format), `settlement/` (capitals, rules, site planner), `economy/`, `story/`, `persist/`, `config/`.
- `net/` — the three packets and the server handlers (shared by client and dedicated server).
- `server/` — `RonSavedData` (world-save hookup), `SettlementCommands`, and `SettlementWorld` (the only code that looks at or changes real blocks).
- `client/` — screens and the client entrypoint. **Only** this package may use client classes (a test enforces it).
- `src/test/java/` — tests. `gradle coreTests` runs them.

## Config

`config/riseofnations.json` is created on first start. Values outside safe ranges are corrected and logged.
`startingSettlementTreasury` sets the starting treasury of new civilizations.

## Known limitations

- Changes are saved by Minecraft's normal world saving (autosave and quit). A crash right after founding a civilization can lose that change.
- Look-alike letters from different alphabets (for example a Cyrillic "a" in "Rome") are not detected as duplicates.
- The capital marker is just a pillar. It is not protected: anyone can break it (the capital data stays and is reported as "missing"). Protection comes with territory claiming.
- Adventure-mode players can still found a capital (only spectators are blocked).
- Capitals are Overworld-only for now.
- The capital's name is fixed at founding (`<Civilization> Capital`); there is no settlement rename yet.
- Texts are English only; there is no translation file yet.
- A client without the mod on a server that has it cannot join (normal for mods that add packets).
