package dev.antifullbright;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Test-only mod entrypoint. This source set is not included in the production JAR. */
@Mod(GameTestBootstrap.MOD_ID)
public final class GameTestBootstrap {
    public static final String MOD_ID = "antifullbright_gametest";

    public GameTestBootstrap(IEventBus modBus) {
        modBus.addListener(GameTestBootstrap::registerGameTests);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ServerContractGameTests.class);
    }
}
