package dev.antifullbright;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DocumentationSecurityTest {
    @Test
    void englishReadmeRetainsClientScannerLimitations() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        String lower = readme.toLowerCase(Locale.ROOT);

        assertAll(
                () -> assertTrue(lower.contains("not tamper-proof")),
                () -> assertTrue(lower.contains("does not contain a server handshake")),
                () -> assertTrue(lower.contains("player can remove or modify the scanner")),
                () -> assertTrue(lower.contains("server cannot currently fix or verify")),
                () -> assertFalse(lower.contains("tamper-proof anti-cheat")),
                () -> assertFalse(lower.contains("server-enforced scanner"))
        );
    }

    @Test
    void japaneseReadmeRetainsClientScannerLimitations() throws IOException {
        String readme = Files.readString(Path.of("README_JA.md"));

        assertAll(
                () -> assertTrue(readme.contains("改変不能なアンチチートではありません")),
                () -> assertTrue(readme.contains("サーバーハンドシェイクはありません")),
                () -> assertTrue(readme.contains("プレイヤーはスキャナーを削除・改変できます")),
                () -> assertTrue(readme.contains("サーバー強制型アンチチートとしては扱いません")),
                () -> assertFalse(readme.contains("改変不能なアンチチートです")),
                () -> assertFalse(readme.contains("サーバーが検査結果を保証します"))
        );
    }
}
