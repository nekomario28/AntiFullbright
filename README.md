# Anti Fullbright

[日本語ドキュメント](README_JA.md)

Anti Fullbright is a server-only mod for Minecraft 1.21.1 and NeoForge 21.1.235. It does not claim to identify Fullbright directly. Instead, it progressively warns players who mine for an extended period in complete darkness.

The Mod ID is `antifullbright`. The Java source, tag namespace, configuration filename, logging thread, and build artifact all use this ID.

## Building and installation

Use Java 21.

```bash
./gradlew build
```

Copy `build/libs/antifullbright-1.0.0.jar` into the `mods` directory of a NeoForge 1.21.1 server. No client-side installation or custom network payload is required.

## Detection behavior

- Only natural mining blocks broken by a real player through `BlockEvent.BreakEvent` are recorded.
- Fake players, creative players, spectators, and—by default—operators and players with Night Vision are excluded.
- Both the player's eye position and the broken block must match the configured block-light and sky-light values.
- The mod never loads a chunk to obtain light values.
- By default, a warning requires both 60 seconds of continuous mining and 20 counted blocks. The session resets after each warning.
- A session resets after 10 seconds without a qualifying break, movement into light, death, logout, dimension change, teleportation, or placement of a light-emitting block.
- Holding a tagged light source grants only a 20-second grace period at the beginning of a session. Mining counts normally after that period even if the item is still held.
- Create machines and Deployers are excluded because they do not produce a real-player break event or operate through a FakePlayer. A real player using tools from another mod is checked normally.
- Recently player-placed blocks are excluded when broken again. These records have an expiration time, a per-player limit, and are removed on chunk unload.

Only players with active state are checked every 20 ticks. The mod performs no surrounding-area scans and never force-loads chunks.

## Configuration

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
- `notifyOperatorsAtWarning` (the first warning level reported to operators; default 2)
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

Run `/darkmining reload` after changing it. The server sends fully rendered text, so no language resources or mod installation are needed on clients. This is one server-wide language setting; it does not automatically follow each client's language preference.

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

Warning and kick evidence is written to the normal server logger and asynchronously appended as one JSON object per line to `logs/dark-mining-detections.jsonl`. World and entity values are captured into an immutable record on the server thread before a dedicated single writer thread performs file I/O.
