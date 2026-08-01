# Anti Fullbright

[日本語ドキュメント](README_JA.md)

Anti Fullbright is a Minecraft 1.21.1 / NeoForge 21.1.235 mod.

- The server-side feature does not claim to identify Fullbright directly. It progressively warns players who mine for an extended period in complete darkness.
- The optional client-side scanner checks local mods and resource packs during client setup and monitors later resource-pack changes.

The Mod ID is `antifullbright`. The client scanner is included in `1.1.0` and is not a tamper-proof anti-cheat.

## Building and installation

Use Java 21.

```bash
./gradlew build
```

The stable artifact is `build/libs/antifullbright-1.1.0.jar`.

- Install it in the server `mods` directory for server-side dark-mining detection.
- Install the same JAR in the client `mods` directory only when the local scanner is required.

The server-side detector still works without client installation. This release does not contain a server handshake that proves the scanner is installed or that a scan result is genuine.

## Server-side detection behavior

- Only natural mining blocks broken by a real player through `BlockEvent.BreakEvent` are recorded.
- Fake players, creative players, spectators, and—by default—operators, players with Night Vision, and players whose eyes are underwater are excluded.
- Both the player's eye position and the broken block must match the configured block-light and sky-light values.
- The mod never loads a chunk to obtain light values.
- By default, a warning requires both 60 seconds of continuous mining and 20 counted blocks. The session resets after each warning.
- A session resets after 10 seconds without a qualifying break, movement into light, death, logout, dimension change, teleportation, or placement of a light-emitting block.
- Holding a tagged light source grants only a 20-second grace period at the beginning of a session. Mining counts normally after that period even if the item is still held.
- Create machines and Deployers are excluded because they do not produce a real-player break event or operate through a FakePlayer. A real player using tools from another mod is checked normally.
- Recently player-placed blocks are excluded when broken again. These records have an expiration time, a per-player limit, and are removed on chunk unload.

Only players with active state are checked every 20 ticks. The mod performs no surrounding-area scans and never force-loads chunks.

## Server configuration

All settings can be changed in `config/antifullbright-server.toml` after the first server start.

- `language` (`ja_jp` or `en_us`; default `en_us`)
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

`/darkmining reload` synchronously reloads this file. Numeric values are constrained to their declared safe ranges.

### Display language

The `language` setting controls player warnings, disconnect reasons, operator notifications, and administrator command output.

```toml
# English
language = "en_us"

# Japanese
language = "ja_jp"
```

Run `/darkmining reload` after changing it. The server sends fully rendered text, so client language resources are not required for the server-side detector.

## Client scanner

The scanner runs during `FMLClientSetupEvent`. It can prevent normal client startup from completing, but it does **not** guarantee that every other mod was prevented from executing any initialization code before the scan.

The scanner inspects `.jar` and `.zip` files directly inside `mods`, ZIP packs and unpacked directories directly inside `resourcepacks`, and configured hashes.

The approved public default is versioned as [`antifullbright/default-v1`](docs/production-blocking-policy-1.1.0.md). Its core rule is: **BLOCK only on precise, high-confidence evidence; report broad names, descriptions, and uncertain scan failures as WARNING.**

### Default blocking findings

A default installation blocks only:

- the exact authoritative loader Mod ID `fullbright`;
- an exact SHA-256 explicitly added to `blockedModSha256` or `blockedResourcePackSha256`;
- either precise resource-pack prefix:
  - `assets/minecraft/optifine/lightmap/`
  - `assets/minecraft/mcpatcher/lightmap/`;
- unreadable, malformed, or over-limit content only when a controlled deployment explicitly changes `failClosed = true`.

The distributed SHA-256 lists are empty. Hashes are version-specific and must be maintained by the deployment that chooses to use them.

The running AntiFullbright archive is ignored only by its actual loaded-mod path, with code-source fallback. Another archive cannot bypass scanning merely by claiming the same Mod ID.

### Default warning findings

Tokens in `suspiciousModTokens` and `suspiciousResourcePackTokens` produce warnings only. This includes Fullbright, Gamma Bright, Gamma Utils, True Fullbright, Fullbright Utils, Resource Gamma Utils, Boosted Brightness, and Night Vision naming variants.

