from pathlib import Path


def replace_one(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 occurrence, found {count}")
    return text.replace(old, new)


scanner_path = Path("src/main/java/dev/antifullbright/client/ContentScanner.java")
scanner = scanner_path.read_text()

scanner = replace_one(
    scanner,
    "    public record Report(int modsScanned, int packsScanned, List<Finding> findings) {\n",
    "    private record TextRead(String text, boolean overLimit) {}\n\n"
    "    public record Report(int modsScanned, int packsScanned, List<Finding> findings) {\n",
    "TextRead insertion",
)

scanner = replace_one(
    scanner,
    '''                    if (!entry.isDirectory() && MOD_METADATA.contains(entryPath)) {
                        String metadata = read(zip, entry, policy);
                        for (String modId : parseModIds(entryPath, metadata)) {
''',
    '''                    if (!entry.isDirectory() && MOD_METADATA.contains(entryPath)) {
                        TextRead metadataRead = read(zip, entry, policy);
                        if (metadataRead.overLimit()) {
                            return Optional.of(textLimit(policy, "mod", file, entryPath));
                        }
                        String metadata = metadataRead.text();
                        for (String modId : parseModIds(entryPath, metadata)) {
''',
    "mod metadata bounded read",
)

scanner = replace_one(
    scanner,
    "                Optional<String> signature = match(entryPath, policy.blockedPackPaths());",
    "                Optional<String> signature = matchPrefix(entryPath, policy.blockedPackPaths());",
    "zip pack prefix match",
)

scanner = replace_one(
    scanner,
    '''                if (!entry.isDirectory() && entryPath.equals("pack.mcmeta") && warning.isEmpty()) {
                    warning = match(lower(read(zip, entry, policy)), policy.suspiciousPackTokens())
                            .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
                }
''',
    '''                if (!entry.isDirectory() && entryPath.equals("pack.mcmeta") && warning.isEmpty()) {
                    TextRead metadataRead = read(zip, entry, policy);
                    if (metadataRead.overLimit()) {
                        return Optional.of(textLimit(policy, "resourcepack", pack, entryPath));
                    }
                    warning = match(lower(metadataRead.text()), policy.suspiciousPackTokens())
                            .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
                }
''',
    "zip pack metadata bounded read",
)

scanner = replace_one(
    scanner,
    "            Optional<String> signature = match(relative, policy.blockedPackPaths());",
    "            Optional<String> signature = matchPrefix(relative, policy.blockedPackPaths());",
    "directory pack prefix match",
)

scanner = replace_one(
    scanner,
    '''                String metadata;
                try (InputStream input = Files.newInputStream(current)) {
                    metadata = lower(new String(input.readNBytes(policy.maximumTextBytes()), StandardCharsets.UTF_8));
                }
                warning = match(metadata, policy.suspiciousPackTokens())
                        .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
''',
    '''                TextRead metadataRead;
                try (InputStream input = Files.newInputStream(current)) {
                    metadataRead = read(input, policy);
                }
                if (metadataRead.overLimit()) {
                    return Optional.of(textLimit(policy, "resourcepack", pack, relative));
                }
                warning = match(lower(metadataRead.text()), policy.suspiciousPackTokens())
                        .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
''',
    "directory pack metadata bounded read",
)

scanner = replace_one(
    scanner,
    '''    private static String read(ZipFile zip, ZipEntry entry, Policy policy) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readNBytes(policy.maximumTextBytes()), StandardCharsets.UTF_8);
        }
    }
''',
    '''    private static TextRead read(ZipFile zip, ZipEntry entry, Policy policy) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return read(input, policy);
        }
    }

    private static TextRead read(InputStream input, Policy policy) throws IOException {
        int maximum = policy.maximumTextBytes();
        byte[] bytes = input.readNBytes(maximum + 1);
        boolean overLimit = bytes.length > maximum;
        int length = Math.min(bytes.length, maximum);
        return new TextRead(new String(bytes, 0, length, StandardCharsets.UTF_8), overLimit);
    }
''',
    "bounded read helper",
)

scanner = replace_one(
    scanner,
    '''    private static Optional<String> match(String value, Set<String> tokens) {
        return tokens.stream().filter(token -> !token.isEmpty() && value.contains(token)).findFirst();
    }
''',
    '''    private static Optional<String> match(String value, Set<String> tokens) {
        return tokens.stream().filter(token -> !token.isEmpty() && value.contains(token)).findFirst();
    }

    private static Optional<String> matchPrefix(String value, Set<String> prefixes) {
        return prefixes.stream().filter(prefix -> !prefix.isEmpty() && value.startsWith(prefix)).findFirst();
    }
''',
    "prefix helper",
)

scanner = replace_one(
    scanner,
    '''    private static Finding limit(Policy policy, String category, Path path) {
        Severity severity = policy.failClosed() ? Severity.BLOCK : Severity.WARNING;
        return new Finding(severity, category, path, "entry_limit",
                "Exceeded " + policy.maximumEntries() + " entries.");
    }

    private static Finding io(Policy policy, String category, Path path, IOException error) {
''',
    '''    private static Finding limit(Policy policy, String category, Path path) {
        Severity severity = policy.failClosed() ? Severity.BLOCK : Severity.WARNING;
        return new Finding(severity, category, path, "entry_limit",
                "Exceeded " + policy.maximumEntries() + " entries.");
    }

    private static Finding textLimit(Policy policy, String category, Path path, String metadataPath) {
        Severity severity = policy.failClosed() ? Severity.BLOCK : Severity.WARNING;
        return new Finding(severity, category, path, "text_limit",
                "Exceeded " + policy.maximumTextBytes() + " bytes while reading " + metadataPath + ".");
    }

    private static Finding io(Policy policy, String category, Path path, IOException error) {
''',
    "text limit finding",
)

scanner_path.write_text(scanner)

test_path = Path("src/test/java/dev/antifullbright/client/ContentScannerTest.java")
tests = test_path.read_text()

new_tests = '''    @Test
    void nestedDocumentationLightmapPathIsNotBlockedAsAnActivePackPath() throws IOException {
        Path packs = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        writeZip(packs.resolve("documentation.zip"), Map.of(
                "pack.mcmeta", "{\\"pack\\":{\\"pack_format\\":34,\\"description\\":\\"documentation\\"}}",
                "docs/assets/minecraft/optifine/lightmap/world0.png", "example-only"
        ));

        ContentScanner.Report report = ContentScanner.scanResourcePacks(packs, productionPolicy(false));

        assertFalse(report.hasBlockingFindings());
    }

    @Test
    void oversizedMetadataWarnsOrBlocksAccordingToFailClosed() throws IOException {
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        String metadata = "[[mods]]\\ndescription=\\\"" + "x".repeat(2_048)
                + "\\\"\\nmodId=\\\"fullbright\\\"\\n";
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

'''

tests = replace_one(
    tests,
    "    @Test\n    void exactIgnoredArchivePathIsNotScanned() throws IOException {\n",
    new_tests + "    @Test\n    void exactIgnoredArchivePathIsNotScanned() throws IOException {\n",
    "new scanner boundary tests",
)

tests = replace_one(
    tests,
    '''    private static ContentScanner.Policy productionPolicy(boolean failClosed) {
        return new ContentScanner.Policy(
''',
    '''    private static ContentScanner.Policy productionPolicy(boolean failClosed) {
        return productionPolicy(failClosed, 1_048_576);
    }

    private static ContentScanner.Policy productionPolicy(boolean failClosed, int maximumTextBytes) {
        return new ContentScanner.Policy(
''',
    "policy helper overload",
)

tests = replace_one(
    tests,
    '''                10_000,
                1_048_576,
                failClosed
''',
    '''                10_000,
                maximumTextBytes,
                failClosed
''',
    "custom maximum text bytes",
)

test_path.write_text(tests)

evidence_path = Path("docs/client-scanner-evidence-1.1.0-beta.1.md")
evidence = evidence_path.read_text()
evidence = replace_one(
    evidence,
    "- configured precise resource-pack paths block ZIP or unpacked packs;\n",
    "- configured precise resource-pack path prefixes block ZIP or unpacked packs without matching nested documentation paths;\n",
    "evidence prefix wording",
)
evidence = replace_one(
    evidence,
    "- malformed archives follow the configured fail-open/fail-closed policy;\n",
    "- malformed archives and oversized metadata follow the configured fail-open/fail-closed policy;\n",
    "evidence text limit wording",
)
evidence_path.write_text(evidence)

policy_path = Path("docs/production-blocking-policy-1.1.0.md")
policy = policy_path.read_text()
policy = replace_one(
    policy,
    "- unit tests lock the approved block and non-block boundaries;\n",
    "- unit tests lock the approved block and non-block boundaries, including root-prefix matching;\n",
    "policy acceptance prefix wording",
)
policy = replace_one(
    policy,
    "- the prohibited OptiFine lightmap fixture still blocks at startup and runtime;\n",
    "- the prohibited OptiFine lightmap fixture still blocks at startup and runtime;\n"
    "- oversized metadata produces an explicit warning or block according to `failClosed`;\n",
    "policy acceptance text limit wording",
)
policy_path.write_text(policy)

for temporary in (
    Path(".github/workflows/apply-final-review-fixes.yml"),
    Path(".github/workflows/trigger-final-review-fixes.yml"),
    Path(".github/scripts/apply-final-review-fixes.py"),
):
    temporary.unlink(missing_ok=True)
