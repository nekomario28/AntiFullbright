package dev.antifullbright;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game regression coverage for datapack tags, commands, and a minimal dark-mining session. */
@GameTestHolder(AntiFullbright.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerContractGameTests {
    private ServerContractGameTests() {}

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void countedBlockTagContainsStone(GameTestHelper helper) {
        if (!Blocks.STONE.defaultBlockState().is(ModTags.DARK_MINING_COUNTED_BLOCKS)) {
            helper.fail("minecraft:stone is missing from antifullbright:dark_mining_counted_blocks");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void lightSourceTagContainsTorch(GameTestHelper helper) {
        if (!Items.TORCH.getDefaultInstance().is(ModTags.DARK_MINING_LIGHT_SOURCES)) {
            helper.fail("minecraft:torch is missing from antifullbright:dark_mining_light_sources");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void darkMiningCommandIsRegistered(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        if (root.getChild("darkmining") == null) {
            helper.fail("Expected /darkmining to be registered on the GameTest server");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(
            templateNamespace = AntiFullbright.MOD_ID,
            template = "dark_room",
            setupTicks = 40,
            timeoutTicks = 40)
    public static void survivalBreakInCompleteDarknessStartsCountedSession(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            if (player.isCreative() || player.isSpectator()) {
                helper.fail("Mock ServerPlayer did not enter survival mode");
                return;
            }

            BlockPos standing = helper.absolutePos(new BlockPos(2, 1, 2));
            player.setPos(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D);

            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            var level = helper.getLevel();
            BlockPos eyes = BlockPos.containing(player.getEyePosition());
            int eyeBlockLight = level.getBrightness(LightLayer.BLOCK, eyes);
            int eyeSkyLight = level.getBrightness(LightLayer.SKY, eyes);
            int targetBlockLight = level.getBrightness(LightLayer.BLOCK, target);
            int targetSkyLight = level.getBrightness(LightLayer.SKY, target);
            if (eyeBlockLight != 0 || eyeSkyLight != 0 || targetBlockLight != 0 || targetSkyLight != 0) {
                helper.fail("Dark-room fixture was not fully dark: eyes="
                        + eyeBlockLight + "/" + eyeSkyLight
                        + ", target=" + targetBlockLight + "/" + targetSkyLight);
                return;
            }

            manager.onBreak(player, level, target, Blocks.STONE.defaultBlockState());
            boolean counted = manager.statusLines(level.getServer(), player).stream()
                    .map(component -> component.getString())
                    .anyMatch(line -> line.contains("countedBlocks=1") || line.contains("対象破壊数=1"));
            if (!counted) {
                helper.fail("A qualifying survival break did not start a one-block counted session: "
                        + manager.debugLines(level.getServer(), player));
                return;
            }

            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }
}
