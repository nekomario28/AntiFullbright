package dev.antifullbright.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded, read-only scanner for local client mods and resource packs. */
public final class ContentScanner {
    private static final Set<String> MOD_METADATA = Set.of(
            "meta-inf/neoforge.mods.toml", "meta-inf/mods.toml", "fabric.mod.json", "quilt.mod.json");
    private static final Pattern TOML_MOD_ID = Pattern.compile(
            "^\\s*modId\\s*=\\s*[\\\"']([a-z0-9_-]+)[\\\"']",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern VALID_MOD_ID = Pattern.compile("[a-z0-9_-]+");

    private ContentScanner() {}

    public enum Severity {
        WARNING,
        BLOCK
    }

    public record Policy(
            Set<String> blockedModIds,
            Set<String> suspiciousModTokens,
            Set<String> suspiciousPackTokens,
            Set<String> blockedPackPaths,
            Set<String> blockedModHashes,
            Set<String> blockedPackHashes,
            int maximumEntries,
            int maximumTextBytes,
            boolean failClosed) {
        public Policy {
            blockedModIds = normalize(blockedModIds, false);
            suspiciousModTokens = normalize(suspiciousModTokens, false);
            suspiciousPackTokens = normalize(suspiciousPackTokens, false);
            blockedPackPaths = normalize(blockedPackPaths, true);
            blockedModHashes = hashes(blockedModHashes);
            blockedPackHashes = hashes(blockedPackHashes);
            maximumEntries = Math.max(1, maximumEntries);
            maximumTextBytes = Math.max(1024, maximumTextBytes);
        }
    }

    public record Finding(Severity severity, String category, Path path, String rule, String detail) {
        public String display() {
            return severity + " " + category + ": " + path + " [" + rule + "] " + detail;
        }
    }

    public record Report(int modsScanned, int packsScanned, List<Finding> findings) {
        public Report {
            findings = List.copyOf(findings);
        }

        public boolean clean() {
            return findings.isEmpty();
        }

        public boolean hasBlockingFindings() {
            return findings.stream().anyMatch(finding -> finding.severity() == Severity.BLOCK);
        }

        public boolean hasWarnings() {
            return findings.stream().anyMatch(finding -> finding.severity() == Severity.WARNING);
        }

        public List<Finding> blockingFindings() {
            return findings.stream().filter(finding -> finding.severity() == Severity.BLOCK).toList();
        }

        public String summary() {
            if (clean()) {
                return "No findings (mods=" + modsScanned + ", resourcePacks=" + packsScanned + ").";
            }
            long blocks = findings.stream().filter(finding -> finding.severity() == Severity.BLOCK).count();
            long warnings = findings.size() - blocks;
            StringBuilder result = new StringBuilder("Content scan findings (blocks=")
                    .append(blocks).append(", warnings=").append(warnings).append("):");
            findings.forEach(finding -> result.append(System.lineSeparator()).append(" - ").append(finding.display()));
            return result.toString();
        }
    }

    public static Report scan(Path gameDir, Policy policy, boolean scanMods, boolean scanPacks) {
        return scan(gameDir, policy, scanMods, scanPacks, Set.of());
    }

    public static Report scan(Path gameDir, Policy policy, boolean scanMods, boolean scanPacks, Set<Path> ignoredMods) {
        Report mods = scanMods ? scanMods(gameDir.resolve("mods"), policy, ignoredMods) : empty();
        Report packs = scanPacks ? scanResourcePacks(gameDir.resolve("resourcepacks"), policy) : empty();
        List<Finding> findings = new ArrayList<>(mods.findings());
        findings.addAll(packs.findings());
        return new Report(mods.modsScanned(), packs.packsScanned(), findings);
    }

    public static Report scanMods(Path directory, Policy policy) {
        return scanMods(directory, policy, Set.of());
    }

    public static Report scanMods(Path directory, Policy policy, Set<Path> ignoredMods) {
        Set<Path> ignored = new LinkedHashSet<>();
        ignoredMods.stream().map(ContentScanner::absolute).forEach(ignored::add);
        List<Finding> findings = new ArrayList<>();
        int scanned = 0;
        try {
            for (Path file : children(directory)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !archive(file)) continue;
                if (ignored.contains(absolute(file))) continue;
                scanned++;
                inspectMod(file, policy).ifPresent(findings::add);
            }
        } catch (IOException error) {
            findings.add(io(policy, "mods", directory, error));
        }
        return new Report(scanned, 0, findings);
    }

