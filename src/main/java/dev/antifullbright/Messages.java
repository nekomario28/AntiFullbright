package dev.antifullbright;

import java.util.Locale;

final class Messages {
    private Messages() {}

    static boolean japanese() {
        return !"en_us".equalsIgnoreCase(AntiFullbrightConfig.LANGUAGE.get());
    }

    static String warning(int count) {
        if (japanese()) {
            return count == 1
                    ? "警告: 完全な暗所での長時間採掘を検出しました。光源を設置してから採掘を続けてください。"
                    : "最終警告: 完全な暗所での長時間採掘を再び検出しました。";
        }
        return count == 1
                ? "Warning: prolonged mining in complete darkness was detected. Place a light source before continuing."
                : "FINAL WARNING: prolonged mining in complete darkness was detected again.";
    }

    static String kick() {
        return japanese()
                ? "完全な暗所での長時間採掘が複数回検出されました。松明などの光源を設置して採掘してください。"
                : "Prolonged mining in complete darkness was detected multiple times. Please place a light source such as a torch while mining.";
    }

    static String operatorWarning(String playerName, int count) {
        return japanese()
                ? "[AntiFullbright] " + playerName + " が暗所採掘の警告レベル " + count + " に到達しました。"
                : "[AntiFullbright] " + playerName + " reached dark-mining warning level " + count + ".";
    }

    static String statusTitle(String playerName) {
        return japanese() ? "暗所採掘ステータス: " + playerName : "Dark-mining status: " + playerName;
    }

    static String warningStatus(int count, String lastWarning) {
        return japanese()
                ? "警告レベル=" + count + "、最終警告=" + lastWarning
                : "warningLevel=" + count + ", lastWarning=" + lastWarning;
    }

    static String sessionStatus(long seconds, int blocks) {
        return japanese()
                ? "セッション秒数=" + seconds + "、対象破壊数=" + blocks
                : "sessionSeconds=" + seconds + ", countedBlocks=" + blocks;
    }

    static String debugTitle(String playerName) {
        return japanese() ? "暗所採掘デバッグ: " + playerName : "Dark-mining debug: " + playerName;
    }

    static String coordinate(double y, int blockY, int maximumY) {
        return japanese()
                ? String.format(Locale.ROOT, "Y=%.2f（ブロックY=%d、最大Y=%d）", y, blockY, maximumY)
                : String.format(Locale.ROOT, "Y=%.2f (blockY=%d, maximumY=%d)", y, blockY, maximumY);
    }

    static String light(String location, int block, int sky) {
        String localizedLocation = japanese()
                ? (location.equals("eyes") ? "目の位置" : "足元")
                : (location.equals("eyes") ? "eye position" : "feet position");
        return japanese()
                ? localizedLocation + "の光: ブロック=" + block + "、天空=" + sky
                : localizedLocation + " light: block=" + block + ", sky=" + sky;
    }

    static String grace(long seconds) {
        return japanese()
                ? "光源所持猶予の残り秒数=" + seconds
                : "light-holding grace remaining seconds=" + seconds;
    }

    static String exclusion(String reason) {
        String localized = switch (reason) {
            case "disabled" -> japanese() ? "設定で無効" : "disabled by configuration";
            case "fake_player" -> japanese() ? "FakePlayer" : "fake player";
            case "creative" -> japanese() ? "クリエイティブモード" : "creative mode";
            case "spectator" -> japanese() ? "スペクテイターモード" : "spectator mode";
            case "above_maximum_y" -> japanese() ? "maximumY より上" : "above maximumY";
            case "night_vision" -> japanese() ? "暗視効果" : "night vision effect";
            case "operator" -> japanese() ? "サーバーOP" : "server operator";
            case "eye_chunk_unloaded" -> japanese() ? "目の位置のチャンクが未ロード" : "eye-position chunk is not loaded";
            case "eye_not_dark" -> japanese() ? "目の位置が設定された暗さではない" : "eye position is not at configured darkness";
            default -> japanese() ? "なし" : "none";
        };
        return japanese() ? "除外理由=" + localized : "excludedReason=" + localized;
    }

    static String reset(String playerName) {
        return japanese()
                ? playerName + " の暗所採掘警告とセッションをリセットしました。"
                : "Reset dark-mining warnings and session for " + playerName + ".";
    }

    static String setWarning(String playerName, int count) {
        return japanese()
                ? playerName + " の警告レベルを " + count + " に設定しました。"
                : "Set " + playerName + " warning level to " + count + ".";
    }

    static String reloaded(int changed) {
        return japanese()
                ? "AntiFullbright のサーバー設定を再読込しました（変更 " + changed + " 件）。"
                : "Reloaded AntiFullbright server config (" + changed + " changed values).";
    }

    static String reloadFailed(String detail) {
        return japanese() ? "設定を再読込できませんでした: " + detail : "Could not reload config: " + detail;
    }

    static String never() {
        return japanese() ? "なし" : "never";
    }
}
