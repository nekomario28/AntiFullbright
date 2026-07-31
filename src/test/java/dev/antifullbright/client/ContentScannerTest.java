package dev.antifullbright.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void productionDefaultsAreConservativeAndExplicit() {
        assertFalse(ClientScanConfig.DEFAULT_FAIL_CLOSED);
        assertEquals(Set.of("fullbright"), ClientScanConfig.csv(ClientScanConfig.DEFAULT_BLOCKED_MOD_IDS));
        assertEquals(Set.of(
                        "assets/minecraft/optifine/lightmap/",
                        "assets/minecraft/mcpatcher/lightmap/"),
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_BLOCKED_RESOURCE_PACK_PATHS));
        assertTrue(ClientScanConfig.DEFAULT_BLOCKED_MOD_SHA256.isEmpty());
        assertTrue(ClientScanConfig.DEFAULT_BLOCKED_RESOURCE_PACK_SHA256.isEmpty());
        assertFalse(ClientScanConfig.DEFAULT_BLOCKED_RESOURCE_PACK_PATHS.contains("shaders/core"));
        assertTrue(ClientScanConfig.DEFAULT_SUSPICIOUS_MOD_TOKENS.contains("gammautils"));
        assertTrue(ClientScanConfig.DEFAULT_SUSPICIOUS_MOD_TOKENS.contains("truefullbright"));
    }

    @Test
    void exactBlockedModIdProducesBlockingFinding() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("renamed-helper.jar"), Map.of(
                "META-INF/neoforge.mods.toml",
                "[[mods]]\nmodId=\"fullbright\"\nversion=\"1\"\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("blocked_mod_id")));
    }

    @Test
    void warningTokenModIdDoesNotBecomeAnUnreviewedBlockRule() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("renamed-helper.jar"), Map.of(
                "META-INF/neoforge.mods.toml",
                "[[mods]]\nmodId=\"gammautils\"\nversion=\"1\"\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
        assertTrue(report.hasWarnings());
    }

    @Test
    void dependencyModIdDoesNotReplaceDeclaredModId() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("compatibility-helper.jar"), Map.of(
                "META-INF/neoforge.mods.toml",
                "[[mods]]\nmodId=\"safehelper\"\nversion=\"1\"\n"
                        + "[[dependencies.safehelper]]\nmodId=\"fullbright\"\ntype=\"incompatible\"\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
        assertTrue(report.hasWarnings());
    }

    @Test
    void exactFabricRootIdProducesBlockingFinding() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("fabric-helper.jar"), Map.of(
                "fabric.mod.json",
                "{\"schemaVersion\":1,\"id\":\"fullbright\",\"version\":\"1\"}"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("blocked_mod_id")));
    }

    @Test
    void nestedFabricIdDoesNotReplaceRootModId() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("safe-fabric-helper.jar"), Map.of(
                "fabric.mod.json",
                "{\"schemaVersion\":1,\"id\":\"safehelper\",\"version\":\"1\","
                        + "\"custom\":{\"id\":\"fullbright\"}}"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
        assertTrue(report.hasWarnings());
    }

    @Test
    void exactQuiltLoaderIdProducesBlockingFinding() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("quilt-helper.jar"), Map.of(
                "quilt.mod.json",
                "{\"schema_version\":1,\"quilt_loader\":{\"id\":\"fullbright\",\"version\":\"1\"}}"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("blocked_mod_id")));
    }

    @Test
    void suspiciousDescriptionWarnsWithoutBlocking() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("safe-helper.jar"), Map.of(
                "META-INF/neoforge.mods.toml",
                "[[mods]]\nmodId=\"safehelper\"\ndescription='Disables fullbright compatibility mode'\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
        assertTrue(report.hasWarnings());
        assertTrue(report.findings().stream()
                .anyMatch(finding -> finding.rule().equals("suspicious_metadata")));
    }

    @Test
    void prohibitedOptifineLightmapPathProducesBlockingFinding() throws IOException {
        Path packs = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        writeZip(packs.resolve("innocent-name.zip"), Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"test\"}}",
                "assets/minecraft/optifine/lightmap/world0.png", "not-a-real-png"
        ));

        ContentScanner.Report report = ContentScanner.scanResourcePacks(packs, productionPolicy(false));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("blocked_pack_path")));
    }

    @Test
    void genericCoreShaderPathIsNotBlockedByProductionDefaults() throws IOException {
        Path packs = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        writeZip(packs.resolve("legitimate-core-shader.zip"), Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"visual shader pack\"}}",
                "assets/minecraft/shaders/core/lightmap.fsh", "void main() {}"
        ));

        ContentScanner.Report report = ContentScanner.scanResourcePacks(packs, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
    }

    @Test
    void nestedDocumentationLightmapPathIsNotBlockedAsAnActivePackPath() throws IOException {
        Path packs = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        writeZip(packs.resolve("documentation.zip"), Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"documentation\"}}",
                "docs/assets/minecraft/optifine/lightmap/world0.png", "example-only"
        ));

        ContentScanner.Report report = ContentScanner.scanResourcePacks(packs, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
    }

    @Test
    void oversizedMetadataWarnsOrBlocksAccordingToFailClosed() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        String metadata = "[[mods]]\ndescription=\"" + "x".repeat(2_048)
                + "\"\nmodId=\"fullbright\"\n";
        writeZip(mods.resolve("oversized-metadata.jar"), Map.of(
                "META-INF/neoforge.mods.toml", metadata
        ));

        ContentScanner.Report open = ContentScanner.scanMods(mods, productionPolicy(false, 1_024));
        ContentScanner.Report closed = ContentScanner.scanMods(mods, productionPolicy(true, 1_024));

        assertFalse(open.hasBlockingFindings());
        assertTrue(open.findings().stream().anyMatch(finding -> finding.rule().equals("text_limit")));
        assertTrue(closed.hasBlockingFindings());
        assertTrue(closed.blockingFindings().stream().anyMatch(finding -> finding.rule().equals("text_limit")));
    }

    @Test
    void exactIgnoredArchivePathIsNotScanned() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        Path ownArchive = mods.resolve("antifullbright.jar");
        writeZip(ownArchive, Map.of(
                "META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"fullbright\"\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(
                mods, productionPolicy(false), Set.of(ownArchive));

        assertEquals(0, report.modsScanned());
        assertTrue(report.clean());
    }

    @Test
    void malformedArchiveBlocksOnlyWhenFailClosed() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        Files.writeString(mods.resolve("broken.jar"), "not a zip", StandardCharsets.UTF_8);

        ContentScanner.Report closed = ContentScanner.scanMods(mods, productionPolicy(true));
        ContentScanner.Report open = ContentScanner.scanMods(mods, productionPolicy(false));

        assertTrue(closed.hasBlockingFindings());
        assertFalse(open.hasBlockingFindings());
        assertTrue(open.hasWarnings());
    }

    private static ContentScanner.Policy productionPolicy(boolean failClosed) {
        return productionPolicy(failClosed, 1_048_576);
    }

    private static ContentScanner.Policy productionPolicy(boolean failClosed, int maximumTextBytes) {
        return new ContentScanner.Policy(
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_BLOCKED_MOD_IDS),
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_SUSPICIOUS_MOD_TOKENS),
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_SUSPICIOUS_RESOURCE_PACK_TOKENS),
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_BLOCKED_RESOURCE_PACK_PATHS),
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_BLOCKED_MOD_SHA256),
                ClientScanConfig.csv(ClientScanConfig.DEFAULT_BLOCKED_RESOURCE_PACK_SHA256),
                10_000,
                maximumTextBytes,
                failClosed
        );
    }

    private static void writeZip(Path target, Map<String, String> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
