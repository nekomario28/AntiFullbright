package dev.antifullbright.client;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded, read-only scanner for local client mods and resource packs. */
public final class ContentScanner {
    private static final Set<String> MOD_METADATA = Set.of(
            "meta-inf/neoforge.mods.toml", "meta-inf/mods.toml", "fabric.mod.json", "quilt.mod.json");

    private ContentScanner() {}

    public record Policy(
            Set<String> blockedModTokens,
            Set<String> blockedPackTokens,
            Set<String> blockedPackPaths,
            Set<String> blockedModHashes,
            Set<String> blockedPackHashes,
            int maximumEntries,
            int maximumTextBytes,
            boolean failClosed) {
        public Policy {
            blockedModTokens = normalize(blockedModTokens, false);
            blockedPackTokens = normalize(blockedPackTokens, false);
            blockedPackPaths = normalize(blockedPackPaths, true);
            blockedModHashes = hashes(blockedModHashes);
            blockedPackHashes = hashes(blockedPackHashes);
            maximumEntries = Math.max(1, maximumEntries);
            maximumTextBytes = Math.max(1024, maximumTextBytes);
        }
    }

    public record Finding(String category, Path path, String rule, String detail) {
        public String display() {
            return category + ": " + path + " [" + rule + "] " + detail;
        }
    }

    public record Report(int modsScanned, int packsScanned, List<Finding> findings) {
        public Report {
            findings = List.copyOf(findings);
        }

        public boolean clean() {
            return findings.isEmpty();
        }

        public String summary() {
            if (clean()) {
                return "No blocked content found (mods=" + modsScanned + ", resourcePacks=" + packsScanned + ").";
            }
            StringBuilder result = new StringBuilder("Blocked or unreadable content detected:");
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
            addError(findings, policy, "mods", directory, error);
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
            addError(findings, policy, "resourcepack", directory, error);
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
            try (ZipFile zip = new ZipFile(file.toFile())) {
                Optional<String> name = match(lower(file.getFileName().toString()), policy.blockedModTokens());
                if (name.isPresent()) return finding("mod", file, "blocked_name", name.get());

                int count = 0;
                for (var entries = zip.entries().asIterator(); entries.hasNext();) {
                    ZipEntry entry = entries.next();
                    if (++count > policy.maximumEntries()) return limit("mod", file, policy.maximumEntries());
                    String path = path(entry.getName());
                    Optional<String> pathToken = match(path, policy.blockedModTokens());
                    if (pathToken.isPresent()) return finding("mod", file, "blocked_archive_path", pathToken.get());
                    if (!entry.isDirectory() && MOD_METADATA.contains(path)) {
                        Optional<String> metadata = match(read(zip, entry, policy), policy.blockedModTokens());
                        if (metadata.isPresent()) return finding("mod", file, "blocked_metadata", metadata.get());
                    }
                }
            }
        } catch (IOException error) {
            if (policy.failClosed()) return Optional.of(io("mod", file, error));
        }
        return Optional.empty();
    }

    private static Optional<Finding> inspectPack(Path pack, boolean unpacked, Policy policy) {
        Optional<String> name = match(lower(pack.getFileName().toString()), policy.blockedPackTokens());
        if (name.isPresent()) return finding("resourcepack", pack, "blocked_name", name.get());
        try {
            Optional<Finding> hash = blockedHash("resourcepack", pack, policy.blockedPackHashes());
            if (hash.isPresent()) return hash;
            return unpacked ? inspectPackDirectory(pack, policy) : inspectPackZip(pack, policy);
        } catch (IOException error) {
            return policy.failClosed() ? Optional.of(io("resourcepack", pack, error)) : Optional.empty();
        }
    }

    private static Optional<Finding> inspectPackZip(Path pack, Policy policy) throws IOException {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            int count = 0;
            for (var entries = zip.entries().asIterator(); entries.hasNext();) {
                ZipEntry entry = entries.next();
                if (++count > policy.maximumEntries()) return limit("resourcepack", pack, policy.maximumEntries());
                String path = path(entry.getName());
                Optional<String> signature = match(path, policy.blockedPackPaths());
                if (signature.isPresent()) return finding("resourcepack", pack, "blocked_pack_path", signature.get());
                if (!entry.isDirectory() && path.equals("pack.mcmeta")) {
                    Optional<String> metadata = match(read(zip, entry, policy), policy.blockedPackTokens());
                    if (metadata.isPresent()) return finding("resourcepack", pack, "blocked_metadata", metadata.get());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Finding> inspectPackDirectory(Path pack, Policy policy) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(pack)) {
            paths = stream.limit((long) policy.maximumEntries() + 1).toList();
        }
        if (paths.size() > policy.maximumEntries()) return limit("resourcepack", pack, policy.maximumEntries());
        for (Path current : paths) {
            if (Files.isSymbolicLink(current)) continue;
            String relative = path(pack.relativize(current).toString());
            Optional<String> signature = match(relative, policy.blockedPackPaths());
            if (signature.isPresent()) return finding("resourcepack", pack, "blocked_pack_path", signature.get());
            if (relative.equals("pack.mcmeta") && Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
                String metadata;
                try (InputStream input = Files.newInputStream(current)) {
                    metadata = lower(new String(input.readNBytes(policy.maximumTextBytes()), StandardCharsets.UTF_8));
                }
                Optional<String> token = match(metadata, policy.blockedPackTokens());
                if (token.isPresent()) return finding("resourcepack", pack, "blocked_metadata", token.get());
            }
        }
        return Optional.empty();
    }

    private static String read(ZipFile zip, ZipEntry entry, Policy policy) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return lower(new String(input.readNBytes(policy.maximumTextBytes()), StandardCharsets.UTF_8));
        }
    }

    private static Optional<Finding> blockedHash(String category, Path target, Set<String> blocked) throws IOException {
        if (blocked.isEmpty()) return Optional.empty();
        String hash = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) ? hashDirectory(target) : hashFile(target);
        return blocked.contains(hash) ? finding(category, target, "blocked_sha256", hash) : Optional.empty();
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

    private static Optional<Finding> finding(String category, Path path, String rule, String token) {
        return Optional.of(new Finding(category, path, rule, "Matched: " + token));
    }

    private static Optional<Finding> limit(String category, Path path, int maximum) {
        return Optional.of(new Finding(category, path, "entry_limit", "Exceeded " + maximum + " entries."));
    }

    private static Finding io(String category, Path path, IOException error) {
        return new Finding(category, path, "scan_io_error", error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    private static void addError(List<Finding> findings, Policy policy, String category, Path path, IOException error) {
        if (policy.failClosed()) findings.add(io(category, path, error));
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
