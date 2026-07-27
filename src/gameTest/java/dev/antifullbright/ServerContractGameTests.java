package dev.antifullbright;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game regression coverage for datapack tags and server command registration. */
@GameTestHolder(AntiFullbright.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerContractGameTests {
    private ServerContractGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 20)
    public static void countedBlockTagContainsStone(GameTestHelper helper) {
        if (!Blocks.STONE.defaultBlockState().is(ModTags.DARK_MINING_COUNTED_BLOCKS)) {
            helper.fail("minecraft:stone is missing from antifullbright:dark_mining_counted_blocks");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 20)
    public static void lightSourceTagContainsTorch(GameTestHelper helper) {
        if (!Items.TORCH.getDefaultInstance().is(ModTags.DARK_MINING_LIGHT_SOURCES)) {
            helper.fail("minecraft:torch is missing from antifullbright:dark_mining_light_sources");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 20)
    public static void darkMiningCommandIsRegistered(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        if (root.getChild("darkmining") == null) {
            helper.fail("Expected /darkmining to be registered on the GameTest server");
            return;
        }
        helper.succeed();
    }
}
