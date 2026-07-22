package dev.antifullbright;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WarningSavedData extends SavedData {
    private static final String DATA_NAME = AntiFullbright.MOD_ID + "_warnings";
    private final Map<UUID, WarningEntry> entries = new HashMap<>();

    public static WarningSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(WarningSavedData::new, WarningSavedData::load), DATA_NAME);
    }

    public static WarningSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarningSavedData data = new WarningSavedData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (Tag raw : list) {
            CompoundTag player = (CompoundTag) raw;
            try {
                UUID uuid = UUID.fromString(player.getString("uuid"));
                data.entries.put(uuid, new WarningEntry(player.getInt("count"), player.getLong("lastWarning")));
            } catch (IllegalArgumentException ignored) {
                AntiFullbright.LOGGER.warn("Ignored malformed UUID in {} SavedData", DATA_NAME);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((uuid, entry) -> {
            CompoundTag player = new CompoundTag();
            player.putString("uuid", uuid.toString());
            player.putInt("count", entry.count);
            player.putLong("lastWarning", entry.lastWarningEpochMillis);
            list.add(player);
        });
        tag.put("players", list);
        return tag;
    }

    public WarningSnapshot get(UUID uuid, long now, long decayMillis) {
        WarningEntry entry = entries.get(uuid);
        if (entry == null) return new WarningSnapshot(0, 0);
        decay(entry, now, decayMillis);
        return new WarningSnapshot(entry.count, entry.lastWarningEpochMillis);
    }

    public WarningSnapshot increment(UUID uuid, long now, long decayMillis) {
        WarningEntry entry = entries.computeIfAbsent(uuid, ignored -> new WarningEntry(0, 0));
        decay(entry, now, decayMillis);
        entry.count++;
        entry.lastWarningEpochMillis = now;
        setDirty();
        return new WarningSnapshot(entry.count, entry.lastWarningEpochMillis);
    }

    public void set(UUID uuid, int count, long now) {
        entries.put(uuid, new WarningEntry(Math.max(0, count), count == 0 ? 0 : now));
        setDirty();
    }

    private void decay(WarningEntry entry, long now, long decayMillis) {
        if (entry.count <= 0 || entry.lastWarningEpochMillis <= 0 || decayMillis <= 0) return;
        long steps = (now - entry.lastWarningEpochMillis) / decayMillis;
        if (steps <= 0) return;
        int before = entry.count;
        entry.count = Math.max(0, entry.count - (int) Math.min(Integer.MAX_VALUE, steps));
        entry.lastWarningEpochMillis = entry.count == 0 ? 0 : entry.lastWarningEpochMillis + steps * decayMillis;
        if (entry.count != before) setDirty();
    }

    private static final class WarningEntry {
        int count;
        long lastWarningEpochMillis;

        WarningEntry(int count, long lastWarningEpochMillis) {
            this.count = count;
            this.lastWarningEpochMillis = lastWarningEpochMillis;
        }
    }

    public record WarningSnapshot(int count, long lastWarningEpochMillis) {}
}
