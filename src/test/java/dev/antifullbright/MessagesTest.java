package dev.antifullbright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MessagesTest {
    @Test
    void englishMessagesCoverPlayerOperatorAndAdministratorOutput() {
        try (ConfigTestSupport ignored = ConfigTestSupport.attachDefaults()) {
            AntiFullbrightConfig.LANGUAGE.set("en_us");

            assertAll(
                    () -> assertTrue(Messages.warning(1).startsWith("Warning:")),
                    () -> assertTrue(Messages.warning(2).startsWith("FINAL WARNING:")),
                    () -> assertTrue(Messages.kick().contains("detected multiple times")),
                    () -> assertEquals(
                            "[AntiFullbright] Alex reached dark-mining warning level 2.",
                            Messages.operatorWarning("Alex", 2)),
                    () -> assertEquals("Dark-mining status: Alex", Messages.statusTitle("Alex")),
                    () -> assertTrue(Messages.warningStatus(2, "now").contains("warningLevel=2")),
                    () -> assertTrue(Messages.sessionStatus(61, 20).contains("countedBlocks=20")),
                    () -> assertEquals("Dark-mining debug: Alex", Messages.debugTitle("Alex")),
                    () -> assertTrue(Messages.coordinate(-12.5, -13, 16).contains("maximumY=16")),
                    () -> assertTrue(Messages.light("eyes", 0, 0).contains("eye position light")),
                    () -> assertTrue(Messages.grace(10).contains("remaining seconds=10")),
                    () -> assertTrue(Messages.exclusion("operator").contains("server operator")),
                    () -> assertEquals(
                            "Reset dark-mining warnings and session for Alex.",
                            Messages.reset("Alex")),
                    () -> assertEquals(
                            "Set Alex warning level to 2.",
                            Messages.setWarning("Alex", 2)),
                    () -> assertTrue(Messages.reloaded(2).contains("2 changed values")),
                    () -> assertTrue(Messages.reloadFailed("broken").contains("broken")),
                    () -> assertEquals("never", Messages.never())
            );
        }
    }

    @Test
    void japaneseMessagesCoverPlayerOperatorAndAdministratorOutput() {
        try (ConfigTestSupport ignored = ConfigTestSupport.attachDefaults()) {
            AntiFullbrightConfig.LANGUAGE.set("ja_jp");

            assertAll(
                    () -> assertTrue(Messages.warning(1).startsWith("警告:")),
                    () -> assertTrue(Messages.warning(2).startsWith("最終警告:")),
                    () -> assertTrue(Messages.kick().contains("複数回検出")),
                    () -> assertEquals(
                            "[AntiFullbright] Alex が暗所採掘の警告レベル 2 に到達しました。",
                            Messages.operatorWarning("Alex", 2)),
                    () -> assertEquals("暗所採掘ステータス: Alex", Messages.statusTitle("Alex")),
                    () -> assertTrue(Messages.warningStatus(2, "現在").contains("警告レベル=2")),
                    () -> assertTrue(Messages.sessionStatus(61, 20).contains("対象破壊数=20")),
                    () -> assertEquals("暗所採掘デバッグ: Alex", Messages.debugTitle("Alex")),
                    () -> assertTrue(Messages.coordinate(-12.5, -13, 16).contains("最大Y=16")),
                    () -> assertTrue(Messages.light("eyes", 0, 0).contains("目の位置の光")),
                    () -> assertTrue(Messages.grace(10).contains("残り秒数=10")),
                    () -> assertTrue(Messages.exclusion("operator").contains("サーバーOP")),
                    () -> assertEquals(
                            "Alex の暗所採掘警告とセッションをリセットしました。",
                            Messages.reset("Alex")),
                    () -> assertEquals(
                            "Alex の警告レベルを 2 に設定しました。",
                            Messages.setWarning("Alex", 2)),
                    () -> assertTrue(Messages.reloaded(2).contains("変更 2 件")),
                    () -> assertTrue(Messages.reloadFailed("破損").contains("破損")),
                    () -> assertEquals("なし", Messages.never())
            );
        }
    }
}
