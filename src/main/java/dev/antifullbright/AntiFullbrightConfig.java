package dev.antifullbright;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AntiFullbrightConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> LANGUAGE = BUILDER
            .comment("Language for player warnings and administrator command output: en_us or ja_jp.")
            .define("language", "en_us", AntiFullbrightConfig::isLanguage);
    public static final ModConfigSpec.BooleanValue ENABLED = bool("enabled", true,
            "Enable complete-darkness mining detection.");
    public static final ModConfigSpec.IntValue MAXIMUM_Y = integer("maximumY", 16, -2048, 2048,
            "Highest player block Y at which mining can count.");
    public static final ModConfigSpec.IntValue REQUIRED_BLOCK_LIGHT = integer("requiredBlockLight", 0, 0, 15,
            "Required block light at both the player's eyes and the broken block.");
    public static final ModConfigSpec.IntValue REQUIRED_SKY_LIGHT = integer("requiredSkyLight", 0, 0, 15,
            "Required sky light at both the player's eyes and the broken block.");
    public static final ModConfigSpec.IntValue CONTINUOUS_MINING_SECONDS = integer("continuousMiningSeconds", 60, 1, 86_400,
            "Continuous counted-mining time required for a warning.");
    public static final ModConfigSpec.IntValue MINIMUM_BLOCKS = integer("minimumBlocks", 20, 1, 100_000,
            "Counted blocks required in the same session for a warning.");
    public static final ModConfigSpec.IntValue INACTIVITY_RESET_SECONDS = integer("inactivityResetSeconds", 10, 1, 3_600,
            "Reset a session after this many seconds without a qualifying break.");
    public static final ModConfigSpec.IntValue TORCH_HOLDING_GRACE_SECONDS = integer("torchHoldingGraceSeconds", 20, 0, 3_600,
            "Grace period at the beginning of dark mining while a tagged light source is held.");
    public static final ModConfigSpec.IntValue WARNINGS_BEFORE_KICK = integer("warningsBeforeKick", 3, 1, 100,
            "Warning level at which the player is disconnected.");
    public static final ModConfigSpec.IntValue WARNING_DECAY_MINUTES = integer("warningDecayMinutes", 30, 1, 43_200,
            "Minutes without a new warning before warning level decreases by one.");
    public static final ModConfigSpec.BooleanValue EXCLUDE_OPERATORS = bool("excludeOperators", true,
            "Exclude server operators from detection.");
    public static final ModConfigSpec.BooleanValue EXCLUDE_NIGHT_VISION = bool("excludeNightVision", true,
            "Exclude players with the vanilla night vision effect.");
    public static final ModConfigSpec.BooleanValue EXCLUDE_UNDERWATER = bool("excludeUnderwater", true,
            "Exclude players whose eye position is submerged in water.");
    public static final ModConfigSpec.IntValue NOTIFY_OPERATORS_AT_WARNING = integer("notifyOperatorsAtWarning", 2, 1, 100,
            "First warning level that is reported to online operators.");
    public static final ModConfigSpec.BooleanValue PERSIST_WARNINGS = bool("persistWarnings", true,
            "Persist UUID warning levels in the overworld SavedData.");
    public static final ModConfigSpec.BooleanValue ENABLE_DEDICATED_LOG = bool("enableDedicatedLog", true,
            "Append warning evidence to logs/dark-mining-detections.jsonl.");
    public static final ModConfigSpec.BooleanValue PLACED_BLOCK_TRACKING_ENABLED = bool("placedBlockTrackingEnabled", true,
            "Ignore recently player-placed blocks when they are broken again.");
    public static final ModConfigSpec.IntValue PLACED_BLOCK_TRACKING_EXPIRATION_MINUTES = integer(
            "placedBlockTrackingExpirationMinutes", 60, 1, 10_080,
            "Minutes after which a placed-block entry expires.");
    public static final ModConfigSpec.IntValue PLACED_BLOCK_TRACKING_MAXIMUM_ENTRIES_PER_PLAYER = integer(
            "placedBlockTrackingMaximumEntriesPerPlayer", 4096, 0, 100_000,
            "Hard per-player limit for placed-block entries; zero disables storage.");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AntiFullbrightConfig() {}

    private static ModConfigSpec.BooleanValue bool(String name, boolean defaultValue, String comment) {
        return BUILDER.comment(comment).define(name, defaultValue);
    }

    private static ModConfigSpec.IntValue integer(String name, int defaultValue, int min, int max, String comment) {
        return BUILDER.comment(comment).defineInRange(name, defaultValue, min, max);
    }

    /** Reloads supported values from the server config file without touching game objects off-thread. */
    public static int reloadFromDisk(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Config file does not exist: " + path);
        }
        int changed = 0;
        try (CommentedFileConfig disk = CommentedFileConfig.builder(path).sync().build()) {
            disk.load();
            changed += setLanguage(disk, "language", LANGUAGE);
            changed += setBool(disk, "enabled", ENABLED);
            changed += setInt(disk, "maximumY", MAXIMUM_Y, -2048, 2048);
            changed += setInt(disk, "requiredBlockLight", REQUIRED_BLOCK_LIGHT, 0, 15);
            changed += setInt(disk, "requiredSkyLight", REQUIRED_SKY_LIGHT, 0, 15);
            changed += setInt(disk, "continuousMiningSeconds", CONTINUOUS_MINING_SECONDS, 1, 86_400);
            changed += setInt(disk, "minimumBlocks", MINIMUM_BLOCKS, 1, 100_000);
            changed += setInt(disk, "inactivityResetSeconds", INACTIVITY_RESET_SECONDS, 1, 3_600);
            changed += setInt(disk, "torchHoldingGraceSeconds", TORCH_HOLDING_GRACE_SECONDS, 0, 3_600);
            changed += setInt(disk, "warningsBeforeKick", WARNINGS_BEFORE_KICK, 1, 100);
            changed += setInt(disk, "warningDecayMinutes", WARNING_DECAY_MINUTES, 1, 43_200);
            changed += setBool(disk, "excludeOperators", EXCLUDE_OPERATORS);
            changed += setBool(disk, "excludeNightVision", EXCLUDE_NIGHT_VISION);
            changed += setBool(disk, "excludeUnderwater", EXCLUDE_UNDERWATER);
            changed += setInt(disk, "notifyOperatorsAtWarning", NOTIFY_OPERATORS_AT_WARNING, 1, 100);
            changed += setBool(disk, "persistWarnings", PERSIST_WARNINGS);
            changed += setBool(disk, "enableDedicatedLog", ENABLE_DEDICATED_LOG);
            changed += setBool(disk, "placedBlockTrackingEnabled", PLACED_BLOCK_TRACKING_ENABLED);
            changed += setInt(disk, "placedBlockTrackingExpirationMinutes", PLACED_BLOCK_TRACKING_EXPIRATION_MINUTES, 1, 10_080);
            changed += setInt(disk, "placedBlockTrackingMaximumEntriesPerPlayer", PLACED_BLOCK_TRACKING_MAXIMUM_ENTRIES_PER_PLAYER, 0, 100_000);
        }
        return changed;
    }

    private static boolean isLanguage(Object value) {
        return value instanceof String language
                && (language.equalsIgnoreCase("ja_jp") || language.equalsIgnoreCase("en_us"));
    }

    private static int setLanguage(CommentedFileConfig disk, String key, ModConfigSpec.ConfigValue<String> target) {
        Object raw = disk.get(key);
        if (!isLanguage(raw)) return 0;
        String value = ((String) raw).toLowerCase(java.util.Locale.ROOT);
        boolean changed = !target.get().equals(value);
        target.set(value);
        return changed ? 1 : 0;
    }

    private static int setBool(CommentedFileConfig disk, String key, ModConfigSpec.BooleanValue target) {
        Object raw = disk.get(key);
        if (!(raw instanceof Boolean value)) return 0;
        boolean changed = target.getAsBoolean() != value;
        target.set(value);
        return changed ? 1 : 0;
    }

    private static int setInt(CommentedFileConfig disk, String key, ModConfigSpec.IntValue target, int min, int max) {
        Object raw = disk.get(key);
        if (!(raw instanceof Number number)) return 0;
        int value = Math.clamp(number.intValue(), min, max);
        boolean changed = target.getAsInt() != value;
        target.set(value);
        return changed ? 1 : 0;
    }
}
