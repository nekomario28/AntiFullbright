package dev.antifullbright.client;

import dev.antifullbright.AntiFullbright;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/** Physical-client-only entrypoint. Dedicated servers never load this class. */
@Mod(value = AntiFullbright.MOD_ID, dist = Dist.CLIENT)
public final class AntiFullbrightClient {
    public AntiFullbrightClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientScanConfig.SPEC);
        ClientScanBootstrap.register(modBus);
    }
}
