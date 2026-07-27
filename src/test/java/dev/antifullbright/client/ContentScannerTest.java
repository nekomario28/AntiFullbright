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
    void exactBlockedModIdProducesBlockingFinding() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("renamed-helper.jar"), Map.of(
                "META-INF/neoforge.mods.toml",
                "[[mods]]\nmodId=\"fullbright\"\nversion=\"1\"\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, policy(true));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("blocked_mod_id")));
    }

    @Test
    void exactFabricRootIdProducesBlockingFinding() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        writeZip(mods.resolve("fabric-helper.jar"), Map.of(
                "fabric.mod.json",
                "{\"schemaVersion\":1,\"id\":\"fullbright\",\"version\":\"1\"}"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, policy(true));

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

        ContentScanner.Report report = ContentScanner.scanMods(mods, policy(true));

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

        ContentScanner.Report report = ContentScanner.scanMods(mods, policy(true));

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

        ContentScanner.Report report = ContentScanner.scanMods(mods, policy(true));

        assertFalse(report.hasBlockingFindings());
        assertTrue(report.hasWarnings());
        assertTrue(report.findings().stream()
                .anyMatch(finding -> finding.rule().equals("suspicious_metadata")));
    }

    @Test
    void prohibitedLightmapPathProducesBlockingFinding() throws IOException {
        Path packs = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        writeZip(packs.resolve("innocent-name.zip"), Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"test\"}}",
                "assets/minecraft/optifine/lightmap/world0.png", "not-a-real-png"
        ));

        ContentScanner.Report report = ContentScanner.scanResourcePacks(packs, policy(true));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("blocked_pack_path")));
    }

    @Test
    void exactIgnoredArchivePathIsNotScanned() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        Path ownArchive = mods.resolve("antifullbright.jar");
        writeZip(ownArchive, Map.of(
                "META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"fullbright\"\n"
        ));

        ContentScanner.Report report = ContentScanner.scanMods(mods, policy(true), Set.of(ownArchive));

        assertEquals(0, report.modsScanned());
        assertTrue(report.clean());
    }

    @Test
    void malformedArchiveBlocksOnlyWhenFailClosed() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        Files.writeString(mods.resolve("broken.jar"), "not a zip", StandardCharsets.UTF_8);

        ContentScanner.Report closed = ContentScanner.scanMods(mods, policy(true));
        ContentScanner.Report open = ContentScanner.scanMods(mods, policy(false));

        assertTrue(closed.hasBlockingFindings());
        assertFalse(open.hasBlockingFindings());
        assertTrue(open.hasWarnings());
    }

    private static ContentScanner.Policy policy(boolean failClosed) {
        return new ContentScanner.Policy(
                Set.of("fullbright"),
                Set.of("fullbright"),
                Set.of("fullbright", "nightvision"),
                Set.of("assets/minecraft/optifine/lightmap/"),
                Set.of(),
                Set.of(),
                10_000,
                1_048_576,
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
