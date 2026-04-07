# PolarAddon

PolarAddon is a Minecraft server plugin for Polar Anticheat setups. It adds staff commands for forcing a player rotation and applying controlled knockback.

## Features

- `/polar rotate <player> [yaw] [pitch]` rotates an online player to the requested yaw and pitch.
- `/polar knockback <player> [strength]` applies knockback from the player's current facing direction.
- Knockback checks the space in front of the player and cancels horizontal push when it would drive them into a solid block.
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

- `polaraddon.use` allows access to the `/polar` command root.
- `polaraddon.rotate` allows `/polar rotate`.
- `polaraddon.knockback` allows `/polar knockback`.

All permissions default to server operators.
