package dev.antifullbright;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class DarkMiningEvents {
    private static DarkMiningManager manager;

    private DarkMiningEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        manager();
        WarningSavedData.get(event.getServer());
        AntiFullbright.LOGGER.info("AntiFullbright dark-mining detection is ready");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (manager != null) manager.close();
        manager = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (manager == null || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        manager.onBreak(player, level, event.getPos(), event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (manager == null || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        manager.onPlace(player, level, event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (manager != null) manager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        DarkMiningCommands.register(event.getDispatcher(), manager());
        AntiFullbright.LOGGER.info("Registered /darkmining administrator commands");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (manager != null && event.getEntity() instanceof ServerPlayer player) manager.logout(player.getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        reset(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        reset(event.getEntity());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        reset(event.getEntity());
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        reset(event.getEntity());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (manager != null && event.getLevel() instanceof ServerLevel level) {
            manager.unloadChunk(level.dimension(), event.getChunk().getPos());
        }
    }

    private static void reset(net.minecraft.world.entity.Entity entity) {
        if (manager != null && entity instanceof ServerPlayer player) manager.resetSession(player.getUUID());
    }

    private static synchronized DarkMiningManager manager() {
        if (manager == null) manager = new DarkMiningManager();
        return manager;
    }
}
