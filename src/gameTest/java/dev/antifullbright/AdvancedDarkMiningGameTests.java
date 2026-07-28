package dev.antifullbright;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic integration coverage for time, warning, placement, and reset behavior. */
@GameTestHolder(AntiFullbright.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdvancedDarkMiningGameTests {
    private static final BlockPos ROOM_TARGET = new BlockPos(2, 1, 0);

    private AdvancedDarkMiningGameTests() {}

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void heldTorchGraceExpiresAndThenCounts(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
            BlockPos target = helper.absolutePos(ROOM_TARGET);

            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            if (!containsCount(status(manager, helper, player), 0)) {
                helper.fail("Held-torch grace precondition did not remain uncounted");
                return;
            }

            DarkMiningState state = state(manager, player.getUUID());
            state.graceUntil = System.currentTimeMillis() - 1L;
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            if (!containsCount(status(manager, helper, player), 1)) {
                helper.fail("Expired held-torch grace did not allow the next qualifying break to count");
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void inactivityResetsBeforeTheNextCount(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(ROOM_TARGET);
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());

            DarkMiningState state = state(manager, player.getUUID());
            state.lastQualifyingBreakAt = System.currentTimeMillis()
                    - AntiFullbrightConfig.INACTIVITY_RESET_SECONDS.getAsInt() * 1_000L - 1L;
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());

            if (!containsCount(status(manager, helper, player), 1)) {
                helper.fail("Inactive session was continued instead of being replaced by a fresh one");
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void expiredPlayerPlacedBlockRecordIsCounted(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(ROOM_TARGET);
            manager.onPlace(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            expirePlacementRecords(manager, player.getUUID());
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());

            if (!containsCount(status(manager, helper, player), 1)) {
                helper.fail("Expired player-placement record still suppressed the qualifying break");
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void thresholdIssuesFirstWarningAndResetsSession(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            triggerWarning(manager, helper, player);
            WarningSavedData.WarningSnapshot warning = manager.warningStatus(
                    helper.getLevel().getServer(), player.getUUID());
            if (warning.count() != 1 || !containsCount(status(manager, helper, player), 0)) {
                helper.fail("First threshold crossing did not issue warning level one and reset the session: " + warning);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void repeatedThresholdProgressesToSecondWarning(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            triggerWarning(manager, helper, player);
            triggerWarning(manager, helper, player);
            WarningSavedData.WarningSnapshot warning = manager.warningStatus(
                    helper.getLevel().getServer(), player.getUUID());
            if (warning.count() != 2 || !containsCount(status(manager, helper, player), 0)) {
                helper.fail("Repeated threshold crossings did not progress to warning level two: " + warning);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void underwaterPlayerIsExcluded(GameTestHelper helper) {
        assertExcluded(helper, testPlayer(helper, true), "underwater", "目の位置が水中", "Underwater player");
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void fakePlayerIsExcluded(GameTestHelper helper) {
        assertExcluded(helper, FakePlayerFactory.getMinecraft(helper.getLevel()),
                "fake player", "FakePlayer", "FakePlayer");
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void commandTreeContainsAllAdministratorOperations(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        var darkMining = root.getChild("darkmining");
        if (darkMining == null) {
            helper.fail("Missing /darkmining root command");
            return;
        }
        Set<String> expected = Set.of("status", "reset", "setwarning", "reload", "debug");
        Set<String> actual = darkMining.getChildren().stream().map(node -> node.getName()).collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected)
                || darkMining.getChild("status").getChild("player") == null
                || darkMining.getChild("reset").getChild("player") == null
                || darkMining.getChild("debug").getChild("player") == null
                || darkMining.getChild("setwarning").getChild("player") == null
                || darkMining.getChild("setwarning").getChild("player").getChild("count") == null) {
            helper.fail("Unexpected /darkmining command tree: " + actual);
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void explicitResetClearsActiveSession(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            manager.onBreak(player, helper.getLevel(), helper.absolutePos(ROOM_TARGET), Blocks.STONE.defaultBlockState());
            manager.resetSession(player.getUUID());
            if (!containsCount(status(manager, helper, player), 0)) {
                helper.fail("Explicit session reset left counted blocks active");
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void logoutClearsSessionAndPlacementRecord(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(ROOM_TARGET);
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            manager.onPlace(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            manager.logout(player.getUUID());
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            if (!containsCount(status(manager, helper, player), 1)) {
                helper.fail("Logout did not clear both session and player-placement state");
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "dark_room", setupTicks = 40, timeoutTicks = 40)
    public static void chunkUnloadClearsPlacementRecord(GameTestHelper helper) {
        DarkMiningManager manager = new DarkMiningManager();
        ServerPlayer player = testPlayer(helper, false);
        try {
            positionInsideRoom(helper, player);
            BlockPos target = helper.absolutePos(ROOM_TARGET);
            manager.onPlace(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            manager.unloadChunk(helper.getLevel().dimension(), new ChunkPos(target));
            manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
            if (!containsCount(status(manager, helper, player), 1)) {
                helper.fail("Chunk unload did not clear the placement record for that chunk");
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            player.discard();
        }
    }

    private static void triggerWarning(
            DarkMiningManager manager, GameTestHelper helper, ServerPlayer player) {
        BlockPos target = helper.absolutePos(ROOM_TARGET);
        manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
        DarkMiningState state = state(manager, player.getUUID());
        long now = System.currentTimeMillis();
        state.countedBlocks = Math.max(0, AntiFullbrightConfig.MINIMUM_BLOCKS.getAsInt() - 1);
        state.countedStartedAt = now - AntiFullbrightConfig.CONTINUOUS_MINING_SECONDS.getAsInt() * 1_000L - 1_000L;
        state.lastQualifyingBreakAt = now;
        manager.onBreak(player, helper.getLevel(), target, Blocks.STONE.defaultBlockState());
    }

    private static void assertExcluded(
            GameTestHelper helper, ServerPlayer player, String englishReason, String japaneseReason, String label) {
        DarkMiningManager manager = new DarkMiningManager();
        boolean discard = !(player instanceof net.neoforged.neoforge.common.util.FakePlayer);
        try {
            positionInsideRoom(helper, player);
            manager.onBreak(player, helper.getLevel(), helper.absolutePos(ROOM_TARGET), Blocks.STONE.defaultBlockState());
            List<String> status = status(manager, helper, player);
            List<String> debug = debug(manager, helper, player);
            boolean reason = debug.stream().anyMatch(line -> line.contains(englishReason) || line.contains(japaneseReason));
            if (!containsCount(status, 0) || !reason) {
                helper.fail(label + " was not excluded as expected: status=" + status + ", debug=" + debug);
                return;
            }
            helper.succeed();
        } finally {
            manager.close();
            if (discard) player.discard();
        }
    }

    private static ServerPlayer testPlayer(GameTestHelper helper, boolean underwater) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "af-advanced-gametest"),
                ClientInformation.createDefault()) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
            @Override public boolean hasPermissions(int level) { return false; }
            @Override public FluidType getEyeInFluidType() {
                return underwater ? NeoForgeMod.WATER_TYPE.value() : super.getEyeInFluidType();
            }
            @Override public void sendSystemMessage(Component message) {}
            @Override public void displayClientMessage(Component message, boolean actionBar) {}
        };
    }

    private static void positionInsideRoom(GameTestHelper helper, ServerPlayer player) {
        BlockPos standing = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D);
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

    @SuppressWarnings("unchecked")
    private static DarkMiningState state(DarkMiningManager manager, UUID uuid) {
        try {
            Field field = DarkMiningManager.class.getDeclaredField("states");
            field.setAccessible(true);
            Map<UUID, DarkMiningState> states = (Map<UUID, DarkMiningState>) field.get(manager);
            DarkMiningState state = states.get(uuid);
            if (state == null) throw new IllegalStateException("No active state for " + uuid);
            return state;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect dark-mining state", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void expirePlacementRecords(DarkMiningManager manager, UUID uuid) {
        try {
            Field field = DarkMiningManager.class.getDeclaredField("placedBlocks");
            field.setAccessible(true);
            Map<UUID, LinkedHashMap<Object, Long>> placed =
                    (Map<UUID, LinkedHashMap<Object, Long>>) field.get(manager);
            LinkedHashMap<Object, Long> entries = placed.get(uuid);
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("No placement record to expire");
            }
            long expired = System.currentTimeMillis()
                    - AntiFullbrightConfig.PLACED_BLOCK_TRACKING_EXPIRATION_MINUTES.getAsInt() * 60_000L - 1L;
            entries.replaceAll((key, value) -> expired);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not expire placement records", exception);
        }
    }
}
