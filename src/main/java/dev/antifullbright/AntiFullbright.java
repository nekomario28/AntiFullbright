package dev.antifullbright;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;

@Mod(AntiFullbright.MOD_ID)
public final class AntiFullbright {
    public static final String MOD_ID = "antifullbright";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String ENABLE_GAMETESTS_PROPERTY = "antifullbright.enableGameTests";
    private static final String GAMETEST_CLASS = "dev.antifullbright.ServerContractGameTests";

    public AntiFullbright(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, AntiFullbrightConfig.SPEC);
        NeoForge.EVENT_BUS.register(DarkMiningEvents.class);

        if (Boolean.getBoolean(ENABLE_GAMETESTS_PROPERTY)) {
            modBus.addListener(AntiFullbright::registerGameTests);
        }
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        try {
            event.register(Class.forName(GAMETEST_CLASS));
            LOGGER.info("Registered AntiFullbright GameTests");
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(
                    "GameTest registration was enabled but the isolated GameTest source set is missing", error);
        }
    }
}
