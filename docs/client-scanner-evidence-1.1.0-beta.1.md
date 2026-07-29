# Client Scanner Evidence — AntiFullbright 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- Tested implementation head: `1bf1fce7f7078817546d6deb7efdc39703c71ee4`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- PR state: Draft
- Merge authorization: none
- Stable release authorization: none

This document records beta implementation evidence. It does not claim tamper-proof client attestation and does not authorize merge or stable release.

## Exact-head workflow summary

All required workflows passed at `1bf1fce7f7078817546d6deb7efdc39703c71ee4`:

| Workflow | Run | Result |
| --- | ---: | --- |
| Build | `30488049987` | success |
| GameTest | `30488049916` | success |
| Packaged Server | `30488049932` | success |
| External Runtime | `30488049969` | success |

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

Build run `30488049987` passed at the same tested implementation head and retained the NeoGradle physical-client gates:

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
- configured prohibited resource-pack paths block ZIP or unpacked packs;
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

The default policy treats exact Mod IDs, exact configured hashes, prohibited pack paths, and configured fail-closed scan errors as blocking evidence. Ambiguous token matches remain warning-only. No canonical production-server blocked-ID/hash registry has been approved.

## Security limits

This evidence does not change the following architectural limits:

- a user can remove or modify a normal client-side scanner;
- no server-side cryptographic attestation proves that a scan occurred;
- client setup scanning cannot guarantee that every other mod executed no earlier initialization code;
- a malicious modified client can falsify local behavior or logs;
- no Windows or macOS external-profile runtime evidence is recorded.

## Remaining release gates

Before stable `1.1.0` or Ready for Review:

1. upload and verify the intended project icon if it will ship;
2. approve a canonical production policy or explicitly keep the scanner user-configured;
3. review the exact tested implementation and updated evidence;
4. decide whether a manual official-Mojang-Launcher GUI launch is desired in addition to the completed independent launcher-managed profile test.

The previous requirement for an external launcher-managed clean launch and startup-block fixture is now satisfied.