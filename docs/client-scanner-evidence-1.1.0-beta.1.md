# Client Scanner Evidence — 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- Exact evidence head: `d73681a31860ce2419ddb36677c1a3cbc0b2b414`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- PR state at evidence capture: Draft
- Merge authorization: none
- Stable release authorization: none

This document records evidence for the beta implementation. It does not claim tamper-proof client attestation and does not authorize a merge or stable release.

## Automated build and runtime evidence

### Build workflow

GitHub Actions run: `30289326327`

The run completed successfully at exact head `d73681a31860ce2419ddb36677c1a3cbc0b2b414` and covered:

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
| `antifullbright` | `8662145817` | `sha256:49b3a26f41a78111072396710cdbbd74e7d8f2c0e8c296e4613e4a95098459c1` |
| `gradle-build-log` | `8662145555` | `sha256:78f4b2d666030779a236a2e4ee78b0c941dccc683b5d7be9f6bf690ff5dccf3f` |
| `dedicated-server-smoke-log` | `8662169909` | `sha256:bc5a2ba69c72b7cb4862e095ccb785bf92922884cb539d12e424c3d15d2e5ff6` |
| `physical-client-resourcepack-mutation-log` | `8662186676` | `sha256:776e29a2231dabb91915db1ae6b4de0ef9c0e6dd169699b5ed08d578f362004d` |
| `physical-client-startup-block-log` | `8662198108` | `sha256:d84f13b1e604477850d314d1792c086b3f30b885130bd6302fcceecd4eb26ae0` |

### Packaged-server workflow

GitHub Actions run: `30289324759`

The separate packaged-server gate completed successfully at the same exact head. It:

1. built `antifullbright-1.1.0-beta.1.jar`;
2. downloaded the official NeoForge `21.1.235` installer;
3. verified the installer against the Maven-hosted SHA-256 file;
4. installed a fresh NeoForge server outside the development run directory;
5. copied only the generated AntiFullbright JAR into its `mods` directory;
6. started the packaged server and required the Minecraft and AntiFullbright readiness markers.

Evidence values:

- Verified NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`
- Generated AntiFullbright JAR SHA-256: `c0a6b418624284428a19ca0937dd609ac89264124ea1373e26f122595bfa41e3`
- Packaged-server artifact ID: `8662172242`
- Packaged-server artifact digest: `sha256:382d0b0a119d93ab0ef1b319f1f01ffa9a56fc97cd497a75f9219e215a621b86`

Required runtime markers were present:

```text
Anti Fullbright 1.1.0-beta.1 (antifullbright)
AntiFullbright dark-mining detection is ready
Done (8.272s)! For help, type "help"
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

### Dedicated-server class separation

Both the development server and fresh packaged server started without client-class loading failures. The common entrypoint does not directly reference the client scanner entrypoint.

## Unit-test coverage

The current JUnit suite verifies:

- exact blocked Mod ID produces `BLOCK`;
- a harmless description containing `fullbright` produces `WARNING` rather than `BLOCK`;
- prohibited lightmap path produces `BLOCK`;
- only the exact running AntiFullbright archive path is ignored;
- malformed archives differ correctly between fail-open and fail-closed policies.

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
