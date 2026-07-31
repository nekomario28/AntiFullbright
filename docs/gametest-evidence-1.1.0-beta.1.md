# GameTest Evidence — AntiFullbright 1.1.0-beta.1

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

This record covers the isolated GameTest suite, production-JAR isolation, packaged-server startup, and a real same-world server restart persistence test. It does not authorize merge or release.

## Exact-head workflow summary

All required workflows passed at `1bf1fce7f7078817546d6deb7efdc39703c71ee4`:

| Workflow | Run | Result |
| --- | ---: | --- |
| Build | `30488049987` | success |
| GameTest | `30488049916` | success |
| Packaged Server | `30488049932` | success |
| External Runtime | `30488049969` | success |

## GameTest workflow

- GitHub Actions run: `30488049916`
- Result: success
- Artifact: `gametest-log`
- Artifact ID: `8738512289`
- Artifact digest: `sha256:014d2ae89ff7fef7d5986b9ee723572020a79cb5b765d752c40ced5987ff9040`

Required saved-log markers:

```text
Enabled Gametest Namespaces: [antifullbright]
Registered AntiFullbright GameTests
24 tests are now running
24 GAME TESTS COMPLETE
All 24 required tests passed :)
Stopping server
```

The successful run also emitted the expected warning-threshold evidence at `continuousMiningSeconds=61`, `countedBlocks=20`, first with `warningCount=1` and then with `warningCount=2`.

## Executed tests — 24 total

### `ServerContractGameTests` — 11

1. `countedBlockTagContainsStone` — verifies `minecraft:stone` is in `antifullbright:dark_mining_counted_blocks`.
2. `lightSourceTagContainsTorch` — verifies `minecraft:torch` is in `antifullbright:dark_mining_light_sources`.
3. `darkMiningCommandIsRegistered` — verifies `/darkmining` exists in the live dispatcher.
4. `survivalBreakInCompleteDarknessStartsCountedSession` — a qualifying stone break starts a one-block session.
5. `creativePlayerIsExcludedFromDarkMiningCount` — creative remains at zero with the expected exclusion reason.
6. `playerPlacedBlockIsExcludedOnce` — first break is excluded and the next break is counted.
7. `placingTorchResetsActiveSession` — tagged light-source placement resets an active session.
8. `heldTorchStartsGraceWithoutCounting` — held torch starts positive grace without incrementing the count.
9. `nightVisionPlayerIsExcludedFromDarkMiningCount` — Night Vision remains excluded.
10. `operatorIsExcludedFromDarkMiningCount` — permission-level-two operator remains excluded.
11. `spectatorIsExcludedFromDarkMiningCount` — spectator remains excluded.

### `AdvancedDarkMiningGameTests` — 11

12. `heldTorchGraceExpiresAndThenCounts` — mining counts after the held-light grace deadline expires.
13. `inactivityResetsBeforeTheNextCount` — an inactive session is reset before the next qualifying break.
14. `expiredPlayerPlacedBlockRecordIsCounted` — an expired placement record no longer suppresses counting.
15. `thresholdIssuesFirstWarningAndResetsSession` — the configured duration and block threshold issues warning level one and resets the session.
16. `repeatedThresholdProgressesToSecondWarning` — a repeated threshold event advances the warning level to two.
17. `underwaterPlayerIsExcluded` — underwater players remain excluded.
18. `fakePlayerIsExcluded` — NeoForge `FakePlayer` remains excluded.
19. `commandTreeContainsAllAdministratorOperations` — reload, status, reset, setwarning, and debug command branches exist.
20. `explicitResetClearsActiveSession` — an explicit reset clears active session state.
21. `logoutClearsSessionAndPlacementRecord` — logout cleanup removes session and placement state.
22. `chunkUnloadClearsPlacementRecord` — chunk-unload cleanup removes placement records in that chunk.

### `PlacementCapacityGameTests` — 1

23. `oldestPlacementIsEvictedAtConfiguredMaximum` — reaching the placement-record limit evicts the oldest record and retains newer entries.

### `CommandExecutionGameTests` — 1

24. `administratorCommandsMutateAndReportWarningState` — real Brigadier execution of `setwarning`, `status`, `debug`, and `reset` mutates and reports production state correctly.

The tests query production manager state and command output rather than reproducing the detector algorithm in test code.

## Test-only structures and isolation

The isolated `src/gameTest` source set contains:

```text
src/gameTest/resources/data/antifullbright/structure/empty.nbt
src/gameTest/resources/data/antifullbright/structure/dark_room.nbt
```

- `antifullbright:empty`: `1 x 1 x 1`, data version `3955`, compressed NBT SHA-256 `73422112d58c01d2493dc6ceb1ad6e527ac877965d599aff7254f3bc376297ae`
- `antifullbright:dark_room`: `5 x 5 x 5`, sealed stone shell, data version `3955`, compressed NBT SHA-256 `32e69f310fedda9dc2b8de77e64275efcd9ebd7f5314fe5abe4dbcf7f6974d73`

