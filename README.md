# Anti Fullbright

[日本語ドキュメント](README_JA.md)

Anti Fullbright is a Minecraft 1.21.1 / NeoForge 21.1.235 mod.

- On servers, it does not claim to identify Fullbright directly. It progressively warns players who mine for an extended period in complete darkness.
- When installed on a client, it scans the local `mods` and `resourcepacks` directories during startup and fails client loading when configured Fullbright signatures are found.
- After startup, it recursively watches `resourcepacks` and performs a full rescan after create, modify, or delete events.

The Mod ID is `antifullbright`. Version 1.1.0 adds the client content scanner.

## Building and installation

Use Java 21.

```bash
./gradlew build
```

The artifact is `build/libs/antifullbright-1.1.0.jar`.

- For server-side dark-mining detection only, install it in the server `mods` directory.
- Players who must use local mod and resource-pack scanning also install the same JAR in the client `mods` directory.

The scanner runs locally. This version does not yet implement a cryptographic server handshake that proves the scanner is installed or that a reported scan result is genuine.

## Client scanner

When enabled, client startup scans the following content.

### Mods

The scanner inspects `.jar` and `.zip` files directly inside `mods`:

- file names
- archive paths
- `META-INF/neoforge.mods.toml`
- `META-INF/mods.toml`
- `fabric.mod.json`
- `quilt.mod.json`
- configured SHA-256 hashes

The running AntiFullbright archive is excluded only by its actual code-source absolute path, not by trusting a claimed Mod ID.

### Resource packs

ZIP packs and unpacked directories directly inside `resourcepacks` are checked using:

- pack names
- `pack.mcmeta`
- archive or directory paths
- configured SHA-256 hashes

The default prohibited lightmap signatures are:

```text
assets/minecraft/optifine/lightmap/
assets/minecraft/mcpatcher/lightmap/
assets/minecraft/shaders/core/lightmap
```

A violation—or unreadable content while `failClosed = true`—throws during client setup and fails mod loading.

### Change monitoring

After a clean startup scan, `watchResourcePacks = true` recursively monitors `resourcepacks` with Java `WatchService`.

- create, modify, and delete events are observed
- newly created subdirectories are registered
- bursts are coalesced using `watchDebounceMillis`
- a full resource-pack rescan follows changes, including `OVERFLOW`
- runtime detection exits the client with code `23` by default

## Client configuration

Settings are generated in `config/antifullbright-client.toml`:

- `enabled`
- `scanMods`
- `scanResourcePacks`
- `watchResourcePacks`
- `failClosed`
- `exitOnRuntimeDetection`
- `watchDebounceMillis`
- `maximumArchiveEntries`
- `maximumTextBytes`
- `blockedModTokens`
- `blockedResourcePackTokens`
- `blockedResourcePackPaths`
- `blockedModSha256`
- `blockedResourcePackSha256`

Tokens and hashes are comma-separated. Text matching is case-insensitive. Hashes are 64 hexadecimal SHA-256 values with an optional `sha256:` prefix.

## Server-side dark-mining detection

- Only natural mining blocks broken by a real player through `BlockEvent.BreakEvent` are recorded.
- Fake players, creative players, spectators, and—by default—operators, players with Night Vision, and underwater players are excluded.
- Both the player's eye position and the broken block must match the configured block-light and sky-light values.
- The mod never loads a chunk to obtain light values.
- By default, a warning requires both 60 seconds of continuous mining and 20 counted blocks.
- Sessions reset on inactivity, light exposure, death, logout, dimension changes, teleportation, or light-emitting block placement.
- Create machines and Deployers are excluded when they do not produce a real-player break event or operate through a FakePlayer.
- Recently player-placed blocks are excluded using expiring, per-player bounded records.

Only players with active state are checked every 20 ticks. The mod performs no surrounding-area scans and never force-loads chunks.

## Server configuration

Settings are generated in `config/antifullbright-server.toml`:

- `language`
- `enabled`
- `maximumY`
- `requiredBlockLight`
- `requiredSkyLight`
- `continuousMiningSeconds`
- `minimumBlocks`
- `inactivityResetSeconds`
- `torchHoldingGraceSeconds`
- `warningsBeforeKick`
- `warningDecayMinutes`
- `excludeOperators`
- `excludeNightVision`
- `excludeUnderwater`
- `notifyOperatorsAtWarning`
- `persistWarnings`
- `enableDedicatedLog`
- `placedBlockTrackingEnabled`
- `placedBlockTrackingExpirationMinutes`
- `placedBlockTrackingMaximumEntriesPerPlayer`

`/darkmining reload` synchronously reloads the server configuration and constrains values to their declared ranges.

## Data-pack tags

- Light-source items: `antifullbright:dark_mining_light_sources`
- Counted mining blocks: `antifullbright:dark_mining_counted_blocks`

## Administrator commands

All commands require permission level 2 or higher.

```text
/darkmining status <player>
/darkmining reset <player>
/darkmining setwarning <player> <count>
/darkmining reload
/darkmining debug <player>
```

## Persistence and evidence logs

Warning levels and timestamps are stored by UUID in Overworld SavedData (`antifullbright_warnings.dat`). Warning and kick evidence is written to the normal logger and `logs/dark-mining-detections.jsonl`.

## Security limitations

This is an ordinary NeoForge client mod, not a tamper-proof anti-cheat.

- A player can remove or modify the scanner itself.
- A custom implementation may evade name, metadata, path, and known-hash signatures.
- A normal server mod cannot fully trust information controlled by the client.
- Strict deployments should combine a controlled launcher, signed manifests, and the existing server-side behavioral detector.

The dark-mining detector remains enabled as a defense-in-depth signal when client scanning is bypassed.
