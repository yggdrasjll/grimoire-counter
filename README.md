# Grimoire Counter

Grimoire's carry counter as a mod of its own, for Hypixel SkyBlock on Minecraft: Java Edition 26.2 (Fabric).
Made and owned by **Yggdrasjll**. All rights reserved: see [LICENSE.txt](LICENSE.txt).

It counts what you carry for each client, and only when Hypixel itself confirms it:

| Carry | Counts when |
| --- | --- |
| Slayer bosses (Voidgloom, Inferno, Revenant, Tarantula, Sven, Riftstalker) | a boss with your client's name on it dies near you, and it is the booked slayer and tier. Each boss counts once. A boss that despawns with health left (quest failed, the client died, you left) never counts. Loot share lines count nothing. |
| Dungeons (Entrance, F1–F7, M1–M7) | the run started with your client in it, on the booked floor, and the end screen says the boss was defeated. A failed run never counts. |
| Kuudra (Basic to Infernal) | the run started with your client in it, on the booked tier, and Kuudra went down. A defeat never counts. |

It is display only: it reads chat, the scoreboard, the tab list and the name tags your game already shows. It never
clicks, aims or moves for you.

## What it has

- **Counting by itself**, with the rules above (the same ones as in Grimoire).
- **Counting by hand** on its page: − and + for every carry, more or fewer ordered, remove.
- **A price list** with a row for every carry it counts (every slayer tier, every Kuudra tier, Entrance to M7): your
  prices in millions per carry. When a client trades you coins, the carries that amount buys
  are offered in chat, one click to add them; each carry shows what it is worth.
- **The count in party chat** (off until you turn it on): `Name 3/10 - Grimoire`, each time a carry counts.
- **The counter on screen**, which you can drag anywhere and size.
- A switch for each of these.

It keeps no history and sends nothing anywhere: the only thing it writes is its own settings file,
`config/grimoire-counter/counter.json` (your carries in progress, your prices, your switches).

## Using it

- Open its page with **K** (change it in Options → Controls → Grimoire Counter) or `/gcounter`.
- `/gcounter add <kind> <tier> <player> <amount>`, for example `/gcounter add voidgloom 4 Name 10`,
  `/gcounter add kuudra infernal Name 3`, `/gcounter add dungeon m5 Name 2`.
- `/gcounter plus <player>`, `/gcounter minus <player>`, `/gcounter remove <player>`.
- `/gcounter claim <player>` counts a boss that died but wasn't counted (another tier than the booked one, or no
  owner name on it); the chat note about it has a button that does the same.
- `/gcounter debug` says what the counter can see around you.

With Grimoire itself installed, Grimoire Counter stays off: Grimoire already has the counter.

## Installing

Needs Fabric Loader 0.19.5 or newer, Fabric API and Java 25. Put `[Ygg] Grimoire Counter-<version>.jar` in your
`mods` folder.

## Building

`gradle build` makes the jar in `build/libs`. `gradle runClient -Pselftest` runs the mod's own check of its
counting rules.

## Not affiliated

Minecraft belongs to Mojang Studios and Microsoft. Hypixel and SkyBlock are trademarks of Hypixel Inc. Grimoire
Counter is unofficial and is not affiliated with or endorsed by any of them. You are responsible for following the
rules of the servers you play on.
