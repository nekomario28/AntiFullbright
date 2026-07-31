# Stable 1.1.0 Release-Gate Evidence

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#5`
- Issue ledger: `#4`
- Evidence head: `d86a042fdff36d7523073f701060c163638900cb`
- Base: `main@7b44910100e2d54e8aeb807ae53259e74a123c80`
- Current version: `1.1.0-beta.1`
- Stable publication authorization: none

This document records technical release-gate evidence. It does not change the project version, create a tag, or authorize publication.

## Exact-head workflow summary

All required workflows passed on the evidence head:

| Workflow | Run | Result |
| --- | ---: | --- |
| Build | `30656345311` | success |
| GameTest | `30656345376` | success |
| Packaged Server | `30656346160` | success |
| External Runtime | `30656345394` | success |
| Cross-platform Scanner | `30656345395` | success |

## Real block-break event to kick enforcement

The `External Runtime` workflow now contains a `break-event-enforcement` job using a fresh packaged NeoForge `21.1.235` server and an actual Minecraft `1.21.1` protocol client.

Procedure:

1. install a fresh official NeoForge server and the generated AntiFullbright beta JAR;
2. start and stop the server once to generate the world/server configuration;
3. configure a deterministic fixture requiring one continuous second, two counted blocks, and a kick at warning level one;
4. create a sealed stone room below `maximumY` with zero block and sky light;
5. connect `BreakEventBot`, teleport it into the room, and have it break two real stone blocks through normal client digging packets;
6. require the server to emit one dedicated evidence record with `action = kick` and require the protocol client to receive the production kick message.

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

The client log contains the first completed break and the received production disconnect reason. The server log and dedicated JSONL evidence contain the same kick decision. This exercises the real packet-to-NeoForge `BlockEvent.BreakEvent` path, the registered AntiFullbright handler, warning progression, evidence logging, and actual connection disconnect.

- Artifact: `break-event-enforcement-evidence`
- Artifact ID: `8803442552`
- Artifact digest: `sha256:52fb2f0bb0cfc5031195c1cef9a8feb445b52645a60aaa6b14efc390be044cad`
- Generated JAR SHA-256: `fa2b69cea9b76446d40c20c21ad9d95bde8b04bb1f560eb56ebd7fec3a228192`

## Cross-platform scanner and watcher tests

The dedicated matrix ran the scanner, `ResourcePackWatcher`, expanded bilingual message assertions, and documentation security assertions on all three hosted operating systems.

| Platform | Result | Artifact ID | Artifact digest |
| --- | --- | ---: | --- |
| Ubuntu latest | success | `8803422751` | `sha256:1f7e51136207dba81f540cdd1aba4b1f2aebbfe707e5a0215c104b05bab8f7aa` |
| Windows latest | success | `8803566743` | `sha256:479e2c7d4b76663f72c4341b7791f68a7f4b274a178f4bda8912a063821e4ceb` |
| macOS latest | success | `8803496116` | `sha256:0730e702718985e2edf67a249bb57d4c6401b23ab5fc5eae3666f695247b9d17` |

Covered behavior includes:

- exact Mod ID and resource-pack path classification;
- malformed and oversized-content fail-open/fail-closed behavior;
- recursive watcher notifications;
- deletion and recreation of the `resourcepacks` root;
- nested-directory changes after watcher recovery;
- English and Japanese warning, kick, operator, status, debug, reset, setwarning, reload, and failure text;
- retention of the non-attested, removable, locally controlled scanner disclosure.

## Cross-platform limitation boundary

The Windows and macOS matrix proves Java scanner, filesystem watcher, localization, and documentation behavior on those operating systems. It does not claim a complete graphical Minecraft launcher session or blocking-screen rendering on Windows or macOS.

The independently launcher-managed client profile, startup block, and runtime resource-pack mutation tests remain Linux/Xvfb evidence. A manual or automated graphical Windows/macOS client launch may be added as supplementary evidence, but this document does not silently treat the unit-level matrix as equivalent to full client runtime verification.

## Existing runtime evidence retained

The same exact head also passed:

- launcher-managed clean client startup and pre-existing prohibited-pack startup block;
- live resource-pack mutation detection through the normal Minecraft disconnect/screen route;
- fresh packaged dedicated-server startup with GameTest isolation;
- 24/24 required GameTests;
- real same-world server restart persistence.

External Runtime artifacts on this head:

| Artifact | ID | Digest |
| --- | ---: | --- |
| `launcher-client-evidence` | `8803432578` | `sha256:7db543a93222558f57da4169710eb15f50fc725022eb06db88809ce1cc257b52` |
| `restart-persistence-evidence` | `8803425945` | `sha256:7f061a1d1e798db493e61773e9b6229e969a0678ce20d85beddbbe2aefc1fee6` |

## Gate decision

Completed technical gates:

- real packet / NeoForge break-event / warning / evidence / kick path;
- Linux, Windows, and macOS scanner and watcher regression matrix;
- expanded bilingual message assertions;
- automated security-disclosure regression assertions.

Still excluded from this evidence phase:

- version transition from `1.1.0-beta.1` to `1.1.0`;
- final stable artifact hash and release-candidate evidence after that version transition;
- tag or release creation;
- GitHub, Modrinth, or CurseForge publication;
- complete graphical client runtime evidence on Windows and macOS.
