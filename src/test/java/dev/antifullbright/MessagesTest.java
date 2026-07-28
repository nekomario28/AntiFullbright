package dev.antifullbright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MessagesTest {
    @Test
    void englishAndJapaneseMessagesRemainAvailable() {
        String originalLanguage = AntiFullbrightConfig.LANGUAGE.get();
        try {
            AntiFullbrightConfig.LANGUAGE.set("en_us");
            assertTrue(Messages.warning(1).startsWith("Warning:"));
            assertTrue(Messages.exclusion("operator").contains("server operator"));
            assertTrue(Messages.reloaded(2).contains("2 changed values"));

            AntiFullbrightConfig.LANGUAGE.set("ja_jp");
            assertTrue(Messages.warning(1).startsWith("警告:"));
            assertTrue(Messages.exclusion("operator").contains("サーバーOP"));
            assertTrue(Messages.reloaded(2).contains("変更 2 件"));
        } finally {
            AntiFullbrightConfig.LANGUAGE.set(originalLanguage);
        }
    }
}
