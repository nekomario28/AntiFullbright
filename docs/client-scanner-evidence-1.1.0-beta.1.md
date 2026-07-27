# Client Scanner Evidence — 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- Exact evidence head: `521202557b1668f487c44f0339d8b530c23cdb11`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- PR state at evidence capture: Draft
- Merge authorization: none
- Stable release authorization: none

This document records evidence for the beta implementation. It does not claim tamper-proof client attestation and does not authorize a merge or stable release.

## Automated build and runtime evidence

### Build workflow

GitHub Actions run: `30290378662`

The run completed successfully at exact head `521202557b1668f487c44f0339d8b530c23cdb11` and covered:

- Java 21 setup
- Gradle wrapper validation
- production compilation
- test compilation
- JUnit execution
- full Gradle `build`
- development dedicated-server startup
- physical client startup under Xvfb and software rendering
- runtime resource-pack mutation and WatchService rescan
- physical client startup blocking with a prohibited pack already present

Artifacts:

| Artifact | Artifact ID | Artifact digest |
| --- | ---: | --- |
| `antifullbright` | `8662545895` | `sha256:eea283d91915661a379c202a1b05b745e6aef1b1806cdd7fbb73845602e79828` |
| `gradle-build-log` | `8662545685` | `sha256:f0774858a398e5db4958b2a6c932c03ad7efb245051fadcfbe773d7d9c3bfd6f` |
| `dedicated-server-smoke-log` | `8662569374` | `sha256:4cf56d47e203886fcecfbec6475e38ac6341292fe747e093af5c8f7836b5fc63` |
| `physical-client-resourcepack-mutation-log` | `8662583800` | `sha256:9eecf4dddf893d689d01658d02ce2b5633d6c4d75c2ffd0d7affc8d2f8380064` |
| `physical-client-startup-block-log` | `8662595650` | `sha256:64fd11b353d202c6dd42f5f8f49c7f757af35f802c4b9bbf3dbcceef007e8bf8` |

### Packaged-server workflow

GitHub Actions run: `30290378382`

The separate packaged-server gate completed successfully at the same exact head. It:

1. built `antifullbright-1.1.0-beta.1.jar`;
2. downloaded the official NeoForge `21.1.235` installer;
3. verified the installer against the Maven-hosted SHA-256 file;
4. installed a fresh NeoForge server outside the development run directory;
5. copied only the generated AntiFullbright JAR into its `mods` directory;
6. started the packaged server and required the Minecraft and AntiFullbright readiness markers.

Evidence values:

- Verified NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`
- Generated AntiFullbright JAR SHA-256: `26774030e780001cf6f741a99667e8249b3cdfb7f2f19064bec0eb9d290ff23d`
- Packaged-server artifact ID: `8662570558`
- Packaged-server artifact digest: `sha256:371939b7b8e08351a4ddc6e264c572e39004b367644fcac9ee6c9ad713084da8`

Required runtime markers were present:

```text
Anti Fullbright 1.1.0-beta.1 (antifullbright)
AntiFullbright dark-mining detection is ready
Done (10.614s)! For help, type "help"
```

## Verified behavior

### Clean client startup

The physical client reached LWJGL initialization, reported a clean client content scan, and started recursive resource-pack watching. The fixture contained no mods or resource packs at initial scan time.

### Runtime resource-pack mutation

After the clean startup and watcher-ready markers, CI created:

```text
run/client/resourcepacks/ci-prohibited-lightmap.zip
```

The ZIP contained:

```text
assets/minecraft/optifine/lightmap/world0.png
```

The client then emitted both required markers:

```text
Resource-pack rescan completed with blocking findings:
AntiFullbright blocked local content while the client was running.
```

This verifies the requested create/change detection path, full rescan, prohibited-path classification, and safe runtime blocking path without `System.exit`.

### Startup blocking

The same prohibited resource pack remained in place for a new physical-client process. Client setup produced the exact blocking rule `blocked_pack_path` and did not reach a clean startup result.

### Resource-pack root recreation

A WatchService regression test deletes the entire watched `resourcepacks` directory, recreates it, and then writes a new file inside it. The parent-directory recovery watch re-registers the recreated root and observes the nested change.

### Dedicated-server class separation

Both the development server and fresh packaged server started without client-class loading failures. The common entrypoint does not directly reference the client scanner entrypoint.

## Unit-test coverage

The current JUnit suite verifies:

- exact NeoForge/Forge `[[mods]]` Mod ID produces `BLOCK`;
- a blocked Mod ID used only in `[[dependencies.*]]` does not replace the declared Mod ID;
- exact Fabric root `id` produces `BLOCK`;
- a nested Fabric custom `id` does not replace the root Mod ID;
- exact Quilt `quilt_loader.id` produces `BLOCK`;
- a harmless description containing `fullbright` produces `WARNING` rather than `BLOCK`;
- prohibited lightmap path produces `BLOCK`;
- only the exact running AntiFullbright archive path is ignored;
- malformed archives differ correctly between fail-open and fail-closed policies;
- deletion and recreation of the watched resource-pack root restores nested change notifications.

## Remaining limitations and gates

The following are not proven by this evidence:

- A normal client mod cannot prevent a user from deleting or modifying the scanner.
- No server-side attestation or cryptographic proof of a client scan exists.
- Client setup scanning does not guarantee that every other mod was prevented from running earlier initialization code.
- The physical-client gates use the NeoGradle development launch environment. The generated JAR itself is proven on a fresh packaged dedicated server, but not yet in a separately installed launcher-managed client profile.
- No Windows or macOS runtime evidence is recorded.
- No production server policy has yet fixed a canonical allowed-Mod manifest or complete blocked-ID/hash registry.
- The project icon is not yet present.

## Next release-readiness gate

Before changing the version to stable `1.1.0` or marking PR #1 ready for review:

1. install the exact generated JAR in a normal NeoForge `21.1.235` client profile;
2. record a clean launch and one startup-block fixture outside the development environment;
3. add and verify the project icon if it is intended for the release;
4. review the default policy values and privacy disclosure;
5. perform a final exact-head review and rerun both workflows.
