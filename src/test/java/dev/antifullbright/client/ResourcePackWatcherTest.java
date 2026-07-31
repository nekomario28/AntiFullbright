package dev.antifullbright.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackWatcherTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recreatingResourcePackRootRestoresNestedChangeNotifications() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("resourcepacks"));
        AtomicInteger rescans = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();

        try (ResourcePackWatcher watcher = new ResourcePackWatcher(
                root, 100L, rescans::incrementAndGet, failure::set)) {
            watcher.start();

            Files.writeString(root.resolve("before.txt"), "before");
            assertTrue(awaitGreaterThan(rescans, 0), "Initial nested change was not observed.");

            int beforeDelete = rescans.get();
            Files.delete(root.resolve("before.txt"));
            Files.delete(root);
            assertTrue(awaitGreaterThan(rescans, beforeDelete), "Root deletion was not observed.");

            int beforeRecreate = rescans.get();
            Files.createDirectory(root);
            assertTrue(awaitGreaterThan(rescans, beforeRecreate), "Root recreation was not observed.");

            int afterRecreate = rescans.get();
            Files.writeString(root.resolve("after.txt"), "after");
            assertTrue(awaitGreaterThan(rescans, afterRecreate),
                    "Changes inside the recreated root were not observed.");

            assertNull(failure.get(), "Watcher reported an unexpected failure.");
        }
    }

    private static boolean awaitGreaterThan(AtomicInteger value, int baseline) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (value.get() > baseline) {
                return true;
            }
            Thread.sleep(25L);
        }
        return false;
    }
}
