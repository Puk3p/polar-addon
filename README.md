# PolarAddon

PolarAddon is a Minecraft server plugin for Polar Anticheat setups. It adds staff-side tools for random rotation, moderate knockback, and KillAura bait checks.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/polar rotate <player>` | `polaraddon.rotate` | Rotates an online player to a random yaw. |
| `/polar knockback <player>` | `polaraddon.knockback` | Applies a moderate knockback from the player's current facing direction. |
| `/polar kb <player>` | `polaraddon.knockback` | Alias for `/polar knockback`. |
| `/polar test <player>` | `polaraddon.test` | Applies random rotation and moderate knockback together. |
| `/polar summon <player> <mob>` | `polaraddon.summon` | Spawns a temporary KillAura or grinder test mob near the player. |

## Behavior

- Knockback checks the block in front of the player and cancels horizontal push when it would drive them into a solid block.
- `/polar summon` only tries left, right, and behind the player, never directly in front.
- Summoned test mobs are placed 2-3 blocks away when there is solid ground and enough open body space.
- Summoned mobs despawn automatically after 3 seconds and do not receive a custom name.
- Supported summon mobs are `blaze`, `skeleton`, `zombie`, `creeper`, `spider`, `cave_spider`, `witch`, `enderman`, `pig_zombie`, `zombie_pigman`, `slime`, `magma_cube`, and `iron_golem`.
- Polar API listeners log detection alerts, mitigations, and punishments.

## Requirements

- A Spigot-compatible Minecraft server using the 1.8.8 API.
- PolarLoader available on the server before this plugin loads.
- Java 8 or newer at runtime.
- Maven and a modern JDK for building the plugin.

## Build

```sh
mvn verify
```

The shaded plugin jar is produced under `target/`.

## Install

1. Build the plugin with Maven.
2. Copy the shaded jar from `target/` into the server `plugins/` directory.
3. Make sure PolarLoader is installed.
4. Restart the server.

## Permissions

`polaraddon.use` allows access to the `/polar` command root. Each action also requires its own permission:

- `polaraddon.rotate`
- `polaraddon.knockback`
- `polaraddon.test`
- `polaraddon.summon`

All permissions default to server operators.

## Releases

The release workflow runs when a tag starting with `v` is pushed, for example:

```sh
git tag v1.0.1
git push origin v1.0.1
```
