package dev.antifullbright.client;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Watches the resource-pack directory recursively and coalesces changes into full rescans. */
public final class ResourcePackWatcher implements AutoCloseable {
    private final Path root;
    private final Path rootParent;
    private final long debounceMillis;
    private final Runnable rescan;
    private final Consumer<Exception> errorHandler;
    private final Map<WatchKey, Path> watchedDirectories = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread thread;
    private volatile boolean running;

    public ResourcePackWatcher(Path root, long debounceMillis, Runnable rescan, Consumer<Exception> errorHandler) {
        this.root = root.toAbsolutePath().normalize();
        this.rootParent = this.root.getParent();
        this.debounceMillis = Math.max(100L, debounceMillis);
        this.rescan = rescan;
        this.errorHandler = errorHandler;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        Files.createDirectories(root);
        watchService = FileSystems.getDefault().newWatchService();
        if (rootParent != null) {
            registerDirectory(rootParent);
        }
        registerRecursively(root);
        running = true;
        thread = new Thread(this::watchLoop, "antifullbright-resourcepack-watch");
        thread.setDaemon(true);
        thread.start();
    }

    private void watchLoop() {
        try {
            while (running) {
                WatchKey first = watchService.take();
                boolean changed = processKey(first);
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(debounceMillis);
                while (running) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        break;
                    }
                    WatchKey additional = watchService.poll(remaining, TimeUnit.NANOSECONDS);
                    if (additional == null) {
                        break;
                    }
                    changed |= processKey(additional);
                    deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(debounceMillis);
                }
                if (changed && running) {
                    rescan.run();
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            // Normal shutdown.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (running) {
                errorHandler.accept(exception);
            }
        }
    }

    private boolean processKey(WatchKey key) throws IOException {
        Path directory = watchedDirectories.get(key);
        if (directory == null) {
            key.reset();
            return true;
        }

        boolean changed = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                registerRecursively(root);
                changed = true;
                continue;
            }
            Object context = event.context();
            if (!(context instanceof Path relative)) {
                changed = true;
                continue;
            }

            Path affected = directory.resolve(relative).toAbsolutePath().normalize();
            if (directory.equals(rootParent) && !affected.equals(root)) {
                continue;
            }

            changed = true;
            if (kind == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(affected, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(affected)) {
                registerRecursively(affected);
            }
        }

        if (!key.reset()) {
            watchedDirectories.remove(key);
        }
        return changed;
    }

    private void registerRecursively(Path start) throws IOException {
        if (!Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(start)) {
            return;
        }
        try (var paths = Files.walk(start)) {
            for (Path directory : paths
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .toList()) {
                registerDirectory(directory);
            }
        }
    }

    private void registerDirectory(Path directory) throws IOException {
        WatchService currentService = watchService;
        if (currentService == null) {
            throw new ClosedWatchServiceException();
        }
        WatchKey key = directory.register(
                currentService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );
        watchedDirectories.put(key, directory.toAbsolutePath().normalize());
    }

    @Override
    public synchronized void close() throws IOException {
        running = false;
        WatchService currentService = watchService;
        watchService = null;
        if (currentService != null) {
            currentService.close();
        }
        Thread currentThread = thread;
        thread = null;
        if (currentThread != null && currentThread != Thread.currentThread()) {
            currentThread.interrupt();
        }
        watchedDirectories.clear();
    }
}