The darkness tests verify block and sky light are both zero at the player eyes and broken block.

The Packaged Server workflow rejects a production JAR containing GameTest classes, `src/gameTest`, or `data/antifullbright/structure/`. It also fails if GameTest registration is activated in the normal packaged server.

## Packaged production verification

- GitHub Actions run: `30488049932`
- Result: success
- Artifact: `packaged-server-evidence`
- Artifact ID: `8738533866`
- Artifact digest: `sha256:0c341ae4c804d145d0227d9a367abad2dff29372aab2f36f7d90f5530269b5c2`
- Generated AntiFullbright JAR SHA-256: `bdfd1cd6eb07204d599df4d8bc09132ceaccffcac6028d0a2fd6ee944ecc7ff1`
- Verified official NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`

The exact generated JAR was installed into a fresh official NeoForge `21.1.235` server outside the development run directory. AntiFullbright and Minecraft readiness markers were present, and GameTest registration was absent.

## Real server restart persistence

The `External Runtime` workflow performs a real two-process restart test rather than only serializing and deserializing NBT in memory.

- GitHub Actions run: `30488049969`
- Job: `restart-persistence`
- Result: success
- Artifact: `restart-persistence-evidence`
- Artifact ID: `8738542790`
- Artifact digest: `sha256:55e8e5bd36b9c4b4b750ae8e0f50fa7f9bf74e260ff1185be5eec2b204d5ae24`
- Generated JAR SHA-256: `bdfd1cd6eb07204d599df4d8bc09132ceaccffcac6028d0a2fd6ee944ecc7ff1`
- Saved warning-data SHA-256: `11143ce55b3735601b95e0bae0fdc7917c5be462945e39037ccf54afb5037654`

Procedure and observed result:

1. Install a fresh official NeoForge server and add only the generated AntiFullbright JAR.
2. Connect an actual Minecraft protocol client named `PersistenceBot`.
3. Execute `/darkmining setwarning PersistenceBot 3` through the server console.
4. Execute `save-all flush` and stop the server normally.
5. Confirm `world/data/antifullbright_warnings.dat` exists and record its SHA-256.
6. Start a new server process using the same world directory.
7. Reconnect the same player identity and execute `/darkmining status PersistenceBot`.
8. Confirm the restored result reports `warningLevel=3`.

Both server processes reached the normal `Done (...)` marker. This closes the previous real-restart persistence gap for warning state.

## Build regression verification

Build run `30488049987` passed at the same exact head, including:

- production and test compilation;
- JUnit and full Gradle build;
- development dedicated-server startup;
- physical development client clean startup;
- runtime resource-pack mutation blocking;
- startup blocking with a prohibited resource pack already present.

Key Build artifacts:

| Artifact | ID | Digest |
| --- | ---: | --- |
| `antifullbright` | `8738503533` | `sha256:e86addea3fce207ca5d37978208c26c4390e58763baf7b47747f410cd2a1e8df` |
| `gradle-build-log` | `8738503271` | `sha256:dafcee635ccbcf27df97bdc49f7868bad71d00d1b4459c261a4dbf30c5fa6758` |
| `dedicated-server-smoke-log` | `8738525567` | `sha256:8544efe494735b58a68b660ca48ddcc0cfbf469fb13d3f3d7a822caec1f5b4ca` |
| `physical-client-resourcepack-mutation-log` | `8738540378` | `sha256:7e5cf65530c8ca82e839de6dcaba3060d2a12158a8b9fcf8a4dc08179076a0bf` |
| `physical-client-startup-block-log` | `8738551427` | `sha256:ce0909692c99b31aa739303aad4510ac6702f0b56d5208b502d12e17c3cdca29` |

## What this evidence proves

On Minecraft `1.21.1`, NeoForge `21.1.235`, and Java 21, the exact tested implementation head proves:

- all 24 isolated GameTests start and pass;
- duration and minimum-block warning thresholds are exercised;
- warning progression through level two is exercised;
- held-light grace expiration and inactivity reset are exercised;
- underwater and FakePlayer exclusions are exercised;
- placement expiration, capacity eviction, logout cleanup, and chunk-unload cleanup are exercised;
- administrator command structure and real command execution are exercised;
- warning SavedData survives a complete stop and restart using the same world;
- GameTest-only code and structures do not leak into the release JAR;
- a fresh packaged server starts with the generated JAR and without GameTest activation.

## Remaining limits

The following are still not fully proven by this phase:

- a complete end-to-end test beginning with an actual NeoForge `BlockEvent.BreakEvent` and ending in a real player kick at the configured warning limit;
- long-duration warning decay across real wall-clock time;
- localization assertions for every command and warning under both languages;
- Windows and macOS runtime behavior.

These remaining limits do not invalidate the 24-test result or the verified same-world restart persistence result.