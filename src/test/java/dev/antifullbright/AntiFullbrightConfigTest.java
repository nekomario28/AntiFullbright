package dev.antifullbright;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiFullbrightConfigTest {
    @Test
    void reloadAppliesSupportedValuesAndClampsRanges(@TempDir Path directory) throws IOException {
        String originalLanguage = AntiFullbrightConfig.LANGUAGE.get();
        boolean originalEnabled = AntiFullbrightConfig.ENABLED.getAsBoolean();
        int originalMaximumY = AntiFullbrightConfig.MAXIMUM_Y.getAsInt();
        int originalMinimumBlocks = AntiFullbrightConfig.MINIMUM_BLOCKS.getAsInt();
        Path file = directory.resolve("antifullbright-server.toml");
        Files.writeString(file, """
                language = \"ja_jp\"
                enabled = false
                maximumY = 99999
                minimumBlocks = 7
                """);

        try {
            int changed = AntiFullbrightConfig.reloadFromDisk(file);
            assertTrue(changed >= 4);
            assertEquals("ja_jp", AntiFullbrightConfig.LANGUAGE.get());
            assertFalse(AntiFullbrightConfig.ENABLED.getAsBoolean());
            assertEquals(2048, AntiFullbrightConfig.MAXIMUM_Y.getAsInt());
            assertEquals(7, AntiFullbrightConfig.MINIMUM_BLOCKS.getAsInt());
        } finally {
            AntiFullbrightConfig.LANGUAGE.set(originalLanguage);
            AntiFullbrightConfig.ENABLED.set(originalEnabled);
            AntiFullbrightConfig.MAXIMUM_Y.set(originalMaximumY);
            AntiFullbrightConfig.MINIMUM_BLOCKS.set(originalMinimumBlocks);
        }
    }

    @Test
    void reloadRejectsMissingFile(@TempDir Path directory) {
        assertThrows(IllegalStateException.class,
                () -> AntiFullbrightConfig.reloadFromDisk(directory.resolve("missing.toml")));
    }
}
