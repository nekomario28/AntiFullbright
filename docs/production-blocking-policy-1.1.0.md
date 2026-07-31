# Production Blocking Policy — AntiFullbright 1.1.0

## Status

- Policy ID: `antifullbright/default-v1`
- Status: approved production default
- Scope: client-local scanner defaults
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Candidate implementation: `1.1.0-beta.1`

This policy defines the rules shipped as AntiFullbright's default client configuration. It does not create server-side attestation: players can remove the scanner, alter its configuration, or modify the client. The existing server-side dark-mining detector remains the independently useful enforcement layer.

## Decision rule

The production default follows one principle:

> BLOCK only on high-confidence, precisely defined evidence. Treat names, descriptions, broad paths, and scan uncertainty as WARNING unless a controlled deployment explicitly chooses stricter behavior.

This reduces false-positive startup denial while preserving exact rules that are reproducible in tests.

## Approved defaults

| Setting | Default |
| --- | --- |
| `enabled` | `true` |
| `scanMods` | `true` |
| `scanResourcePacks` | `true` |
| `watchResourcePacks` | `true` |
| `disconnectOnRuntimeDetection` | `true` |
| `failClosed` | `false` |
| `blockedModIds` | `fullbright` |
| `blockedModSha256` | empty |
| `blockedResourcePackSha256` | empty |
| `blockedResourcePackPaths` | `assets/minecraft/optifine/lightmap/`, `assets/minecraft/mcpatcher/lightmap/` |

The resource limits and watcher debounce remain operational safety controls rather than cheat signatures.

## BLOCK findings

A default installation blocks only these cases:

1. **Exact declared Mod ID**
   - A loader metadata parser finds the exact declared Mod ID `fullbright`.
   - Dependency IDs, nested custom JSON IDs, filenames, display names, and descriptive text do not count as an exact declared ID.

2. **Exact configured SHA-256**
   - The complete archive or resource-pack digest matches an administrator-supplied entry.
   - The release ships no default hashes because hashes are build- and version-specific.

3. **Precise legacy lightmap path**
   - A resource pack contains a path beginning with:
     - `assets/minecraft/optifine/lightmap/`
     - `assets/minecraft/mcpatcher/lightmap/`

4. **Explicit strict-mode scan failure**
   - Unreadable, malformed, or over-limit content blocks only when the local operator changes `failClosed` to `true`.

## WARNING findings

The following remain warning-only under the approved defaults:

- filename matches;
- archive-entry path token matches;
- display-name or description matches;
- metadata text that mentions Fullbright or compatibility with it;
- identifiers such as Gamma Utils, True Fullbright, Fullbright Utils, Resource Gamma Utils, or Boosted Brightness unless an exact rule is separately approved;
- resource-pack names containing Fullbright, Night Vision, or Gamma Bright wording;
- unreadable, malformed, or over-limit content while `failClosed = false`.

Warning tokens are intentionally broader than block rules. They provide local evidence without claiming that wording alone proves prohibited behavior.

## Explicit non-block rules

The default policy does **not** block solely because of:

- the generic word `gamma`;
- the generic word `brightness`;
- `nightvision` or `night-vision` text;
- a filename containing `fullbright`;
- a harmless description such as “disables Fullbright compatibility”;
- `assets/minecraft/shaders/core/` or another generic core-shader path;
- an unknown mod or resource pack;
- a dependency declaration naming a blocked Mod ID;
- a nested JSON field named `id` that is not the loader's authoritative root ID.

The former broad default `assets/minecraft/shaders/core/lightmap` rule is removed because a generic core-shader path is not sufficiently specific to Fullbright behavior.

## Default warning tokens

### Mods

```text
fullbright
full_bright
full-bright
gammabright
gamma-bright
gammautils
gamma-utils
boostedbrightness
boosted-brightness
truefullbright
true-fullbright
fullbrightutils
fullbright-utils
resourcegammautils
resource-gamma-utils
```

### Resource packs

```text
fullbright
full_bright
full-bright
nightvision
night-vision
gammabright
gamma-bright
```

## Hash registry policy

The distributed hash lists remain empty. A deployment may add hashes only when it records:

- source URL or acquisition origin;
- project and version;
- loader and Minecraft version;
- exact filename;
- SHA-256 digest;
- date verified;
- reason for prohibition.

A hash must be removed or reviewed when a new build is released. Hashes must never be inferred from filenames.

## Change control

Adding a new default BLOCK rule requires all of the following:

1. a precise, machine-readable signal;
2. a reproducible fixture;
3. a regression test proving the block;
4. a negative test covering a plausible legitimate case;
5. review for false-positive breadth;
6. documentation of the rule and migration effect;
7. a versioned policy update.

New names discovered in the ecosystem should normally enter the warning list first. Promotion from WARNING to BLOCK requires evidence stronger than branding or descriptive text.

## Existing configuration migration

NeoForge does not overwrite an existing `config/antifullbright-client.toml` merely because compiled defaults changed. Users upgrading from the beta defaults should either:

1. back up and delete the existing file so NeoForge regenerates it; or
2. edit it manually:
   - set `failClosed = false`;
   - remove `assets/minecraft/shaders/core/lightmap` from `blockedResourcePackPaths`;
   - retain the two approved OptiFine/MCPatcher lightmap prefixes.

Controlled installations that intentionally require fail-closed behavior may keep `failClosed = true`, but that is a deployment override rather than the public default.

## Acceptance criteria

This policy is accepted when:

- compiled defaults match this document;
- unit tests lock the approved block and non-block boundaries, including root-prefix matching;
- clean client startup still succeeds;
- the prohibited OptiFine lightmap fixture still blocks at startup and runtime;
- oversized metadata produces an explicit warning or block according to `failClosed`;
- all project workflows pass at the exact policy head.
