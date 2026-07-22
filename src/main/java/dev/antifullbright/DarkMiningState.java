package dev.antifullbright;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class DarkMiningState {
    long darkActivityStartedAt;
    long graceUntil;
    long countedStartedAt;
    long lastQualifyingBreakAt;
    int countedBlocks;
    LightSnapshot lastEyeLight = LightSnapshot.UNKNOWN;
    LightSnapshot lastBlockLight = LightSnapshot.UNKNOWN;
    BlockPos lastBrokenPos;
    String lastBrokenBlock = "minecraft:air";
    long lastLightPlacementAt;
    BlockPos lastLightPlacementPos;
    ResourceKey<Level> lastDimension;
    double lastX;
    double lastY;
    double lastZ;
    boolean positionInitialized;

    boolean hasSession() {
        return darkActivityStartedAt > 0;
    }

    boolean hasCountedSession() {
        return countedStartedAt > 0;
    }

    long sessionSeconds(long now) {
        return countedStartedAt == 0 ? 0 : Math.max(0, (now - countedStartedAt) / 1_000L);
    }

    long graceRemainingSeconds(long now) {
        return Math.max(0, (graceUntil - now + 999L) / 1_000L);
    }

    void resetSession() {
        darkActivityStartedAt = 0;
        graceUntil = 0;
        countedStartedAt = 0;
        lastQualifyingBreakAt = 0;
        countedBlocks = 0;
        lastEyeLight = LightSnapshot.UNKNOWN;
        lastBlockLight = LightSnapshot.UNKNOWN;
        lastBrokenPos = null;
        lastBrokenBlock = "minecraft:air";
    }

    void rememberPosition(ResourceKey<Level> dimension, double x, double y, double z) {
        lastDimension = dimension;
        lastX = x;
        lastY = y;
        lastZ = z;
        positionInitialized = true;
    }

    record LightSnapshot(int block, int sky) {
        static final LightSnapshot UNKNOWN = new LightSnapshot(-1, -1);
    }
}
