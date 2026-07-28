package dev.antifullbright;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WarningSavedDataTest {
    @Test
    void warningStateRoundTripsThroughNbt() {
        UUID player = UUID.randomUUID();
        WarningSavedData original = new WarningSavedData();
        original.set(player, 3, 12_345L);

        CompoundTag encoded = original.save(new CompoundTag(), null);
        WarningSavedData restored = WarningSavedData.load(encoded, null);

        assertEquals(new WarningSavedData.WarningSnapshot(3, 12_345L),
                restored.get(player, 12_345L, 60_000L));
    }

    @Test
    void warningDecayAdvancesByWholeIntervalsAndClearsAtZero() {
        UUID player = UUID.randomUUID();
        WarningSavedData data = new WarningSavedData();
        data.set(player, 3, 1_000L);

        assertEquals(new WarningSavedData.WarningSnapshot(1, 121_000L),
                data.get(player, 121_000L, 60_000L));
        assertEquals(new WarningSavedData.WarningSnapshot(0, 0L),
                data.get(player, 181_000L, 60_000L));
    }

    @Test
    void incrementAppliesElapsedDecayBeforeAddingNewWarning() {
        UUID player = UUID.randomUUID();
        WarningSavedData data = new WarningSavedData();
        data.set(player, 2, 1_000L);

        assertEquals(new WarningSavedData.WarningSnapshot(1, 121_000L),
                data.increment(player, 121_000L, 60_000L));
    }
}
