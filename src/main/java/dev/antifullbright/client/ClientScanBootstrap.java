package dev.antifullbright.client;

import dev.antifullbright.AntiFullbright;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Starts the client scan after the client config has loaded. */
public final class ClientScanBootstrap {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean RUNTIME_BLOCK_SHOWN = new AtomicBoolean();
    private static volatile ResourcePackWatcher watcher;

    private ClientScanBootstrap() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientScanBootstrap::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        if (!ClientScanConfig.ENABLED.getAsBoolean() || !STARTED.compareAndSet(false, true)) {
            return;
        }

        Path gameDirectory = FMLPaths.GAMEDIR.get();
        ContentScanner.Report report = scan(gameDirectory);
        logReport("Client content scan", report);
        if (report.hasBlockingFindings()) {
            throw new IllegalStateException("AntiFullbright blocked client setup. " + report.summary());
        }

        if (ClientScanConfig.WATCH_RESOURCE_PACKS.getAsBoolean()
                && ClientScanConfig.SCAN_RESOURCE_PACKS.getAsBoolean()) {
            startWatcher(gameDirectory.resolve("resourcepacks"));
        }
    }

    public static ContentScanner.Report scan(Path gameDirectory) {
        return ContentScanner.scan(
                gameDirectory,
                ClientScanConfig.policy(),
                ClientScanConfig.SCAN_MODS.getAsBoolean(),
                ClientScanConfig.SCAN_RESOURCE_PACKS.getAsBoolean(),
                ownArchive()
        );
    }

    private static Set<Path> ownArchive() {
        try {
            var source = AntiFullbright.class.getProtectionDomain().getCodeSource();
            if (source == null) return Set.of();
            Path path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? Set.of(path) : Set.of();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            AntiFullbright.LOGGER.warn("Could not identify the AntiFullbright archive; it will be scanned normally", exception);
            return Set.of();
        }
    }

    private static void startWatcher(Path resourcePacksDirectory) {
        ResourcePackWatcher created = new ResourcePackWatcher(
                resourcePacksDirectory,
                ClientScanConfig.WATCH_DEBOUNCE_MILLIS.getAsInt(),
                () -> rescanResourcePacks(resourcePacksDirectory),
                ClientScanBootstrap::watcherFailed
        );
        try {
            created.start();
            watcher = created;
            Runtime.getRuntime().addShutdownHook(new Thread(ClientScanBootstrap::closeWatcher,
                    "antifullbright-watch-shutdown"));
            AntiFullbright.LOGGER.info("Watching resource packs for changes: {}", resourcePacksDirectory);
        } catch (IOException exception) {
            watcherFailed(exception);
        }
    }

    private static void rescanResourcePacks(Path resourcePacksDirectory) {
        ContentScanner.Report report = ContentScanner.scanResourcePacks(
                resourcePacksDirectory, ClientScanConfig.policy());
        logReport("Resource-pack rescan", report);
        if (report.hasBlockingFindings()) {
            blockRunningClient(report.summary());
        }
    }

    private static void watcherFailed(Exception exception) {
        String message = "Resource-pack watcher failed: " + exception.getClass().getSimpleName()
                + ": " + String.valueOf(exception.getMessage());
        if (ClientScanConfig.FAIL_CLOSED.getAsBoolean()) {
            blockRunningClient(message);
        } else {
            AntiFullbright.LOGGER.error(message, exception);
        }
    }

    private static void logReport(String operation, ContentScanner.Report report) {
        if (report.hasBlockingFindings()) {
            AntiFullbright.LOGGER.error("{} completed with blocking findings: {}", operation, report.summary());
        } else if (report.hasWarnings()) {
            AntiFullbright.LOGGER.warn("{} completed with warnings: {}", operation, report.summary());
        } else {
            AntiFullbright.LOGGER.info("{} completed: {}", operation, report.summary());
        }
    }

    private static void blockRunningClient(String reason) {
        AntiFullbright.LOGGER.error("AntiFullbright blocked local content while the client was running. {}", reason);
        closeWatcher();
        if (!ClientScanConfig.DISCONNECT_ON_RUNTIME_DETECTION.getAsBoolean()
                || !RUNTIME_BLOCK_SHOWN.compareAndSet(false, true)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Component title = Component.literal("AntiFullbright blocked local content");
            Component details = Component.literal(reason);
            DisconnectedScreen screen = new DisconnectedScreen(new TitleScreen(), title, details);
            if (minecraft.level != null || minecraft.getConnection() != null) {
                minecraft.disconnect(screen);
            } else {
                minecraft.forceSetScreen(screen);
            }
        });
    }

    private static synchronized void closeWatcher() {
        ResourcePackWatcher current = watcher;
        watcher = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (IOException exception) {
            AntiFullbright.LOGGER.debug("Failed to close resource-pack watcher", exception);
        }
    }
}
