# SPS Viewpoint Compatibility Patch (`UpdateCompatPatches`)

A Project Zomboid (Build 42) mod that keeps other mods running across game updates until their authors ship fixes. It runs through ZombieBuddy and works in memory while the game loads. No other mod's files are changed on disk.

- **Steam Workshop:** https://steamcommunity.com/sharedfiles/filedetails/?id=3839321264
- **Current version:** 0.2.3 (game 42.22.2)
- **Requires:** Project Zomboid 42.22 or later and ZombieBuddy 2.3.4 or later. Keep it directly under ZombieBuddy in the mod list.

What it does:

1. **Version unlock.** Lets listed mods load past an out-of-date `versionMax` in their `mod.info`.
2. **Class adapter.** Rewrites a mod's references to game code that moved or was removed, as that mod's classes load.
3. **Admin menu filter** (Lua). In Viewpoint's first- and third-person view, keeps other mods' admin and debug menus out of the action picker.
4. **Immersive Snow - Viewpoint version check** (Lua). Skips that add-on's Viewpoint version check, but only for one specific tested version pair. This is unsupported; see the player README.

The full player-facing description, the rule format and the log lines are in [`workshop/Contents/mods/UpdateCompatPatches/common/README.txt`](workshop/Contents/mods/UpdateCompatPatches/common/README.txt).

## What's in this repo

| Path | What it is |
|---|---|
| `src/jay/compat/` | The Java source (package `jay.compat`) |
| `src/jay/compat/compat-rules.txt` | The built-in rule list (version unlocks and class/method remaps per game version) |
| `test/` | Offline checks: `Check.java` rewrites mod classes exactly as in game and has the JVM verify them; `check2.py` / `refcheck.py` find missing references against a new game jar |
| `build.sh`, `BUILDING.txt` | Build instructions (produces `UpdateCompatPatches.jar`) |
| `workshop/` | The Steam Workshop release folder (`workshop.txt`, `preview.png`, `mod.info`, poster, player README, and the two Lua files), **without** the built jar or the `42/src` copy |

## Building

See [`BUILDING.txt`](BUILDING.txt) or [`build.sh`](build.sh). You need JDK 25, plus the game's `projectzomboid.jar` and `ZombieBuddy.jar` to compile against (neither is bundled):

```
PZ="<Project Zomboid folder>" ./build.sh
```

## Releases

Built jars are attached to this repo's **Releases** page, one per version tag (`v0.2.3` and so on), instead of being committed.

## License

No license has been chosen for this mod's own code yet.
