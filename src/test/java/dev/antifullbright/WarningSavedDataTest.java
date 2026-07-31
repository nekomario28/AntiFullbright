package dev.antifullbright;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
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
    void warningStateSurvivesCompressedDiskRoundTrip(@TempDir Path directory) throws IOException {
        UUID player = UUID.randomUUID();
        WarningSavedData original = new WarningSavedData();
        original.set(player, 4, 98_765L);
        Path file = directory.resolve("antifullbright_warnings.dat");

        NbtIo.writeCompressed(original.save(new CompoundTag(), null), file);
        CompoundTag fromDisk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        WarningSavedData restarted = WarningSavedData.load(fromDisk, null);

        assertEquals(new WarningSavedData.WarningSnapshot(4, 98_765L),
                restarted.get(player, 98_765L, 60_000L));
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
