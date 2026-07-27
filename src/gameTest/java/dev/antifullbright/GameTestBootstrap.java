package dev.antifullbright;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Test-only entrypoint. This source set is not included in the production JAR. */
@Mod(AntiFullbright.MOD_ID)
public final class GameTestBootstrap {
    public GameTestBootstrap(IEventBus modBus) {
        modBus.addListener(GameTestBootstrap::registerGameTests);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ServerContractGameTests.class);
    }
}
