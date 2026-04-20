# PolarAddon

PolarAddon is a Minecraft server plugin for Polar Anticheat setups. It adds staff-side tools for random rotation, moderate knockback, and KillAura bait checks.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/pa rotate <player>` | `polaraddon.rotate` | Rotates an online player to a random yaw. |
| `/pa knockback <player>` | `polaraddon.knockback` | Applies a moderate knockback from the player's current facing direction. |
| `/pa kb <player>` | `polaraddon.knockback` | Alias for `/pa knockback`. |
| `/pa test <player>` | `polaraddon.test` | Applies random rotation and moderate knockback together. |
| `/pa summon <player> <mob>` | `polaraddon.summon` | Spawns a temporary KillAura or grinder test mob near the player. |
| `/pa reload` | `polaraddon.reload` | Reloads plugin config (including Discord alert settings). |
| `/polaraddon ...` | `polaraddon.use` + sub-permission | Alias for `/pa`. |

## Behavior

- Knockback checks the block in front of the player and cancels horizontal push when it would drive them into a solid block.
- `/polar summon` only tries left, right, and behind the player, never directly in front.
- Summoned test mobs are placed 2-3 blocks away when there is solid ground and enough open body space.
- Summoned mobs despawn automatically after 3 seconds and do not receive a custom name.
- Supported summon mobs are `blaze`, `silverfish`, `zombie`, `skeleton`, `witch`, `creeper`, and `enderman`.
- Polar API listeners log detection alerts, mitigations, and punishments.
- Polar alerts can also be sent as Discord webhook embeds.
- Detection alerts are grouped more patiently by player + check, with max violation level shown per burst.
- Reach/KillAura detection bursts can include combat context (target, distance, CPS window, ping, TPS) when player is in combat.

## Discord Alerts

Configure `plugins/PolarAddon/config.yml`:

```yaml
discord:
  webhook-url: "https://discord.com/api/webhooks/..."
  username: "PolarAddon Alerts"
  avatar-url: ""
  aggregate-window-millis: 1250
  aggregate-window-detection-millis: 2500
  aggregate-window-detection-by-cloud-type:
    COMBAT_BEHAVIOR: 1200
    AUTO_CLICKER: 1500
    CPS_LIMIT: 1500
    RIGHT_CPS_LIMIT: 1500
    INVALID_PROTOCOL: 3500
  aggregate-window-mitigation-millis: 1250
  aggregate-window-punishment-millis: 1250
  combat-context:
    enabled: true
  player-avatar:
    premium-skin-lookup-enabled: true
    premium-skin-lookup-timeout-millis: 1200
    premium-skin-lookup-cache-minutes: 60
    fallback-url: "https://mc-heads.net/avatar/Steve/128"
  alerts:
    detection: true
    mitigation: true
    punishment: true
```

If `discord.webhook-url` is empty, Discord alerts are disabled.
Use aggregate windows to control patience:
- `discord.aggregate-window-detection-millis`
- `discord.aggregate-window-detection-by-cloud-type` (optional overrides by Polar `CloudCheckType`)
- `discord.aggregate-window-mitigation-millis`
- `discord.aggregate-window-punishment-millis`

Player avatar behavior:
- If premium lookup is enabled and the username exists on Mojang, the embed uses that player's skin head.
- If not premium (or lookup fails), it uses `discord.player-avatar.fallback-url`.

Combat context behavior:
- Works best with CombatLogX installed (`softdepend`), using API checks when available.
- Without CombatLogX, it falls back to recent PvP activity tracking.
- Included for combat-oriented detection spikes (Reach/KillAura patterns or cloud types like `COMBAT_BEHAVIOR`, `AUTO_CLICKER`, `CPS_LIMIT`, `RIGHT_CPS_LIMIT`).
- Detection embeds also include `Cloud Type` when Polar provides it.

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

`polaraddon.use` allows access to the `/pa` command root. Each action also requires its own permission:

- `polaraddon.rotate`
- `polaraddon.knockback`
- `polaraddon.test`
- `polaraddon.summon`
- `polaraddon.reload`

Permission bundles:
- `polaraddon.staff` → `use`, `rotate`, `knockback`, `test`, `summon`
- `polaraddon.admin` → `polaraddon.staff` + `reload`
- `polaraddon.*` → full wildcard access

Defaults:
- Staff/action nodes default to `false` (assign explicitly to staff groups).
- `polaraddon.admin` and `polaraddon.*` default to `op`, so OP has full command access by default.

## Releases

The release workflow runs when a tag starting with `v` is pushed, for example:

```sh
git tag v1.0.1
git push origin v1.0.1
```
