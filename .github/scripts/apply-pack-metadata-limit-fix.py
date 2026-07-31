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
    '''                if (!entry.isDirectory() && entryPath.equals("pack.mcmeta") && warning.isEmpty()) {
                    TextRead metadataRead = read(zip, entry, policy);
                    if (metadataRead.overLimit()) {
                        return Optional.of(textLimit(policy, "resourcepack", pack, entryPath));
                    }
                    warning = match(lower(metadataRead.text()), policy.suspiciousPackTokens())
                            .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
                }
''',
    '''                if (!entry.isDirectory() && entryPath.equals("pack.mcmeta")) {
                    TextRead metadataRead = read(zip, entry, policy);
                    if (metadataRead.overLimit()) {
                        return Optional.of(textLimit(policy, "resourcepack", pack, entryPath));
                    }
                    if (warning.isEmpty()) {
                        warning = match(lower(metadataRead.text()), policy.suspiciousPackTokens())
                                .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
                    }
                }
''',
    "zip pack metadata limit independent of warning",
)
scanner = replace_one(
    scanner,
    '''            if (relative.equals("pack.mcmeta")
                    && warning.isEmpty()
                    && Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
                TextRead metadataRead;
                try (InputStream input = Files.newInputStream(current)) {
                    metadataRead = read(input, policy);
                }
                if (metadataRead.overLimit()) {
                    return Optional.of(textLimit(policy, "resourcepack", pack, relative));
                }
                warning = match(lower(metadataRead.text()), policy.suspiciousPackTokens())
                        .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
            }
''',
    '''            if (relative.equals("pack.mcmeta")
                    && Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
                TextRead metadataRead;
                try (InputStream input = Files.newInputStream(current)) {
                    metadataRead = read(input, policy);
                }
                if (metadataRead.overLimit()) {
                    return Optional.of(textLimit(policy, "resourcepack", pack, relative));
                }
                if (warning.isEmpty()) {
                    warning = match(lower(metadataRead.text()), policy.suspiciousPackTokens())
                            .map(token -> warning("resourcepack", pack, "suspicious_metadata", token));
                }
            }
''',
    "directory pack metadata limit independent of warning",
)
scanner_path.write_text(scanner)

test_path = Path("src/test/java/dev/antifullbright/client/ContentScannerTest.java")
tests = test_path.read_text()
new_test = '''    @Test
    void suspiciousPackNameCannotSuppressStrictMetadataLimit() throws IOException {
        Path packs = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        String metadata = "{\\"pack\\":{\\"pack_format\\":34,\\"description\\":\\\""
                + "x".repeat(2_048) + "\\\"}}";
        writeZip(packs.resolve("fullbright-named-but-oversized.zip"), Map.of(
                "pack.mcmeta", metadata
        ));

        ContentScanner.Report report = ContentScanner.scanResourcePacks(
                packs, productionPolicy(true, 1_024));

        assertTrue(report.hasBlockingFindings());
        assertTrue(report.blockingFindings().stream()
                .anyMatch(finding -> finding.rule().equals("text_limit")));
    }

'''
tests = replace_one(
    tests,
    "    @Test\n    void exactIgnoredArchivePathIsNotScanned() throws IOException {\n",
    new_test + "    @Test\n    void exactIgnoredArchivePathIsNotScanned() throws IOException {\n",
    "strict pack metadata regression test",
)
test_path.write_text(tests)

review_path = Path("docs/final-review-1.1.0-beta.1.md")
review = review_path.read_text()
review = replace_one(
    review,
    "- oversized loader metadata warns under fail-open and blocks under fail-closed;\n",
    "- oversized loader and resource-pack metadata warns under fail-open and blocks under fail-closed, even when a filename already generated a warning;\n",
    "final review metadata wording",
)
review_path.write_text(review)

for temporary in (
    Path(".github/scripts/apply-pack-metadata-limit-fix.py"),
    Path(".github/workflows/trigger-pack-metadata-limit-fix.yml"),
):
    temporary.unlink(missing_ok=True)
