package dev.antifullbright;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

/** Attaches an isolated in-memory NeoForge config to the shared specification for one test. */
final class ConfigTestSupport implements AutoCloseable {
    private ConfigTestSupport() {}

    static ConfigTestSupport attachDefaults() {
        try {
            CommentedConfig config = CommentedConfig.inMemory();
            AntiFullbrightConfig.SPEC.correct(config);

            Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> constructor = loadedConfigClass.getDeclaredConstructor(
                    CommentedConfig.class, Path.class, ModConfig.class);
            constructor.setAccessible(true);
            IConfigSpec.ILoadedConfig loaded = (IConfigSpec.ILoadedConfig)
                    constructor.newInstance(config, null, null);
            AntiFullbrightConfig.SPEC.acceptConfig(loaded);
            return new ConfigTestSupport();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not attach an in-memory NeoForge config", exception);
        }
    }

    @Override
    public void close() {
        AntiFullbrightConfig.SPEC.acceptConfig(null);
    }
}
