package dev.antifullbright;

import com.mojang.logging.LogUtils;
import dev.antifullbright.client.ClientScanBootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AntiFullbright.MOD_ID)
public final class AntiFullbright {
    public static final String MOD_ID = "antifullbright";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AntiFullbright(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, AntiFullbrightConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, ClientScanConfig.SPEC);
            ClientScanBootstrap.register(modBus);
        }
        NeoForge.EVENT_BUS.register(DarkMiningEvents.class);
    }
}