    public static Report scanResourcePacks(Path directory, Policy policy) {
        List<Finding> findings = new ArrayList<>();
        int scanned = 0;
        try {
            for (Path pack : children(directory)) {
                boolean unpacked = Files.isDirectory(pack, LinkOption.NOFOLLOW_LINKS);
                if (!unpacked && (!Files.isRegularFile(pack, LinkOption.NOFOLLOW_LINKS) || !archive(pack))) continue;
                scanned++;
                inspectPack(pack, unpacked, policy).ifPresent(findings::add);
            }
        } catch (IOException error) {
            findings.add(io(policy, "resourcepack", directory, error));
        }
        return new Report(0, scanned, findings);
    }

    private static List<Path> children(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected directory: " + directory);
        }
        try (var stream = Files.list(directory)) {
            return stream.sorted(Comparator.comparing(Path::toString)).toList();
        }
    }

    private static Optional<Finding> inspectMod(Path file, Policy policy) {
        try {
            Optional<Finding> hash = blockedHash("mod", file, policy.blockedModHashes());
            if (hash.isPresent()) return hash;

            Optional<Finding> warning = match(lower(file.getFileName().toString()), policy.suspiciousModTokens())
                    .map(token -> warning("mod", file, "suspicious_name", token));

            try (ZipFile zip = new ZipFile(file.toFile())) {
                int count = 0;
                for (var entries = zip.entries().asIterator(); entries.hasNext();) {
                    ZipEntry entry = entries.next();
                    if (++count > policy.maximumEntries()) return Optional.of(limit(policy, "mod", file));
                    String entryPath = path(entry.getName());

                    if (warning.isEmpty()) {
                        warning = match(entryPath, policy.suspiciousModTokens())
                                .map(token -> warning("mod", file, "suspicious_archive_path", token));
                    }

                    if (!entry.isDirectory() && MOD_METADATA.contains(entryPath)) {
                        String metadata = read(zip, entry, policy);
                        for (String modId : parseModIds(entryPath, metadata)) {
                            if (policy.blockedModIds().contains(modId)) {
                                return Optional.of(block("mod", file, "blocked_mod_id", modId));
                            }
                        }
                        if (warning.isEmpty()) {
                            warning = match(lower(metadata), policy.suspiciousModTokens())
                                    .map(token -> warning("mod", file, "suspicious_metadata", token));
                        }
                    }
                }
            }
            return warning;
        } catch (IOException error) {
            return Optional.of(io(policy, "mod", file, error));
        }
    }

    private static Optional<Finding> inspectPack(Path pack, boolean unpacked, Policy policy) {
        Optional<Finding> warning = match(lower(pack.getFileName().toString()), policy.suspiciousPackTokens())
                .map(token -> warning("resourcepack", pack, "suspicious_name", token));
        try {
            Optional<Finding> hash = blockedHash("resourcepack", pack, policy.blockedPackHashes());
            if (hash.isPresent()) return hash;
            Optional<Finding> inspected = unpacked
                    ? inspectPackDirectory(pack, policy, warning)
                    : inspectPackZip(pack, policy, warning);
            return inspected.isPresent() ? inspected : warning;
        } catch (IOException error) {
            return Optional.of(io(policy, "resourcepack", pack, error));
        }
    }

    private static Optional<Finding> inspectPackZip(
            Path pack, Policy policy, Optional<Finding> initialWarning) throws IOException {
        Optional<Finding> warning = initialWarning;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            int count = 0;
            for (var entries = zip.entries().asIterator(); entries.hasNext();) {
                ZipEntry entry = entries.next();
                if (++count > policy.maximumEntries()) return Optional.of(limit(policy, "resourcepack", pack));
                String entryPath = path(entry.getName());
                Optional<String> signature = match(entryPath, policy.blockedPackPaths());
                if (signature.isPresent()) {
                    return Optional.of(block("resourcepack", pack, "blocked_pack_path", signature.get()));
                }
                if (!entry.isDirectory() && entryPath.equals("pack.mcmeta") && warning.isEmpty()) {
                    warning = match(lower(read(zip, entry, policy)), policy.suspiciousPackTokens())
                            .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
                }
            }
        }
        return warning;
    }

    private static Optional<Finding> inspectPackDirectory(
            Path pack, Policy policy, Optional<Finding> initialWarning) throws IOException {
        Optional<Finding> warning = initialWarning;
        List<Path> paths;
        try (var stream = Files.walk(pack)) {
            paths = stream.limit((long) policy.maximumEntries() + 1).toList();
        }
        if (paths.size() > policy.maximumEntries()) return Optional.of(limit(policy, "resourcepack", pack));
        for (Path current : paths) {
            if (Files.isSymbolicLink(current)) continue;
            String relative = path(pack.relativize(current).toString());
            Optional<String> signature = match(relative, policy.blockedPackPaths());
            if (signature.isPresent()) {
                return Optional.of(block("resourcepack", pack, "blocked_pack_path", signature.get()));
            }
            if (relative.equals("pack.mcmeta")
                    && warning.isEmpty()
                    && Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
                String metadata;
                try (InputStream input = Files.newInputStream(current)) {
                    metadata = lower(new String(input.readNBytes(policy.maximumTextBytes()), StandardCharsets.UTF_8));
                }
                warning = match(metadata, policy.suspiciousPackTokens())
                        .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
            }
        }
        return warning;
    }

    private static Set<String> parseModIds(String metadataPath, String metadata) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (metadataPath.endsWith(".toml")) {
            Matcher matcher = TOML_MOD_ID.matcher(metadata);
            while (matcher.find()) {
                ids.add(lower(matcher.group(1)));
            }
            return Set.copyOf(ids);
        }

        try {
            JsonElement parsed = JsonParser.parseString(metadata);
            if (!parsed.isJsonObject()) return Set.of();
            JsonObject root = parsed.getAsJsonObject();
            if (metadataPath.equals("fabric.mod.json")) {
                addJsonModId(ids, root.get("id"));
            } else if (metadataPath.equals("quilt.mod.json")) {
                JsonElement loader = root.get("quilt_loader");
                if (loader != null && loader.isJsonObject()) {
                    addJsonModId(ids, loader.getAsJsonObject().get("id"));
                }
            }
        } catch (JsonParseException | IllegalStateException ignored) {
            // Invalid metadata is handled as suspicious text, not as a fabricated exact Mod ID.
        }
        return Set.copyOf(ids);
    }

    private static void addJsonModId(Set<String> ids, JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return;
        String id = lower(value.getAsString().trim());
        if (VALID_MOD_ID.matcher(id).matches()) ids.add(id);
    }

    private static String read(ZipFile zip, ZipEntry entry, Policy policy) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readNBytes(policy.maximumTextBytes()), StandardCharsets.UTF_8);
        }
    }

    private static Optional<Finding> blockedHash(String category, Path target, Set<String> blocked) throws IOException {
        if (blocked.isEmpty()) return Optional.empty();
        String hash = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) ? hashDirectory(target) : hashFile(target);
        return blocked.contains(hash) ? Optional.of(block(category, target, "blocked_sha256", hash)) : Optional.empty();
    }

    private static String hashFile(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashDirectory(Path root) throws IOException {
        MessageDigest digest = sha256();
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> !Files.isSymbolicLink(file))
                    .sorted(Comparator.comparing(file -> path(root.relativize(file).toString()))).toList();
        }
        for (Path file : files) {
            digest.update(path(root.relativize(file).toString()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
            }
            digest.update((byte) 0xff);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Optional<String> match(String value, Set<String> tokens) {
        return tokens.stream().filter(token -> !token.isEmpty() && value.contains(token)).findFirst();
    }

    private static Finding block(String category, Path path, String rule, String token) {
        return new Finding(Severity.BLOCK, category, path, rule, "Matched: " + token);
    }

    private static Finding warning(String category, Path path, String rule, String token) {
        return new Finding(Severity.WARNING, category, path, rule, "Matched: " + token);
    }

    private static Finding limit(Policy policy, String category, Path path) {
        Severity severity = policy.failClosed() ? Severity.BLOCK : Severity.WARNING;
        return new Finding(severity, category, path, "entry_limit",
                "Exceeded " + policy.maximumEntries() + " entries.");
    }

    private static Finding io(Policy policy, String category, Path path, IOException error) {
        Severity severity = policy.failClosed() ? Severity.BLOCK : Severity.WARNING;
        return new Finding(severity, category, path, "scan_io_error",
                error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
    }

    private static Report empty() {
        return new Report(0, 0, List.of());
    }

    private static boolean archive(Path file) {
        String name = lower(file.getFileName().toString());
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static Path absolute(Path value) {
        return value.toAbsolutePath().normalize();
    }

    private static String path(String value) {
        return lower(value.replace('\\', '/'));
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalize(Set<String> values, boolean paths) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.stream().map(String::trim).map(value -> paths ? path(value) : lower(value))
                .filter(value -> !value.isEmpty()).forEach(result::add);
        return Set.copyOf(result);
    }

    private static Set<String> hashes(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String hash = lower(value.trim());
            if (hash.startsWith("sha256:")) hash = hash.substring(7);
            if (hash.matches("[0-9a-f]{64}")) result.add(hash);
        }
        return Set.copyOf(result);
    }
}
