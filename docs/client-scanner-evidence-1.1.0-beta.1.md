# Client Scanner Evidence — AntiFullbright 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- External-runtime implementation head: `1bf1fce7f7078817546d6deb7efdc39703c71ee4`
- Production policy: `antifullbright/default-v1`
- Policy document: `docs/production-blocking-policy-1.1.0.md`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- PR state: Draft
- Merge authorization: none
- Stable release authorization: none

This document records beta implementation, launcher-runtime, and approved default-policy evidence. It does not claim tamper-proof client attestation and does not authorize merge or stable release.

## Historical exact-head workflow summary

All required workflows passed at the externally tested implementation head `1bf1fce7f7078817546d6deb7efdc39703c71ee4`:

| Workflow | Run | Result |
| --- | ---: | --- |
| Build | `30488049987` | success |
| GameTest | `30488049916` | success |
| Packaged Server | `30488049932` | success |
| External Runtime | `30488049969` | success |

The final pull-request checks are the source of truth for the later policy, documentation, and icon commits.

## Approved production default policy

The approved policy is versioned as `antifullbright/default-v1`. Its decision rule is:

> BLOCK only on precise, high-confidence evidence. Treat broad names, descriptions, generic paths, and scan uncertainty as WARNING unless a controlled deployment explicitly opts into stricter behavior.

### Default BLOCK boundary

A default installation blocks only:

1. the exact authoritative loader Mod ID `fullbright`;
2. an exact administrator-configured SHA-256;
3. either precise resource-pack prefix:
   - `assets/minecraft/optifine/lightmap/`;
   - `assets/minecraft/mcpatcher/lightmap/`;
4. unreadable, malformed, or over-limit content only when `failClosed = true` is explicitly configured.

The public default is `failClosed = false`. Both distributed hash lists are empty.

### Default WARNING boundary

Warnings cover Fullbright, Gamma Bright, Gamma Utils, True Fullbright, Fullbright Utils, Resource Gamma Utils, Boosted Brightness, and Night Vision naming variants when found in filenames, archive paths, or descriptive metadata.

Names and descriptions do not become block rules merely because they are suspicious. Dependency IDs and nested custom JSON IDs are not treated as authoritative declared Mod IDs.

### Explicit false-positive boundary

The generic `assets/minecraft/shaders/core/` path is not blocked. The previous broad `assets/minecraft/shaders/core/lightmap` default was removed because a generic core-shader path is not a sufficiently specific Fullbright signature.

Existing client configuration files are not overwritten by NeoForge. The policy document and both READMEs record the required migration: regenerate the configuration or set `failClosed = false` and retain only the approved OptiFine/MCPatcher path prefixes.

## Policy regression tests

The JUnit suite now locks these production decisions:

- public `failClosed` default is false;
- exact default blocked Mod ID set is only `fullbright`;
- default blocked resource-pack paths are exactly the OptiFine and MCPatcher lightmap prefixes;
- distributed hash lists are empty;
- Gamma Utils and other discovered names remain warning tokens rather than unreviewed block rules;
- a precise OptiFine lightmap path blocks;
- a generic core-shader lightmap file does not block;
- malformed archives block only under explicit fail-closed mode;
- descriptions, dependency IDs, and nested IDs remain warning-only when appropriate.

Promotion of a new default rule from WARNING to BLOCK now requires a precise signal, positive fixture, negative fixture, false-positive review, documentation, and a versioned policy change.

## External launcher-managed client profile

The `External Runtime` workflow creates an isolated client profile through PortableMC rather than invoking NeoGradle `runClient`. PortableMC resolves the normal Minecraft `1.21.1` and NeoForge `21.1.235` launcher metadata and libraries, places the generated AntiFullbright JAR into the profile's ordinary `mods` directory, and launches the `forgeclient` target with the profile's ordinary game directory.

This is automated evidence for a launcher-managed profile outside the development run configuration. It is not a claim that the official Mojang Launcher graphical interface itself was manually clicked.

- GitHub Actions run: `30488049969`
- Job: `launcher-client`
- Result: success
- Artifact: `launcher-client-evidence`
- Artifact ID: `8738538318`
- Artifact digest: `sha256:86dbeb392a1a5d897cc8464a4fd6dc552627093e0917f885f9a20ffa53828c47`
- Generated AntiFullbright JAR SHA-256: `bdfd1cd6eb07204d599df4d8bc09132ceaccffcac6028d0a2fd6ee944ecc7ff1`

The artifact contains:

```text
launcher-client-clean.log
launcher-client-clean-game.log
launcher-client-startup-block.log
launcher-client-startup-block-game.log
launcher-client-mod.sha256
```

### Clean launch result

The isolated profile reached the real client render thread and produced all required markers:

```text
PortableMC external profile: Minecraft 1.21.1, NeoForge 21.1.235
Backend library: LWJGL version 3.3.3+5
Client content scan completed: No findings (mods=0, resourcePacks=0).
Watching resource packs for changes: .../resourcepacks
```

