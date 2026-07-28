# GameTest Evidence — AntiFullbright 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- Audited GameTest implementation head: `98f47cafff513aaa68489cafd0020f41659b0f1e`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- PR state: Draft
- Merge authorization: none
- Stable release authorization: none

This record covers isolated NeoForge GameTests for server contracts and deterministic dark-mining behavior. It does not claim complete warning, persistence, or time-threshold coverage.

## GameTest workflow

- GitHub Actions run: `30344683179`
- Head SHA: `98f47cafff513aaa68489cafd0020f41659b0f1e`
- Result: success
- Artifact: `gametest-log`
- Artifact ID: `8682435847`
- Artifact digest: `sha256:ff20efd48b7180c1c8bbc9bad08415edcd857f23da7e7c8773f3b3b2cfc2a7fa`

Required markers in the saved log:

```text
Enabled Gametest Namespaces: [antifullbright]
Registered AntiFullbright GameTests
11 tests are now running
11 GAME TESTS COMPLETE
All 11 required tests passed :)
Game test server shutting down
BUILD SUCCESSFUL
```

## Executed tests

### Data-pack and command contracts

1. `minecraft:stone` is present in `antifullbright:dark_mining_counted_blocks`.
2. `minecraft:torch` is present in `antifullbright:dark_mining_light_sources`.
3. The running server command dispatcher contains the `/darkmining` root command.

### Minimal dark-mining behavior

4. A non-excluded player breaking stone in complete darkness starts a session with `countedBlocks=1`.
5. A creative player remains at `countedBlocks=0`, and the debug result identifies creative mode as the exclusion reason.
6. A player-placed stone is excluded on the first break; the same position is counted on the next break, proving that the placement exclusion is consumed once rather than becoming permanent.
7. Placing a tagged, light-emitting torch resets an established one-block dark-mining session to zero.
8. Holding a tagged torch during the first qualifying break leaves the count at zero and starts a positive light-holding grace period.
9. A deterministic Night Vision fixture remains at zero and reports Night Vision as the exclusion reason.
10. A deterministic permission-level-two operator fixture remains at zero and reports server operator as the exclusion reason.
11. A deterministic spectator fixture remains at zero and reports spectator mode as the exclusion reason.

The tests query production status and debug output rather than reproducing the detector's session counters inside the test code.

## Test-only structures

The GameTest source set contains two structures:

```text
src/gameTest/resources/data/antifullbright/structure/empty.nbt
src/gameTest/resources/data/antifullbright/structure/dark_room.nbt
```

### `antifullbright:empty`

- dimensions: `1 x 1 x 1`
- Minecraft data version: `3955`
- compressed NBT SHA-256: `73422112d58c01d2493dc6ceb1ad6e527ac877965d599aff7254f3bc376297ae`

### `antifullbright:dark_room`

- dimensions: `5 x 5 x 5`
- stone outer shell with an air interior
- Minecraft data version: `3955`
- compressed NBT SHA-256: `32e69f310fedda9dc2b8de77e64275efcd9ebd7f5314fe5abe4dbcf7f6974d73`

The behavior tests directly verify that both the player's eye position and the target block have block light `0` and sky light `0` before using the room as a darkness fixture.

The GameTest run enables only the `antifullbright` test namespace. It does not depend on a presumed `minecraft:empty` structure or enable unrelated test namespaces.

## Deterministic player fixtures

NeoForge's standard GameTest mock player is creative and has no network connection. It is used only for the creative exclusion test.

Other tests use test-only `ServerPlayer` subclasses that explicitly control the predicates consumed by production code:

- `isCreative()`
- `isSpectator()`
- permission level checks
- Night Vision presence

These fixtures avoid modifying global server configuration and avoid sending packets through a nonexistent test connection. They remain under `src/gameTest` and are not packaged.

## Test-source isolation

The Packaged Server workflow lists the generated JAR entries and fails if it contains any of:

```text
ServerContractGameTests
src/gameTest
data/antifullbright/structure/
```

The isolation check passed at the audited implementation head. The normal packaged server also fails if GameTest registration is unexpectedly activated.

## Packaged production verification

- GitHub Actions run: `30344683160`
- Head SHA: `98f47cafff513aaa68489cafd0020f41659b0f1e`
- Result: success
- Artifact: `packaged-server-evidence`
- Artifact ID: `8682446145`
- Artifact digest: `sha256:15378b938e70fe0a56336b70c202b83ef4d7f71f786b16bf78bb1dbfaffd23d5`
- Generated JAR SHA-256: `bcbd81780e9212c66e4f6b8e2c95b480cb06267708ad32281ee9f6331b5efc0b`
- Verified NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`

The generated JAR was installed into a fresh official NeoForge `21.1.235` server. Required runtime markers were present:

```text
Anti Fullbright 1.1.0-beta.1 (antifullbright)
AntiFullbright dark-mining detection is ready
Done (5.230s)! For help, type "help"
```

`Registered AntiFullbright GameTests` was absent from the normal packaged-server log.

## Regression verification

The general Build workflow also passed at the same head:

- GitHub Actions run: `30344683208`
- JUnit and full Gradle build: success
- development dedicated server: success
- physical client clean startup: success
- runtime resource-pack mutation block: success
- startup resource-pack block: success

Relevant artifacts:

| Artifact | ID | Digest |
| --- | ---: | --- |
| `antifullbright` | `8682424878` | `sha256:fc07c12c1f1bf1fac15e96d5134b72b2d5cc7f85d236cdab0cce0433e6cd09e6` |
| `gradle-build-log` | `8682424546` | `sha256:ce5e01676d02fb9ace90ab953d864072a165a5f4d2e263c4962a64b5d78bf336` |
| `dedicated-server-smoke-log` | `8682450467` | `sha256:b9a655625c61ad9874d9d6790365767df7bf8f930931f7bc3c2a823355b54b44` |
| `physical-client-resourcepack-mutation-log` | `8682468263` | `sha256:a90b08b536ec1250629f779c734b8fbac5847fec70ced8fadd24752555a097eb` |
| `physical-client-startup-block-log` | `8682480440` | `sha256:71fd57a248fd413321366aaff587bcbf7df3c3c1f7d85e3815fd174b989c91dc` |

## What this phase proves

On NeoForge `21.1.235` and Minecraft `1.21.1`, this phase proves that:

- the isolated GameTest source set compiles and registers only when its explicit test property is enabled;
- exactly eleven required tests start and pass;
- built-in tags and command registration are available in a real GameTest server;
- a qualifying dark break starts a production session;
- creative, spectator, Night Vision, and operator exclusion branches produce zero count and the expected reason;
- player-placed block exclusion is single-use;
- torch placement resets an active session;
- held-torch grace starts without incrementing the count;
- test-only Java and structure assets do not leak into the release JAR;
- normal packaged-server startup does not activate GameTests;
- existing client scanner and server startup gates remain successful.

## Remaining deterministic coverage gate

The following behavior is not yet covered because it depends on real time, mutable global configuration, persistent server state, or more complex event fixtures:

- continuous duration and minimum-block warning thresholds;
- warning issuance, kick progression, and warning decay;
- grace-period expiration after elapsed real time;
- inactivity, death, logout, dimension-change, and teleport event wiring;
- underwater and FakePlayer exclusions;
- player-placed block expiration and maximum-entry eviction;
- SavedData persistence across an actual server restart;
- `/darkmining reload`, status, reset, setwarning, and debug command semantics;
- localization assertions under both configured languages.

Further expansion should first introduce a deterministic clock/configuration seam or dedicated persistence fixture. It should not mutate shared global configuration concurrently or duplicate the production detector algorithm inside tests.
