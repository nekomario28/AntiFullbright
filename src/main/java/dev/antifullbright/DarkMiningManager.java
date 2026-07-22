package dev.antifullbright;

import dev.antifullbright.DarkMiningState.LightSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class DarkMiningManager {
    private static final double TELEPORT_DISTANCE_SQUARED = 16.0 * 16.0;
    private final Map<UUID, DarkMiningState> states = new HashMap<>();
    private final Map<UUID, LinkedHashMap<PlacedBlockKey, Long>> placedBlocks = new HashMap<>();
    private final WarningSavedData transientWarnings = new WarningSavedData();
    private final EvidenceLogger evidenceLogger = new EvidenceLogger();
    private int tickCounter;

    void onBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        UUID uuid = player.getUUID();
        DarkMiningState session = states.computeIfAbsent(uuid, ignored -> new DarkMiningState());

        Optional<String> exclusion = basicExclusion(player);
        if (exclusion.isPresent()) {
            session.resetSession();
            return;
        }
        if (!state.is(ModTags.DARK_MINING_COUNTED_BLOCKS)) return;
        if (consumePlayerPlacedBlock(uuid, level.dimension(), pos)) return;

        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        if (!isLoaded(level, eyePos) || !isLoaded(level, pos)) {
            session.resetSession();
            return;
        }

        LightSnapshot eyeLight = light(level, eyePos);
        LightSnapshot blockLight = light(level, pos);
        if (!isRequiredDarkness(eyeLight) || !isRequiredDarkness(blockLight)) {
            session.resetSession();
            return;
        }

        long now = System.currentTimeMillis();
        if (session.hasSession() && now - session.lastQualifyingBreakAt >= inactivityMillis()) {
            session.resetSession();
        }
        if (!session.hasSession()) {
            session.darkActivityStartedAt = now;
            session.graceUntil = holdsLightSource(player)
                    ? now + AntiFullbrightConfig.TORCH_HOLDING_GRACE_SECONDS.getAsInt() * 1_000L
                    : now;
        }

        session.lastQualifyingBreakAt = now;
        session.lastEyeLight = eyeLight;
        session.lastBlockLight = blockLight;
        session.lastBrokenPos = pos.immutable();
        session.lastBrokenBlock = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        if (now < session.graceUntil && holdsLightSource(player)) return;
        if (!session.hasCountedSession()) session.countedStartedAt = now;
        session.countedBlocks++;

        if (session.sessionSeconds(now) >= AntiFullbrightConfig.CONTINUOUS_MINING_SECONDS.getAsInt()
                && session.countedBlocks >= AntiFullbrightConfig.MINIMUM_BLOCKS.getAsInt()) {
            issueWarning(player, session, now);
            session.resetSession();
        }
    }

    void onPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player instanceof FakePlayer) return;
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();

        if (AntiFullbrightConfig.PLACED_BLOCK_TRACKING_ENABLED.getAsBoolean()) {
            int maximum = AntiFullbrightConfig.PLACED_BLOCK_TRACKING_MAXIMUM_ENTRIES_PER_PLAYER.getAsInt();
            if (maximum > 0) {
                LinkedHashMap<PlacedBlockKey, Long> entries = placedBlocks.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
                purgeExpired(entries, now);
                PlacedBlockKey key = new PlacedBlockKey(level.dimension(), pos.asLong());
                entries.remove(key);
                entries.put(key, now);
                while (entries.size() > maximum) {
                    Iterator<PlacedBlockKey> iterator = entries.keySet().iterator();
                    iterator.next();
                    iterator.remove();
                }
            }
        }

        boolean taggedSource = state.getBlock().asItem().getDefaultInstance().is(ModTags.DARK_MINING_LIGHT_SOURCES);
        int emittedLight = state.getLightEmission(level, pos);
        if (taggedSource || emittedLight > 0) {
            DarkMiningState session = states.computeIfAbsent(uuid, ignored -> new DarkMiningState());
            session.lastLightPlacementAt = now;
            session.lastLightPlacementPos = pos.immutable();
            if (emittedLight > 0 || (isLoaded(level, pos) && level.getBrightness(LightLayer.BLOCK, pos) > 0)) {
                session.resetSession();
            }
        }
    }

    void tick(MinecraftServer server) {
        if (++tickCounter % 20 != 0) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, DarkMiningState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DarkMiningState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            DarkMiningState session = entry.getValue();
            boolean teleported = session.positionInitialized
                    && (!session.lastDimension.equals(player.level().dimension())
                    || player.distanceToSqr(session.lastX, session.lastY, session.lastZ) > TELEPORT_DISTANCE_SQUARED);
            session.rememberPosition(player.level().dimension(), player.getX(), player.getY(), player.getZ());
            if (!session.hasSession()) continue;
            if (teleported || basicExclusion(player).isPresent()
                    || now - session.lastQualifyingBreakAt >= inactivityMillis()) {
                session.resetSession();
                continue;
            }

            ServerLevel level = player.serverLevel();
            BlockPos eyePos = BlockPos.containing(player.getEyePosition());
            if (!isLoaded(level, eyePos) || !isRequiredDarkness(light(level, eyePos))) {
                session.resetSession();
            }
        }

        if (!AntiFullbrightConfig.PLACED_BLOCK_TRACKING_ENABLED.getAsBoolean()
                || AntiFullbrightConfig.PLACED_BLOCK_TRACKING_MAXIMUM_ENTRIES_PER_PLAYER.getAsInt() == 0) {
            placedBlocks.clear();
        } else if (tickCounter % 1_200 == 0) {
            placedBlocks.values().forEach(entries -> purgeExpired(entries, now));
            placedBlocks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    void resetSession(UUID uuid) {
        DarkMiningState state = states.get(uuid);
        if (state != null) state.resetSession();
    }

    void logout(UUID uuid) {
        states.remove(uuid);
        placedBlocks.remove(uuid);
    }

    void unloadChunk(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        placedBlocks.values().forEach(entries -> entries.keySet().removeIf(key -> {
            BlockPos pos = BlockPos.of(key.position);
            return key.dimension.equals(dimension) && (pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z;
        }));
        placedBlocks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    WarningSavedData.WarningSnapshot warningStatus(MinecraftServer server, UUID uuid) {
        return warningStore(server).get(uuid, System.currentTimeMillis(), decayMillis());
    }

    void resetWarnings(MinecraftServer server, ServerPlayer player) {
        warningStore(server).set(player.getUUID(), 0, System.currentTimeMillis());
        resetSession(player.getUUID());
    }

    void setWarnings(MinecraftServer server, ServerPlayer player, int count) {
        warningStore(server).set(player.getUUID(), count, System.currentTimeMillis());
    }

    List<Component> statusLines(MinecraftServer server, ServerPlayer player) {
        long now = System.currentTimeMillis();
        DarkMiningState state = states.get(player.getUUID());
        WarningSavedData.WarningSnapshot warning = warningStatus(server, player.getUUID());
        long seconds = state == null ? 0 : state.sessionSeconds(now);
        int blocks = state == null ? 0 : state.countedBlocks;
        return List.of(
                Component.literal(Messages.statusTitle(player.getGameProfile().getName())),
                Component.literal(Messages.warningStatus(warning.count(), formatTime(warning.lastWarningEpochMillis(), true))),
                Component.literal(Messages.sessionStatus(seconds, blocks)));
    }

    List<Component> debugLines(MinecraftServer server, ServerPlayer player) {
        long now = System.currentTimeMillis();
        DarkMiningState state = states.get(player.getUUID());
        ServerLevel level = player.serverLevel();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        BlockPos feet = player.blockPosition();
        LightSnapshot eyeLight = isLoaded(level, eyes) ? light(level, eyes) : LightSnapshot.UNKNOWN;
        LightSnapshot feetLight = isLoaded(level, feet) ? light(level, feet) : LightSnapshot.UNKNOWN;
        WarningSavedData.WarningSnapshot warning = warningStatus(server, player.getUUID());
        List<Component> result = new ArrayList<>();
        result.add(Component.literal(Messages.debugTitle(player.getGameProfile().getName())));
        result.add(Component.literal(Messages.coordinate(player.getY(), player.blockPosition().getY(),
                AntiFullbrightConfig.MAXIMUM_Y.getAsInt())));
        result.add(Component.literal(Messages.light("eyes", eyeLight.block(), eyeLight.sky())));
        result.add(Component.literal(Messages.light("feet", feetLight.block(), feetLight.sky())));
        result.add(Component.literal(Messages.sessionStatus(state == null ? 0 : state.sessionSeconds(now),
                state == null ? 0 : state.countedBlocks)));
        result.add(Component.literal(Messages.grace(state == null ? 0 : state.graceRemainingSeconds(now))));
        result.add(Component.literal(Messages.warningStatus(warning.count(), formatTime(warning.lastWarningEpochMillis(), true))));
        String exclusion = basicExclusion(player).orElseGet(() -> {
            if (!isLoaded(level, eyes)) return "eye_chunk_unloaded";
            if (!isRequiredDarkness(eyeLight)) return "eye_not_dark";
            return "none";
        });
        result.add(Component.literal(Messages.exclusion(exclusion)));
        return result;
    }

    void close() {
        evidenceLogger.close();
        states.clear();
        placedBlocks.clear();
    }

    private void issueWarning(ServerPlayer player, DarkMiningState session, long now) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        WarningSavedData.WarningSnapshot warning = warningStore(server).increment(player.getUUID(), now, decayMillis());
        boolean kick = warning.count() >= AntiFullbrightConfig.WARNINGS_BEFORE_KICK.getAsInt();

        Component playerMessage = Component.literal(Messages.warning(warning.count()));
        player.sendSystemMessage(playerMessage);
        player.displayClientMessage(playerMessage, true);

        if (warning.count() >= AntiFullbrightConfig.NOTIFY_OPERATORS_AT_WARNING.getAsInt()) {
            Component operatorMessage = Component.literal(Messages.operatorWarning(
                    player.getGameProfile().getName(), warning.count()));
            for (ServerPlayer operator : server.getPlayerList().getPlayers()) {
                if (operator != player && operator.hasPermissions(2)) operator.sendSystemMessage(operatorMessage);
            }
        }

        evidenceLogger.log(server, evidence(player, session, warning.count(), now, kick ? "kick" : "warning"));
        if (kick) player.connection.disconnect(Component.literal(Messages.kick()));
    }

    private EvidenceLogger.Evidence evidence(ServerPlayer player, DarkMiningState state, int warningCount, long now, String action) {
        return new EvidenceLogger.Evidence(
                Instant.ofEpochMilli(now).toString(),
                player.getGameProfile().getName(),
                player.getUUID().toString(),
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                warningCount,
                state.sessionSeconds(now),
                state.countedBlocks,
                state.lastEyeLight.block(), state.lastEyeLight.sky(),
                state.lastBlockLight.block(), state.lastBlockLight.sky(),
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString(),
                BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString(),
                player.hasEffect(MobEffects.NIGHT_VISION),
                formatTime(state.lastLightPlacementAt, false),
                state.lastLightPlacementPos == null ? null : state.lastLightPlacementPos.toShortString(),
                state.lastBrokenBlock,
                action);
    }

    private Optional<String> basicExclusion(ServerPlayer player) {
        if (!AntiFullbrightConfig.ENABLED.getAsBoolean()) return Optional.of("disabled");
        if (player instanceof FakePlayer) return Optional.of("fake_player");
        if (player.isCreative()) return Optional.of("creative");
        if (player.isSpectator()) return Optional.of("spectator");
        if (player.blockPosition().getY() > AntiFullbrightConfig.MAXIMUM_Y.getAsInt()) return Optional.of("above_maximum_y");
        if (AntiFullbrightConfig.EXCLUDE_NIGHT_VISION.getAsBoolean() && player.hasEffect(MobEffects.NIGHT_VISION)) {
            return Optional.of("night_vision");
        }
        if (AntiFullbrightConfig.EXCLUDE_OPERATORS.getAsBoolean() && player.hasPermissions(2)) {
            return Optional.of("operator");
        }
        return Optional.empty();
    }

    private boolean consumePlayerPlacedBlock(UUID uuid, ResourceKey<Level> dimension, BlockPos pos) {
        if (!AntiFullbrightConfig.PLACED_BLOCK_TRACKING_ENABLED.getAsBoolean()) return false;
        LinkedHashMap<PlacedBlockKey, Long> entries = placedBlocks.get(uuid);
        if (entries == null) return false;
        purgeExpired(entries, System.currentTimeMillis());
        return entries.remove(new PlacedBlockKey(dimension, pos.asLong())) != null;
    }

    private void purgeExpired(LinkedHashMap<PlacedBlockKey, Long> entries, long now) {
        long expiration = AntiFullbrightConfig.PLACED_BLOCK_TRACKING_EXPIRATION_MINUTES.getAsInt() * 60_000L;
        entries.entrySet().removeIf(entry -> now - entry.getValue() >= expiration);
    }

    private WarningSavedData warningStore(MinecraftServer server) {
        return AntiFullbrightConfig.PERSIST_WARNINGS.getAsBoolean() ? WarningSavedData.get(server) : transientWarnings;
    }

    private static long decayMillis() {
        return AntiFullbrightConfig.WARNING_DECAY_MINUTES.getAsInt() * 60_000L;
    }

    private static long inactivityMillis() {
        return AntiFullbrightConfig.INACTIVITY_RESET_SECONDS.getAsInt() * 1_000L;
    }

    private static boolean holdsLightSource(ServerPlayer player) {
        return player.getMainHandItem().is(ModTags.DARK_MINING_LIGHT_SOURCES)
                || player.getOffhandItem().is(ModTags.DARK_MINING_LIGHT_SOURCES);
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static LightSnapshot light(ServerLevel level, BlockPos pos) {
        return new LightSnapshot(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }

    private static boolean isRequiredDarkness(LightSnapshot light) {
        return light.block() == AntiFullbrightConfig.REQUIRED_BLOCK_LIGHT.getAsInt()
                && light.sky() == AntiFullbrightConfig.REQUIRED_SKY_LIGHT.getAsInt();
    }

    private static String formatTime(long epochMillis, boolean localized) {
        return epochMillis <= 0 ? (localized ? Messages.never() : "never") : Instant.ofEpochMilli(epochMillis).toString();
    }

    private record PlacedBlockKey(ResourceKey<Level> dimension, long position) {}
}
