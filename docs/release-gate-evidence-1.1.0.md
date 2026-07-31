# Stable 1.1.0 Release-Gate Evidence

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#5`
- Issue ledger: `#4`
- Final tested implementation head: `0d699920228de6ea54f243c9e6c245a0a0361e24`
- Base: `main@7b44910100e2d54e8aeb807ae53259e74a123c80`
- Current version: `1.1.0-beta.1`
- Stable publication authorization: none

This document records technical release-gate evidence. It does not change the project version, create a tag, or authorize publication.

## Exact-head workflow summary

All required workflows passed on the final tested implementation head:

| Workflow | Run | Result |
| --- | ---: | --- |
| Build | `30657403023` | success |
| GameTest | `30657402940` | success |
| Packaged Server | `30657402926` | success |
| External Runtime | `30657402954` | success |
| Cross-platform Scanner | `30657402933` | success |

## Real block-break event to kick enforcement

The `External Runtime` workflow contains a `break-event-enforcement` job using a fresh packaged NeoForge `21.1.235` server and an actual Minecraft `1.21.1` protocol client.

Procedure:

1. install a fresh official NeoForge server and the generated AntiFullbright beta JAR;
2. start and stop the server once to generate the world/server configuration;
3. configure a deterministic fixture requiring one continuous second, two counted blocks, and a kick at warning level one;
4. force-load the origin fixture area independently of the randomized world spawn;
5. create a sealed stone room below `maximumY` with zero block and sky light and fail immediately if the fixture commands encounter an unloaded position;
6. connect `BreakEventBot`, teleport it into the room, and have it break two real stone blocks through normal client digging packets;
7. require the server to emit one dedicated evidence record with `action = kick` and require the protocol client to receive the production kick message.

Observed exact-head evidence:

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
- Artifact ID: `8803848200`
- Artifact digest: `sha256:e5945bf4517d5ce4ce55cb3158897209b0245a60be07143c9d0043bba425c285`
- Generated JAR SHA-256: `fa2b69cea9b76446d40c20c21ad9d95bde8b04bb1f560eb56ebd7fec3a228192`

### Fixture reliability correction

An intermediate exact-head rerun exposed that Minecraft's randomized world spawn can leave the origin fixture chunks unloaded. The original script then attempted `/fill` at the origin and the protocol client observed natural world blocks instead of the intended room. This was a test-fixture failure, not an AntiFullbright enforcement failure.

The final script explicitly force-loads the origin area before construction, rejects the server's `That position is not loaded` response, and removes the force-load after evidence collection. The corrected exact head passed the complete event-to-kick job.

## Cross-platform scanner and watcher tests

The dedicated matrix ran the scanner, `ResourcePackWatcher`, expanded bilingual message assertions, and documentation security assertions on all three hosted operating systems.

| Platform | Result | Artifact ID | Artifact digest |
| --- | --- | ---: | --- |
| Ubuntu latest | success | `8803802938` | `sha256:a731bf7edcb3793beffa8257c00961c550380f19607f15c44dbd10200662ae7a` |
| Windows latest | success | `8803834847` | `sha256:2e2f3ec6c7877bd77c0c240d30044c30e6140a3ac32b15fa8b08e6468d7995a3` |
| macOS latest | success | `8803793609` | `sha256:56f94e8d7d8a23536af41785fe92033881443dc57ce76569206bb8e761272c00` |

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

The same final tested implementation head also passed:

- launcher-managed clean client startup and pre-existing prohibited-pack startup block;
- live resource-pack mutation detection through the normal Minecraft disconnect/screen route;
- fresh packaged dedicated-server startup with GameTest isolation;
- 24/24 required GameTests;
- real same-world server restart persistence.

External Runtime artifacts on the final tested implementation head:

| Artifact | ID | Digest |
| --- | ---: | --- |
| `launcher-client-evidence` | `8803823365` | `sha256:b5fde73a91c922fb07082dee0172100ca6eff1c1a490a98d209062ae7c48df25` |
| `restart-persistence-evidence` | `8803831383` | `sha256:ce48975a42954c4fa57468cfb45f733f748eab5dac40057ac80e20ff1e5a6088` |

## Gate decision

Completed technical gates:

- real packet / NeoForge break-event / warning / evidence / kick path;
- deterministic and spawn-independent enforcement fixture;
- Linux, Windows, and macOS scanner and watcher regression matrix;
- expanded bilingual message assertions;
- automated security-disclosure regression assertions.

Still excluded from this evidence phase:

- version transition from `1.1.0-beta.1` to `1.1.0`;
- final stable artifact hash and release-candidate evidence after that version transition;
- tag or release creation;
- GitHub, Modrinth, or CurseForge publication;
- complete graphical client runtime evidence on Windows and macOS.
