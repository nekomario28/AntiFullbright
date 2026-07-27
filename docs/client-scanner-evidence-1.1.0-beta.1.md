# Client Scanner Evidence — 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- Exact evidence head: `6efbe397a560053e53dabb30cfabab9dfe546cec`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- PR state at evidence capture: Draft
- Merge authorization: none
- Stable release authorization: none

This document records evidence for the beta implementation. It does not claim tamper-proof client attestation and does not authorize a merge or stable release.

## Automated build and runtime evidence

### Build workflow

GitHub Actions run: `30290859638`

The run completed successfully at exact head `6efbe397a560053e53dabb30cfabab9dfe546cec` and covered:

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
| `antifullbright` | `8662724516` | `sha256:e6f92cc529087881baf8c0da09bca25cb083f2c795423931053936a98a0973f9` |
| `gradle-build-log` | `8662724189` | `sha256:c70cbcc8c5672adf1f225723ecb6e45a0cca992762dfd7647a98bfced307f6b4` |
| `dedicated-server-smoke-log` | `8662749046` | `sha256:3225508847a5cfd178965db271167c355f3dbe51adb6e57b1470adb858db6436` |
| `physical-client-resourcepack-mutation-log` | `8662764503` | `sha256:cf7755fba29c8b0f9b37fc8aeeb4514fa1a1a1a58c16c79c0373e680c5d43366` |
| `physical-client-startup-block-log` | `8662776118` | `sha256:5bcbc96a8a0280812792c0f841285cc32468dc70e90e1144d476b26b4c1c8cb8` |

### Packaged-server workflow

GitHub Actions run: `30290859522`

The separate packaged-server gate completed successfully at the same exact head. It:

1. built `antifullbright-1.1.0-beta.1.jar`;
2. downloaded the official NeoForge `21.1.235` installer;
3. verified the installer against the Maven-hosted SHA-256 file;
4. installed a fresh NeoForge server outside the development run directory;
5. copied only the generated AntiFullbright JAR into its `mods` directory;
6. started the packaged server and required the Minecraft and AntiFullbright readiness markers.

Evidence values:

- Verified NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`
- Generated AntiFullbright JAR SHA-256: `ca140594dce51ac2f6bad2eeae8354c7864ac6b8ee6cbeebd5b4383f18da7ce4`
- Packaged-server artifact ID: `8662744224`
- Packaged-server artifact digest: `sha256:c54c984ec723f8d9b0a31e245794cf98974f2f56f8a5487dd7900959eae3045e`

Required runtime markers were present:

```text
Anti Fullbright 1.1.0-beta.1 (antifullbright)
AntiFullbright dark-mining detection is ready
Done (9.123s)! For help, type "help"
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

### Resource-pack root recreation and overflow recovery

A WatchService regression test deletes the entire watched `resourcepacks` directory, recreates it, and then writes a new file inside it. The parent-directory recovery watch re-registers the recreated root and observes the nested change. An `OVERFLOW` event also re-registers the current root before the full rescan, preventing a lost recreation event from leaving the watcher detached.

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

## Policy and privacy review

The beta policy remains client-local and user-editable. It is not represented as a server-enforced policy because no handshake or attestation exists.

The current implementation contains no client payload that transmits scanned contents, filenames, paths, hashes, or classification results. Findings remain in local logs and the blocking screen. Because local paths can appear in logs or crash reports, the README now instructs users to review and redact private path components before sharing evidence.

The default policy keeps exact identifiers, hashes, prohibited paths, and fail-closed errors as blocking evidence. Ambiguous token matches remain warnings only. A production server's canonical blocked-ID/hash registry is still not approved and must not be inferred from the beta defaults.

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
4. approve the canonical production policy or explicitly keep the scanner user-configured;
5. perform a final exact-head review and rerun both workflows.
