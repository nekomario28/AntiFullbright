# Stable 1.1.0 Release-Gate Evidence

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#6`
- Issue ledger: `#4`
- Candidate version: `1.1.0`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- Stable publication authorization: none

This document records the stable-candidate evidence contract and verified artifact identity. The authoritative final head SHA and final workflow run IDs are recorded in PR #6 and Issue #4 after the final exact-head run. They are intentionally not written back into this file after verification, because doing so would create a new, unverified commit.

## Verified stable artifact

- Filename: `antifullbright-1.1.0.jar`
- JAR SHA-256: `4cc843c0c39348203e3ffe30ab4dbd6c33a9eb0f6d2907913d005b89a80588fe`
- GitHub Build artifact: `antifullbright`
- Build artifact ID: `8818250498`
- Build artifact digest: `sha256:e5918078f5a0e1cf83a9f1d5dd3734a9b30f29bb61237da0e8373c8d7ee1cabd`

The GitHub-generated JAR and an independently rebuilt offline JAR were byte-for-byte identical. The archive passed ZIP integrity checks, contains `META-INF/neoforge.mods.toml` and the registered `antifullbright.png` icon, and contains no GameTest-only class, structure, or `.snbt` entry.

## Required exact-head workflow set

The release candidate must pass all of the following on the same PR head:

- Build;
- GameTest;
- Packaged Server;
- External Runtime;
- Cross-platform Scanner on Ubuntu, Windows, and macOS.

The first exact stable-candidate run completed successfully with:

| Workflow | Run | Result |
| --- | ---: | --- |
| Build | `30699184775` | success |
| GameTest | `30699184780` | success |
| Packaged Server | `30699184797` | success |
| External Runtime | `30699184778` | success |
| Cross-platform Scanner | `30699184783` | success |

A later documentation or workflow-label correction requires the same complete workflow set to pass again. The final authoritative rerun is recorded in PR #6 and Issue #4 without modifying the tested repository head.

## Real block-break event to kick enforcement

The `External Runtime` workflow includes a `break-event-enforcement` job using a packaged NeoForge server and an actual Minecraft `1.21.1` protocol client.

Procedure:

1. install NeoForge `21.1.235` and the generated stable candidate JAR;
2. configure one continuous second, two counted blocks, and a kick at warning level one;
3. force-load the deterministic fixture area independently of randomized world spawn;
4. create a sealed stone room below `maximumY` with zero block and sky light;
5. connect `BreakEventBot` and break two real stone blocks through normal client digging packets;
6. require one JSONL evidence record with `action = kick` and require the client to receive the production disconnect reason.

Observed evidence:

```text
playerName=BreakEventBot
warningCount=1
continuousMiningSeconds=8
countedBlocks=2
eyeBlockLight=0
eyeSkyLight=0
brokenBlockLight=0
brokenSkyLight=0
lastBrokenBlock=minecraft:stone
action=kick
```

- Artifact: `break-event-enforcement-evidence`
- Artifact ID: `8818268561`
- Artifact digest: `sha256:558e2b3ab8badb3cd1bfca1cce82b881f14829491292254e7190b7ac21bcc9fd`

This exercises the real client packet, NeoForge `BlockEvent.BreakEvent`, the registered AntiFullbright handler, warning progression, evidence logging, and actual connection termination.

## Runtime and persistence evidence

The same stable candidate passed:

- launcher-managed clean client startup;
- startup blocking for a pre-existing prohibited resource pack;
- live resource-pack mutation handling through Minecraft's normal disconnect and blocking-screen route;
- packaged dedicated-server startup;
- GameTest isolation from the production JAR;
- same-world restart persistence;
- all 24 required GameTests.

| Artifact | ID | Digest |
| --- | ---: | --- |
| `launcher-client-evidence` | `8818261215` | `sha256:a83823a3dae0dc47191e0fea466d01f6aa66786e0b039be8c6ebe0cf0cffbdb2` |
| `restart-persistence-evidence` | `8818260214` | `sha256:bdad035d46ef58f87b4ee2fe78ca774ec1ead34ebf329f36a80c7b6e6abf63b5` |
| `packaged-server-evidence` | `8818254841` | `sha256:84616c6cba59149d7534f158f57f7bcba65a5968f028abb0641e949bfa0c2a6d` |
| `gametest-log` | `8818251230` | `sha256:a2cb86998e93ef1362812b32d627e399d7c6f19d6ab6dc2b7c4e4fd77105b2b3` |

## Cross-platform scanner and watcher tests

The scanner, recursive `WatchService` handling, bilingual messages, and documentation security assertions passed on all three hosted operating systems.

| Platform | Result | Artifact ID | Artifact digest |
| --- | --- | ---: | --- |
| Ubuntu latest | success | `8818246429` | `sha256:564012e7ca59b2b29f4670bca4c592634144003e49b6ee48e7d92eed0c3cd72f` |
| Windows latest | success | `8818303417` | `sha256:73be2ae50207bfbabd8df65bfb34907738b7492bf60549cf9fc1452e68506976` |
| macOS latest | success | `8818276140` | `sha256:3cba2c995f9fbe61c980cdf7b5875cef4343fafe8191b160a2a67f7c243179d1` |

Covered behavior includes exact Mod ID and resource-pack path classification, malformed and oversized-content fail-open/fail-closed handling, recursive watcher recovery, nested changes after recovery, English/Japanese output, and retention of the non-attested client-scanner disclosure.

The matrix proves Java scanner and filesystem-watcher behavior on the three operating systems. It does not claim a complete graphical Minecraft launcher session or blocking-screen rendering on Windows or macOS.

## Independent local-container verification

The candidate source was reconstructed in an isolated Linux container. Every stable-transition file matched the GitHub PR blob SHA before execution.

- offline command: `./gradlew clean build --offline --no-daemon`;
- offline build: success in `18.57 s`;
- local JAR SHA-256: `4cc843c0c39348203e3ffe30ab4dbd6c33a9eb0f6d2907913d005b89a80588fe`;
- local and GitHub JARs: byte-for-byte identical;
- local GameTest: `24/24` passed in `23.56 s`;
- local packaged NeoForge server: loaded `Anti Fullbright 1.1.0`;
- fully automated local packet-to-kick E2E: success in `85.62 s` with the evidence values listed above.

## Publication boundary

Technical release gates do not authorize publication. Before release:

1. perform final human review of the unchanged exact candidate head and JAR;
2. obtain explicit publication authorization;
3. create the tag and release notes without rebuilding the JAR;
4. publish the exact JAR identified above;
5. verify the downloaded filename, SHA-256, metadata, icon, and release-page rendering.

Complete graphical Minecraft runtime on Windows and macOS remains supplementary, non-blocking evidence. The client scanner remains local, removable, modifiable, and non-attested.