The scanner reports zero scanned mods because the running AntiFullbright archive is deliberately excluded from scanning itself. The first external attempt exposed that code-source-only exclusion was not sufficient under NeoForge's transformed launcher environment: the JAR filename contains `fullbright` and produced a warning against itself. The implementation now resolves its archive through NeoForge `ModList` and keeps code-source lookup as a fallback. The corrected exact head produced a clean result.

### Startup blocking result

After the clean process was terminated, CI created this pack in the same isolated profile before starting a new client process:

```text
resourcepacks/ci-prohibited-lightmap.zip
└── assets/minecraft/optifine/lightmap/world0.png
```

The second process identified the exact configured blocking path and aborted client setup:

```text
AntiFullbright blocked client setup. Content scan findings (blocks=1, warnings=0):
BLOCK resourcepack: .../ci-prohibited-lightmap.zip [blocked_pack_path] Matched: assets/minecraft/optifine/lightmap/
```

This proves that the generated JAR, when installed in an independently created launcher-managed NeoForge profile, supports both a clean launch and blocking of a prohibited resource pack already present before startup.

## Development physical-client regression gates

Build run `30488049987` passed at the same externally tested implementation head and retained the NeoGradle physical-client gates:

- clean Xvfb/software-rendered client startup;
- recursive resource-pack watcher startup;
- prohibited resource-pack creation after startup;
- WatchService rescan and runtime blocking through the normal Minecraft disconnect/screen path;
- a separate startup-block process with the prohibited resource pack already present;
- no direct `System.exit` path.

Relevant artifacts:

| Artifact | ID | Digest |
| --- | ---: | --- |
| `physical-client-resourcepack-mutation-log` | `8738540378` | `sha256:7e5cf65530c8ca82e839de6dcaba3060d2a12158a8b9fcf8a4dc08179076a0bf` |
| `physical-client-startup-block-log` | `8738551427` | `sha256:ce0909692c99b31aa739303aad4510ac6702f0b56d5208b502d12e17c3cdca29` |

## Packaged-server class separation

- Packaged Server run: `30488049932`
- Result: success
- Artifact: `packaged-server-evidence`
- Artifact ID: `8738533866`
- Artifact digest: `sha256:0c341ae4c804d145d0227d9a367abad2dff29372aab2f36f7d90f5530269b5c2`
- Verified NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`
- Generated AntiFullbright JAR SHA-256: `bdfd1cd6eb07204d599df4d8bc09132ceaccffcac6028d0a2fd6ee944ecc7ff1`

A fresh official NeoForge dedicated server started with only the generated JAR in `mods`. The common/server entrypoint did not load client-only classes, and GameTest-only classes/resources were absent from the production JAR.

## Verified scanner behavior

The implementation and automated suite verify:

- bounded read-only scanning of ordinary `mods` and `resourcepacks` locations;
- exact NeoForge/Forge `[[mods]]` Mod ID blocking;
- exact Fabric root `id` blocking;
- exact Quilt `quilt_loader.id` blocking;
- dependency or nested custom IDs are not mistaken for the declared Mod ID;
- configured SHA-256 values can block exact archives;
- configured precise resource-pack paths block ZIP or unpacked packs;
- ambiguous filename, archive-path, and metadata token matches remain warnings rather than automatic blocks;
- malformed archives follow the configured fail-open/fail-closed policy;
- the running AntiFullbright JAR is excluded using NeoForge's loaded-mod file path, with code-source fallback;
- resource-pack monitoring is recursive and debounced;
- deletion/recreation of the resource-pack root is recovered;
- WatchService `OVERFLOW` re-registers the root and performs a full rescan;
- runtime findings are handled on the Minecraft main thread through the normal disconnect/screen route;
- no direct process termination is used.

## Policy and privacy review

The beta scanner remains client-local and user-editable. It is not represented as server-enforced because there is no handshake or attestation protocol.

The scanner does not transmit scanned filenames, paths, hashes, or classification results. Findings remain in the local log and blocking screen. Local absolute paths can therefore appear in logs or crash reports; users should redact private path components before sharing them.

The canonical public default is now approved. Controlled deployments may add exact hashes, exact Mod IDs, or enable fail-closed behavior, but those local overrides are not represented as globally authoritative AntiFullbright rules.

## Security limits

This evidence does not change the following architectural limits:

- a user can remove or modify a normal client-side scanner;
- no server-side cryptographic attestation proves that a scan occurred;
- client setup scanning cannot guarantee that every other mod executed no earlier initialization code;
- a malicious modified client can falsify local behavior or logs;
- no Windows or macOS external-profile runtime evidence is recorded.

## Remaining release gates

The external launcher-managed profile, project icon, real restart persistence, and production default policy gates are complete.

Before stable `1.1.0` or Ready for Review:

1. complete the final exact-head human review;
2. confirm all required checks pass on the final policy/documentation head;
3. decide whether a manual official-Mojang-Launcher GUI launch is desired as additional, non-required evidence.
