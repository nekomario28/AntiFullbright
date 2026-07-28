package dev.antifullbright;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DarkMiningStateTest {
    @Test
    void sessionAndGraceUseElapsedMillisecondsDeterministically() {
        DarkMiningState state = new DarkMiningState();
        state.darkActivityStartedAt = 1_000L;
        state.countedStartedAt = 2_000L;
        state.graceUntil = 6_001L;

        assertTrue(state.hasSession());
        assertTrue(state.hasCountedSession());
        assertEquals(3L, state.sessionSeconds(5_999L));
        assertEquals(2L, state.graceRemainingSeconds(4_001L));
    }

    @Test
    void resetClearsOnlyActiveSessionEvidence() {
        DarkMiningState state = new DarkMiningState();
        state.darkActivityStartedAt = 1L;
        state.graceUntil = 2L;
        state.countedStartedAt = 3L;
        state.lastQualifyingBreakAt = 4L;
        state.countedBlocks = 5;
        state.lastBrokenPos = new BlockPos(1, 2, 3);
        state.lastBrokenBlock = "minecraft:stone";
        state.lastLightPlacementAt = 6L;
        state.lastLightPlacementPos = new BlockPos(4, 5, 6);

        state.resetSession();

        assertFalse(state.hasSession());
        assertFalse(state.hasCountedSession());
        assertEquals(0, state.countedBlocks);
        assertNull(state.lastBrokenPos);
        assertEquals("minecraft:air", state.lastBrokenBlock);
        assertEquals(6L, state.lastLightPlacementAt);
        assertEquals(new BlockPos(4, 5, 6), state.lastLightPlacementPos);
    }
}
