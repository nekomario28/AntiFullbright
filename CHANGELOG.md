# Changelog

## 1.1.0

### Added

- Optional client-local scanner for ordinary `mods` and `resourcepacks` content.
- Exact declared Mod ID, configured SHA-256, and precise OptiFine/MCPatcher lightmap-path rules.
- Recursive resource-pack monitoring with debounced rescans, root deletion/recreation recovery, and normal Minecraft disconnect/blocking-screen handling.
- Server-side dark-mining warning progression, configurable kick threshold, operator notifications, administrator commands, persistent warning state, and dedicated JSONL evidence.
- 128×128 project icon registered through NeoForge `logoFile` metadata.
- 24 required GameTests, packaged-server isolation checks, launcher-managed client gates, same-world restart persistence, and a real packet-to-`BlockEvent.BreakEvent`-to-kick enforcement gate.
- Scanner and `WatchService` regression tests on Linux, Windows, and macOS, plus expanded English/Japanese message assertions.

### Production policy

The public default policy is `antifullbright/default-v1`:

- `failClosed = false`;
- exact blocked Mod ID: `fullbright`;
- blocked resource-pack path prefixes:
  - `assets/minecraft/optifine/lightmap/`
  - `assets/minecraft/mcpatcher/lightmap/`
- distributed Mod and resource-pack SHA-256 lists are empty;
- ambiguous names, descriptions, metadata text, and unknown content remain warning-only.

### Configuration migration

NeoForge does not overwrite an existing `config/antifullbright-client.toml` when compiled defaults change. Users upgrading from a beta configuration should back up and regenerate the file, or edit it manually:

```toml
failClosed = false
blockedResourcePackPaths = "assets/minecraft/optifine/lightmap/,assets/minecraft/mcpatcher/lightmap/"
blockedModSha256 = ""
blockedResourcePackSha256 = ""
```

Remove the former broad `assets/minecraft/shaders/core/lightmap` rule if it remains in an existing configuration.

### Security boundary

The client scanner is local and user-controlled. It is not tamper-proof, does not provide server attestation, and can be removed or modified. Strict deployments should combine a controlled launcher, signed manifests, and the independently useful server-side behavioral detector.

Full launcher and blocking-screen runtime evidence is recorded on Linux. Scanner and filesystem-watcher behavior is additionally tested on Windows and macOS; complete graphical Minecraft runtime on those systems is not claimed by this release evidence.
