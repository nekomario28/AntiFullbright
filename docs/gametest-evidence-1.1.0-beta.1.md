# GameTest Evidence — AntiFullbright 1.1.0-beta.1

## Status

- Repository: `nekomario28/AntiFullbright`
- Pull request: `#1`
- Branch: `agent/client-content-scanner`
- Audited implementation head: `bad7a6533921b0fd572865f55b11fa73b97549f9`
- Base: `main@b39b39e6f7886216d2bf5db9393eec71fccb1871`
- Candidate version: `1.1.0-beta.1`
- PR state: Draft
- Merge authorization: none
- Stable release authorization: none

This record covers the first isolated NeoForge GameTest phase. It does not claim full behavioral coverage of dark-mining detection.

## GameTest workflow

- GitHub Actions run: `30340650023`
- Head SHA: `bad7a6533921b0fd572865f55b11fa73b97549f9`
- Result: success
- Artifact: `gametest-log`
- Artifact ID: `8680904958`
- Artifact digest: `sha256:12f7543f0c2218872f8462fad9556835401efcf95e683ab3601b3b776f33e202`

Required markers in the saved log:

```text
Enabled Gametest Namespaces: [antifullbright]
Registered AntiFullbright GameTests
3 tests are now running
3 GAME TESTS COMPLETE
All 3 required tests passed :)
Game test server shutting down
BUILD SUCCESSFUL
```

The three tests completed in approximately 0.5 seconds after the GameTest server reached readiness.

## Executed tests

### Counted-block tag

Verifies that `minecraft:stone` is present in:

```text
antifullbright:dark_mining_counted_blocks
```

This proves the built-in block tag was loaded into the GameTest server registry.

### Light-source item tag

Verifies that `minecraft:torch` is present in:

```text
antifullbright:dark_mining_light_sources
```

This proves the built-in item tag was loaded into the GameTest server registry.

### Administrator command registration

Verifies that the root command dispatcher contains:

```text
darkmining
```

This proves `/darkmining` was registered in the running GameTest server.

## Isolated structure template

The tests use the test-only structure:

```text
src/gameTest/resources/data/antifullbright/structure/empty.nbt
```

Properties:

- namespace: `antifullbright`
- template ID: `antifullbright:empty`
- dimensions: `1 x 1 x 1`
- Minecraft data version: `3955` for Minecraft `1.21.1`
- compressed NBT SHA-256: `73422112d58c01d2493dc6ceb1ad6e527ac877965d599aff7254f3bc376297ae`

The GameTest run enables only the `antifullbright` test namespace. It does not depend on a presumed `minecraft:empty` template or enable unrelated test namespaces.

## Test-source isolation

The test Java source and structure are located under `src/gameTest` and are not included in the production beta JAR.

The Packaged Server workflow checks the generated JAR entry list and fails if it contains any of:

```text
ServerContractGameTests
src/gameTest
data/antifullbright/structure/empty.nbt
```

The isolation check passed.

## Packaged production verification

- GitHub Actions run: `30340650097`
- Result: success
- Artifact: `packaged-server-evidence`
- Artifact ID: `8680923779`
- Artifact digest: `sha256:cc6f86e051e61441c29cc2a2d077e270ec41759cd162b9c4d11fdc55ffd08bf8`
- Generated JAR SHA-256: `bcbd81780e9212c66e4f6b8e2c95b480cb06267708ad32281ee9f6331b5efc0b`
- Verified NeoForge installer SHA-256: `58edd322dc3cbbcd5c75d9a44f93d01211fda2953665483077ddd41fbecf942c`

The generated JAR was installed into a fresh official NeoForge `21.1.235` server. The server reached both AntiFullbright and Minecraft readiness markers.

The packaged-server gate also fails if the normal server log contains:

```text
Registered AntiFullbright GameTests
```

No such marker was present. GameTest registration remained disabled in the normal packaged server.

## Regression verification

The general Build workflow also passed at the same head:

- GitHub Actions run: `30340650027`
- JUnit and full Gradle build: success
- development dedicated server: success
- physical client clean startup: success
- runtime resource-pack mutation block: success
- startup resource-pack block: success

Relevant artifacts:

| Artifact | ID | Digest |
| --- | ---: | --- |
| `antifullbright` | `8680902561` | `sha256:3df07159c7ecc0d79d3ef2023d6184a669bfc80b7b18cfdb5ee8d55a5d380d86` |
| `gradle-build-log` | `8680902220` | `sha256:f667456a2b0c70a9d58c9cbd303221bc4e63b5d07c44834c10f1f5a1426f170b` |
| `dedicated-server-smoke-log` | `8680923496` | `sha256:db0b00b74e3cf07e6426b3f81cddd04b4e8495e8071aa957433f99da428ad5a7` |
| `physical-client-resourcepack-mutation-log` | `8680938953` | `sha256:363113ecc879651f855b9a9d3e7c31c8278881bfb670d18f073b8959452f18d2` |
| `physical-client-startup-block-log` | `8680949718` | `sha256:7e0c0e1239682048adca30cae93d273d359048d4458385e9b9aba79ea26d16fb` |

## What this phase proves

This phase proves that, on NeoForge `21.1.235` and Minecraft `1.21.1`:

- the isolated GameTest source set compiles;
- the test registration bridge is active only when the GameTest system property is enabled;
- exactly three required server-contract tests start and pass;
- the built-in tags and command registration are available in a real GameTest server;
- the test-only structure loads correctly;
- test-only Java and NBT assets do not leak into the release JAR;
- normal packaged-server startup does not activate GameTests;
- existing client scanner and server startup gates still pass.

## Remaining GameTest coverage

The following production behavior is not yet covered by GameTest:

- continuous dark-mining duration accumulation;
- counted block threshold behavior;
- warning issuance and warning-level progression;
- light, inactivity, death, logout, dimension-change, and teleport resets;
- Night Vision, underwater, operator, creative, spectator, and FakePlayer exclusions;
- torch-holding grace-period expiration;
- player-placed block exclusion and expiration;
- SavedData persistence and warning decay;
- `/darkmining reload`, status, reset, setwarning, and debug command semantics.

Further GameTests should be added only after exposing a deterministic test seam or fixture that does not duplicate the production detector logic inside the tests.
