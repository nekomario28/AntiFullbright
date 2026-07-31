package dev.antifullbright;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** In-game regression coverage for datapack tags, commands, and minimal dark-mining behavior. */
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

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void survivalBreakInCompleteDarknessStartsCountedSession(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = nonExcludedPlayer(helper);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            assertFullyDark(helper, player, target);
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> status = status(manager, helper, player);
            if (!containsCount(status, 1)) {
                helper.fail("A qualifying non-excluded break did not start a one-block counted session: "
                        + debug(manager, helper, player));
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void creativePlayerIsExcludedFromDarkMiningCount(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> status = status(manager, helper, player);
            List<String> debug = debug(manager, helper, player);
            boolean creativeReason = debug.stream().anyMatch(line ->
                    line.contains("creative mode") || line.contains("クリエイティブモード"));
            if (!containsCount(status, 0) || !creativeReason) {
                helper.fail("Creative player was not excluded as expected: status=" + status + ", debug=" + debug);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void playerPlacedBlockIsExcludedOnce(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = nonExcludedPlayer(helper);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            assertFullyDark(helper, player, target);
            manager.onPlace(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> firstStatus = status(manager, helper, player);
            if (!containsCount(firstStatus, 0)) {
                helper.fail("First break of a player-placed block was counted: " + firstStatus);
                return;
            }
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> secondStatus = status(manager, helper, player);
            if (!containsCount(secondStatus, 1)) {
                helper.fail("Placed-block exclusion was not consumed exactly once: " + secondStatus);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void placingTorchResetsActiveSession(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = nonExcludedPlayer(helper);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            assertFullyDark(helper, player, target);
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> started = status(manager, helper, player);
            if (!containsCount(started, 1)) {
                helper.fail("Could not establish the precondition session before torch placement: " + started);
                return;
            }
            BlockPos torchPosition = helper.absolutePos(new BlockPos(2, 1, 1));
            manager.onPlace(player, helper.getLevel(), torchPosition, Blocks.TORCH.defaultBlockState());
            List<String> reset = status(manager, helper, player);
            if (!containsCount(reset, 0)) {
                helper.fail("Tagged light-source placement did not reset the active session: " + reset);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void heldTorchStartsGraceWithoutCounting(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = nonExcludedPlayer(helper);
        try {
            positionInsideRoom(helper, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            assertFullyDark(helper, player, target);
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> status = status(manager, helper, player);
            List<String> debug = debug(manager, helper, player);
            if (!containsCount(status, 0) || !hasPositiveGrace(debug)) {
                helper.fail("Held torch did not start an uncounted grace session: status="
                        + status + ", debug=" + debug);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void nightVisionPlayerIsExcludedFromDarkMiningCount(GameTestHelper helper) {
        assertExcluded(helper, nightVisionPlayer(helper), "night vision effect", "暗視効果", "Night Vision player");
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void operatorIsExcludedFromDarkMiningCount(GameTestHelper helper) {
        assertExcluded(helper, operatorPlayer(helper), "server operator", "サーバーOP", "Operator");
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void spectatorIsExcludedFromDarkMiningCount(GameTestHelper helper) {
        assertExcluded(helper, spectatorPlayer(helper), "spectator mode", "スペクテイターモード", "Spectator");
    }

    private static void assertExcluded(
            GameTestHelper helper, ServerPlayer player, String englishReason, String japaneseReason, String label) {
        DarkMiningManager manager = new DarkMiningManager();
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 0));
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            List<String> status = status(manager, helper, player);
            List<String> debug = debug(manager, helper, player);
            boolean expectedReason = debug.stream().anyMatch(line ->
                    line.contains(englishReason) || line.contains(japaneseReason));
            if (!containsCount(status, 0) || !expectedReason) {
                helper.fail(label + " was not excluded as expected: status=" + status + ", debug=" + debug);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    private static ServerPlayer nonExcludedPlayer(GameTestHelper helper) {
        return testPlayer(helper, false, false, false);
    }

    private static ServerPlayer nightVisionPlayer(GameTestHelper helper) {
        return testPlayer(helper, true, false, false);
    }

    private static ServerPlayer operatorPlayer(GameTestHelper helper) {
        return testPlayer(helper, false, true, false);
    }

    private static ServerPlayer spectatorPlayer(GameTestHelper helper) {
        return testPlayer(helper, false, false, true);
    }

    private static ServerPlayer testPlayer(
            GameTestHelper helper, boolean nightVision, boolean operator, boolean spectator) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "af-gametest"),
                ClientInformation.createDefault()) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return spectator; }
            @Override public boolean hasPermissions(int level) { return operator && level >= 2; }

            @Override
            public boolean hasEffect(Holder<MobEffect> effect) {
                return (nightVision && effect.equals(MobEffects.NIGHT_VISION)) || super.hasEffect(effect);
            }
        };
    }

    private static void positionInsideRoom(GameTestHelper helper, ServerPlayer player) {
        BlockPos standing = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D);
    }

    private static void assertFullyDark(GameTestHelper helper, ServerPlayer player, BlockPos target) {
        var level = helper.getLevel();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        int eyeBlockLight = level.getBrightness(LightLayer.BLOCK, eyes);
        int eyeSkyLight = level.getBrightness(LightLayer.SKY, eyes);
        int targetBlockLight = level.getBrightness(LightLayer.BLOCK, target);
        int targetSkyLight = level.getBrightness(LightLayer.SKY, target);
        if (eyeBlockLight != 0 || eyeSkyLight != 0 || targetBlockLight != 0 || targetSkyLight != 0) {
            helper.fail("Dark-room fixture was not fully dark: eyes=" + eyeBlockLight + "/" + eyeSkyLight
                    + ", target=" + targetBlockLight + "/" + targetSkyLight);
        }
    }

    private static List<String> status(DarkMiningManager manager, GameTestHelper helper, ServerPlayer player) {
        return manager.statusLines(helper.getLevel().getServer(), player).stream()
                .map(component -> component.getString()).toList();
    }

    private static List<String> debug(DarkMiningManager manager, GameTestHelper helper, ServerPlayer player) {
        return manager.debugLines(helper.getLevel().getServer(), player).stream()
                .map(component -> component.getString()).toList();
    }

    private static boolean containsCount(List<String> status, int count) {
        return status.stream().anyMatch(line ->
                line.contains("countedBlocks=" + count) || line.contains("対象破壊数=" + count));
    }

    private static boolean hasPositiveGrace(List<String> debug) {
        for (String line : debug) {
            if (!line.contains("grace remaining seconds=") && !line.contains("光源所持猶予の残り秒数=")) {
                continue;
            }
            int separator = line.lastIndexOf('=');
            if (separator >= 0) {
                try {
                    return Long.parseLong(line.substring(separator + 1).trim()) > 0;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }
}
