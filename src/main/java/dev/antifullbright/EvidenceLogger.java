package dev.antifullbright;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class EvidenceLogger {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "antifullbright-evidence-writer");
        thread.setDaemon(true);
        return thread;
    });

    void log(MinecraftServer server, Evidence evidence) {
        String json = GSON.toJson(evidence);
        AntiFullbright.LOGGER.warn("Dark-mining detection: {}", json);
        if (!AntiFullbrightConfig.ENABLE_DEDICATED_LOG.getAsBoolean()) return;

        Path logFile = server.getServerDirectory().resolve("logs").resolve("dark-mining-detections.jsonl");
        writer.execute(() -> {
            try {
                Files.createDirectories(logFile.getParent());
                Files.writeString(logFile, json + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                AntiFullbright.LOGGER.error("Could not append dark-mining evidence to {}", logFile, exception);
            }
        });
    }

    void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                AntiFullbright.LOGGER.warn("Timed out while flushing the dark-mining evidence log");
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    record Evidence(
            String timestamp,
            String playerName,
            String uuid,
            String dimension,
            double x,
            double y,
            double z,
            int warningCount,
            long continuousMiningSeconds,
            int countedBlocks,
            int eyeBlockLight,
            int eyeSkyLight,
            int brokenBlockLight,
            int brokenSkyLight,
            String mainHandItem,
            String offHandItem,
            boolean nightVision,
            String lastLightPlacementTime,
            String lastLightPlacementPosition,
            String lastBrokenBlock,
            String action
    ) {}
}
