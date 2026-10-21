# SovereignCore — Forge 1.20.1

A source project for an in-game server management mod targeting Minecraft Java 1.20.1, Forge 47.2.x, and Java 17.

**Release/build status:** source code passed a local static compile check against signature stubs. This environment could not download ForgeGradle or launch a real Forge server, so a production-ready/reobfuscated JAR has not been verified or included. Use `build.bat` on Windows with JDK 17 and internet access to build the real release JAR.

## Features

- In-game manager GUI: `/sc` or `/manager`
- Online-player browser and per-player staff panel
- Teleport to a player, bring a player to you, teleport to coordinates and dimensions
- Named personal homes; staff can browse/delete other players' homes
- Named warps and global spawn
- `/tpa`, `/tpaccept`, `/tpdeny`
- Multiple roles, custom role creation, and in-game permission toggle editor
- Freeze/unfreeze, heal/feed, gamemode, invulnerability, flight, clear effects and XP
- Inspect inventory/ender chest, clear either, give an item
- Kick, ban, pardon, op and deop controls
- Persistent server data in `<world>/data/sovereigncore.properties`

## Build a release JAR on Windows

1. Install **JDK 17** and confirm `java -version` reports version 17.
2. Extract this project folder somewhere writable.
3. Double-click `build.bat`. It downloads Gradle 8.1.1 if missing and runs ForgeGradle to build and reobfuscate the mod.
4. After a successful build, take the JAR from `build\libs`.
5. Put the same JAR into the `mods` folder of the Forge 1.20.1 server **and every client**.
6. Restart everything, join the server, and run `/sc`.
7. First test on a copy of the world and keep backups.

The first build needs internet access and can take some time while Gradle downloads Forge/Minecraft dependencies.

## Commands

- `/sc`, `/manager` — open the UI
- `/sethome [name]`, `/home [name]`, `/delhome <name>`, `/homes`
- `/setwarp <name>`, `/warp <name>`, `/delwarp <name>`, `/warps`
- `/setspawn`, `/spawn`
- `/tpa <player>`, `/tpaccept`, `/tpdeny`
- `/sctp <player>` — staff teleport

## Default permissions

- Owner / Admin: all SovereignCore permissions
- Moderator: player browsing, teleportation, homes/warps, basic moderation and freeze
- VIP: up to five personal homes, home/warp/TPA/spawn
- Member: up to three personal homes, home/warp/TPA/spawn
- Vanilla operators bypass custom permissions; assigning the Owner role or editing Owner permissions requires vanilla permission level 4.

This is an early version. The actual Forge runtime integration still needs testing before public-server use.