A filename, display name, description, dependency declaration, nested custom ID, or broad shader path is not sufficient for a default block. For example, a harmless description such as “disables Fullbright compatibility” remains warning-only.

The generic `assets/minecraft/shaders/core/` path is intentionally not blocked. It is too broad to serve as a Fullbright-specific signature.

### Resource-pack monitoring

When `watchResourcePacks = true`, Java `WatchService` recursively monitors `resourcepacks`.

- create, modify, delete, and overflow conditions trigger a debounced full rescan;
- newly created subdirectories are registered;
- deletion and recreation of `resourcepacks` itself is recovered through a parent-directory watch;
- a runtime blocking finding is handled on the Minecraft main thread;
- the current world is left through Minecraft's normal disconnect path and a blocking screen is displayed;
- the JVM is not terminated directly.

`WatchService` is a change-notification aid, not a complete security boundary. A future phase should also rescan immediately before server connection and after resource-pack selection changes.

## Client configuration

Settings are generated in `config/antifullbright-client.toml`:

- `enabled`
- `scanMods`
- `scanResourcePacks`
- `watchResourcePacks`
- `failClosed` (public default `false`)
- `disconnectOnRuntimeDetection`
- `watchDebounceMillis`
- `maximumArchiveEntries`
- `maximumTextBytes`
- `blockedModIds`
- `suspiciousModTokens`
- `suspiciousResourcePackTokens`
- `blockedResourcePackPaths`
- `blockedModSha256`
- `blockedResourcePackSha256`

Comma-separated identifiers and tokens are compared case-insensitively. Hashes are 64 hexadecimal SHA-256 values with an optional `sha256:` prefix.

### Upgrading an existing beta configuration

NeoForge does not overwrite an existing client configuration when compiled defaults change. Back up and delete `config/antifullbright-client.toml` to regenerate it, or edit it manually:

```toml
failClosed = false
blockedResourcePackPaths = "assets/minecraft/optifine/lightmap/,assets/minecraft/mcpatcher/lightmap/"
```

A controlled installation may deliberately retain `failClosed = true`; that is a deployment override, not the public default.

### Privacy and enforcement scope

This release does not transmit scanned file contents, filenames, local paths, hashes, or scan results over the network. Classification remains inside the client and is shown only in local logs and the blocking screen.

Local logs or crash reports may contain paths to affected files. Review and redact paths containing account names, home directories, or other private information before sharing those files with third parties.

These settings are controlled by the player's local client. The server cannot currently fix or verify the enabled state or policy content, so this release must not be treated as server-enforced anti-cheat.

## Data-pack tags

- Light-source items: `antifullbright:dark_mining_light_sources`
- Counted mining blocks: `antifullbright:dark_mining_counted_blocks`

The built-in tags are located at:

- `data/antifullbright/tags/item/dark_mining_light_sources.json`
- `data/antifullbright/tags/block/dark_mining_counted_blocks.json`

A data pack can add values through tags with the same IDs. NeoForge's `remove` array can remove built-in entries. For elements from another mod, the recommended syntax is `{ "id": "othermod:item", "required": false }`.

## Administrator commands

All commands require permission level 2 or higher.

```text
/darkmining status <player>
/darkmining reset <player>
/darkmining setwarning <player> <count>
/darkmining reload
/darkmining debug <player>
```

`reset` clears the warning state and current session. `debug` displays Y position, eye and feet light levels, session duration, counted blocks, remaining light-holding grace, warning state, and the current exclusion reason.

## Persistence and evidence logs

Warning levels and last-warning timestamps are stored by UUID in Overworld SavedData (`antifullbright_warnings.dat`). By default, the effective level decreases by one for every 30 minutes without a new warning when the state is next read or updated.

Warning and kick evidence is written to the normal server logger and asynchronously appended as one JSON object per line to `logs/dark-mining-detections.jsonl`.

## Security limitations

This client scanner is not tamper-proof.

- A player can remove or modify the scanner.
- Unknown implementations may evade exact IDs, paths, and known hashes.
- A normal server mod cannot fully trust data controlled by the client.
- Strict deployments should combine a controlled launcher, signed manifests, and the existing server-side behavioral detector.

Stable `1.1.0` artifacts must be published from the exact reviewed release-candidate JAR without rebuilding it after approval. See `CHANGELOG.md` and the release evidence documents.
