package dev.antifullbright.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Client-local policy for startup and resource-pack scans. */
public final class ClientScanConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final boolean DEFAULT_FAIL_CLOSED = false;
    static final String DEFAULT_BLOCKED_MOD_IDS = "fullbright";
    static final String DEFAULT_SUSPICIOUS_MOD_TOKENS = String.join(",",
            "fullbright",
            "full_bright",
            "full-bright",
            "gammabright",
            "gamma-bright",
            "gammautils",
            "gamma-utils",
            "boostedbrightness",
            "boosted-brightness",
            "truefullbright",
            "true-fullbright",
            "fullbrightutils",
            "fullbright-utils",
            "resourcegammautils",
            "resource-gamma-utils");
    static final String DEFAULT_SUSPICIOUS_RESOURCE_PACK_TOKENS = String.join(",",
            "fullbright",
            "full_bright",
            "full-bright",
            "nightvision",
            "night-vision",
            "gammabright",
            "gamma-bright");
    static final String DEFAULT_BLOCKED_RESOURCE_PACK_PATHS = String.join(",",
            "assets/minecraft/optifine/lightmap/",
            "assets/minecraft/mcpatcher/lightmap/");
    static final String DEFAULT_BLOCKED_MOD_SHA256 = "";
    static final String DEFAULT_BLOCKED_RESOURCE_PACK_SHA256 = "";

    public static final ModConfigSpec.BooleanValue ENABLED = bool("enabled", true,
            "Enable client-side mod and resource-pack scanning.");
    public static final ModConfigSpec.BooleanValue SCAN_MODS = bool("scanMods", true,
            "Scan JAR/ZIP files in the local mods directory during client setup.");
    public static final ModConfigSpec.BooleanValue SCAN_RESOURCE_PACKS = bool("scanResourcePacks", true,
            "Scan ZIP and unpacked resource packs during client setup.");
    public static final ModConfigSpec.BooleanValue WATCH_RESOURCE_PACKS = bool("watchResourcePacks", true,
            "Watch the resourcepacks directory recursively and rescan after changes.");
    public static final ModConfigSpec.BooleanValue FAIL_CLOSED = bool("failClosed", DEFAULT_FAIL_CLOSED,
            "Treat unreadable, malformed, or over-limit content as blocking findings. "
                    + "The production default is false to avoid blocking on ambiguous scan failures; "
                    + "controlled deployments may enable strict fail-closed behavior.");
    public static final ModConfigSpec.BooleanValue DISCONNECT_ON_RUNTIME_DETECTION = bool(
            "disconnectOnRuntimeDetection", true,
            "Safely leave the current world and display a blocking screen after runtime detection.");
    public static final ModConfigSpec.IntValue WATCH_DEBOUNCE_MILLIS = integer(
            "watchDebounceMillis", 1000, 100, 30_000,
            "Wait this long after the final filesystem event before rescanning.");
    public static final ModConfigSpec.IntValue MAXIMUM_ARCHIVE_ENTRIES = integer(
            "maximumArchiveEntries", 100_000, 100, 1_000_000,
            "Maximum entries inspected in one archive or unpacked resource pack.");
    public static final ModConfigSpec.IntValue MAXIMUM_TEXT_BYTES = integer(
            "maximumTextBytes", 1_048_576, 1024, 16_777_216,
            "Maximum bytes read from one metadata text file.");

    public static final ModConfigSpec.ConfigValue<String> BLOCKED_MOD_IDS = string(
            "blockedModIds", DEFAULT_BLOCKED_MOD_IDS,
            "Comma-separated exact declared Mod IDs that produce blocking findings.");
    public static final ModConfigSpec.ConfigValue<String> SUSPICIOUS_MOD_TOKENS = string(
            "suspiciousModTokens", DEFAULT_SUSPICIOUS_MOD_TOKENS,
            "Comma-separated tokens that produce warnings when found in mod names, archive paths, or metadata text.");
    public static final ModConfigSpec.ConfigValue<String> SUSPICIOUS_RESOURCE_PACK_TOKENS = string(
            "suspiciousResourcePackTokens", DEFAULT_SUSPICIOUS_RESOURCE_PACK_TOKENS,
            "Comma-separated tokens that produce warnings when found in resource-pack names or pack.mcmeta.");
    public static final ModConfigSpec.ConfigValue<String> BLOCKED_RESOURCE_PACK_PATHS = string(
            "blockedResourcePackPaths", DEFAULT_BLOCKED_RESOURCE_PACK_PATHS,
            "Comma-separated precise resource-pack path prefixes that produce blocking findings. "
                    + "Generic core-shader paths are intentionally not blocked by default.");
    public static final ModConfigSpec.ConfigValue<String> BLOCKED_MOD_SHA256 = string(
            "blockedModSha256", DEFAULT_BLOCKED_MOD_SHA256,
            "Comma-separated SHA-256 hashes for prohibited mod archives. Optional sha256: prefixes are accepted.");
    public static final ModConfigSpec.ConfigValue<String> BLOCKED_RESOURCE_PACK_SHA256 = string(
            "blockedResourcePackSha256", DEFAULT_BLOCKED_RESOURCE_PACK_SHA256,
            "Comma-separated SHA-256 hashes for prohibited ZIP or unpacked resource packs.");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientScanConfig() {}

    public static ContentScanner.Policy policy() {
        return new ContentScanner.Policy(
                csv(BLOCKED_MOD_IDS.get()),
                csv(SUSPICIOUS_MOD_TOKENS.get()),
                csv(SUSPICIOUS_RESOURCE_PACK_TOKENS.get()),
                csv(BLOCKED_RESOURCE_PACK_PATHS.get()),
                csv(BLOCKED_MOD_SHA256.get()),
                csv(BLOCKED_RESOURCE_PACK_SHA256.get()),
                MAXIMUM_ARCHIVE_ENTRIES.getAsInt(),
                MAXIMUM_TEXT_BYTES.getAsInt(),
                FAIL_CLOSED.getAsBoolean()
        );
    }

    private static ModConfigSpec.BooleanValue bool(String name, boolean defaultValue, String comment) {
        return BUILDER.comment(comment).define(name, defaultValue);
    }

    private static ModConfigSpec.IntValue integer(String name, int defaultValue, int min, int max, String comment) {
        return BUILDER.comment(comment).defineInRange(name, defaultValue, min, max);
    }

    private static ModConfigSpec.ConfigValue<String> string(String name, String defaultValue, String comment) {
        return BUILDER.comment(comment).define(name, defaultValue, value -> value instanceof String);
    }

    static Set<String> csv(String value) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isEmpty())
                .forEach(values::add);
        return Set.copyOf(values);
    }
}
