package dev.antifullbright;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Capacity regression coverage for bounded player-placement tracking. */
@GameTestHolder(AntiFullbright.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacementCapacityGameTests {
    private PlacementCapacityGameTests() {}

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 200)
    public static void oldestPlacementIsEvictedAtConfiguredMaximum(GameTestHelper helper) {
        int maximum = AntiFullbrightConfig.PLACED_BLOCK_TRACKING_MAXIMUM_ENTRIES_PER_PLAYER.getAsInt();
        if (maximum < 1 || maximum > 10_000) {
            helper.fail("Unexpected placement tracking maximum for bounded test: " + maximum);
            return;
        }

        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "af-capacity-gametest"),
                ClientInformation.createDefault()) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
            @Override public boolean hasPermissions(int level) { return false; }
        };

        try {
            BlockPos standing = helper.absolutePos(new BlockPos(2, 1, 2));
            player.setPos(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D);
            BlockPos oldest = helper.absolutePos(new BlockPos(2, 1, 0));
            manager.onPlace(player, helper.getLevel(), oldest, Blocks.STONE.defaultBlockState());

            int chunkBaseX = oldest.getX() & ~15;
            int chunkBaseZ = oldest.getZ() & ~15;
            for (int index = 0; index < maximum; index++) {
                int x = chunkBaseX + (index & 15);
                int z = chunkBaseZ + ((index >>> 4) & 15);
                int y = 1_000 + (index >>> 8);
                manager.onPlace(player, helper.getLevel(), new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
            }

            manager.onBreak(player, helper.getLevel(), oldest, Blocks.STONE.defaultBlockState());
            boolean counted = manager.statusLines(helper.getLevel().getServer(), player).stream()
                    .map(component -> component.getString())
                    .anyMatch(line -> line.contains("countedBlocks=1") || line.contains("対象破壊数=1"));
            if (!counted) {
                helper.fail("Oldest placement record was not evicted at maximum=" + maximum);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }
}
