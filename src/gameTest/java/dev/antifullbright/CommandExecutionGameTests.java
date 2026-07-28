package dev.antifullbright;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executes the registered administrator commands through the real Brigadier dispatcher. */
@GameTestHolder(AntiFullbright.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandExecutionGameTests {
    private CommandExecutionGameTests() {}

    @GameTest(templateNamespace = AntiFullbright.MOD_ID, template = "empty", timeoutTicks = 40)
    public static void administratorCommandsMutateAndReportWarningState(GameTestHelper helper) throws Exception {
        List<String> messages = new ArrayList<>();
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "af-command-gametest"),
                ClientInformation.createDefault()) {
            @Override public void sendSystemMessage(Component message) {
                messages.add(message.getString());
            }
        };

        try {
            var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
            var source = player.createCommandSourceStack().withPermission(4);

            int setResult = dispatcher.execute("darkmining setwarning @s 2", source);
            int statusResult = dispatcher.execute("darkmining status @s", source);
            int debugResult = dispatcher.execute("darkmining debug @s", source);
            int resetResult = dispatcher.execute("darkmining reset @s", source);
            int finalStatusResult = dispatcher.execute("darkmining status @s", source);

            boolean setMessage = messages.stream().anyMatch(line ->
                    line.contains("warning level to 2") || line.contains("警告レベルを 2"));
            boolean levelTwo = messages.stream().anyMatch(line ->
                    line.contains("warningLevel=2") || line.contains("警告レベル=2"));
            boolean debugMessage = messages.stream().anyMatch(line ->
                    line.contains("Dark-mining debug:") || line.contains("暗所採掘デバッグ:"));
            boolean resetMessage = messages.stream().anyMatch(line ->
                    line.contains("Reset dark-mining warnings") || line.contains("暗所採掘警告とセッションをリセット"));
            boolean levelZero = messages.stream().anyMatch(line ->
                    line.contains("warningLevel=0") || line.contains("警告レベル=0"));

            if (setResult != 1 || statusResult != 1 || debugResult != 1 || resetResult != 1 || finalStatusResult != 1
                    || !setMessage || !levelTwo || !debugMessage || !resetMessage || !levelZero) {
                helper.fail("Administrator command sequence did not produce the expected state and output: " + messages);
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
