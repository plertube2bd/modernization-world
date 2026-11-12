package com.example.jail.test;

import com.example.jail.Jail;
import com.example.jail.MiniGames;
import com.example.jail.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Properties;

/** On a dedicated server: jailed, in the yard and in a room, leave and join again; the floor is still under the player. */
public final class ServerRejoinTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Properties properties = new Properties();
        properties.setProperty("allow-flight", "false");
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            String name;
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                name = context.computeOnClient(client -> client.player.getGameProfile().name());
                server.runCommand("op " + name);
                server.runCommand("감옥 " + name + " 10");
                context.waitTicks(80);
                report(context, server, name, "joined, jailed, in the yard");
            }
            context.waitTicks(40);
            try (TestDedicatedServerConnection connection = server.connect()) {
                context.waitTicks(80);
                connection.waitForChunksRender();
                context.takeScreenshot(TestScreenshotOptions.of("server_rejoin_yard"));
                check(report(context, server, name, "rejoined, in the yard"));
                server.runOnServer(s -> Rooms.enter(player(s, name), Rooms.room(MiniGames.Game.PARKOUR, 0)));
                context.waitTicks(80);
                check(report(context, server, name, "in parkour room 1"));
            }
            context.waitTicks(40);
            try (TestDedicatedServerConnection connection = server.connect()) {
                context.waitTicks(80);
                connection.waitForChunksRender();
                context.takeScreenshot(TestScreenshotOptions.of("server_rejoin_room"));
                check(report(context, server, name, "rejoined, in the room"));
            }
        }
    }

    private static void check(String line) {
        if (line.contains("floor AIR")) {
            throw new AssertionError("the floor is gone: " + line);
        }
    }

    /** What is under the player's feet, as the server and the client see it. */
    private static String report(ClientGameTestContext context, TestDedicatedServerContext server, String name, String when) {
        String onServer = server.computeOnServer(s -> {
            ServerPlayer p = player(s, name);
            if (p == null) {
                return "not on the server";
            }
            BlockPos below = p.blockPosition().below();
            return p.level().dimension().toString() + " " + p.position() + " jailed " + Jail.isJailed(p)
                    + " server floor " + name(p.level().getBlockState(below).getBlock().toString());
        });
        String onClient = context.computeOnClient(c -> c.player == null ? "no client player"
                : "client " + c.player.position() + " floor " + name(c.level.getBlockState(c.player.blockPosition().below()).getBlock().toString()));
        String line = when + ": " + onServer + " / " + onClient;
        System.out.println("[jail-rejoin] " + line);
        return line;
    }

    private static String name(String block) {
        return block.equals("Block{minecraft:air}") ? "AIR" : block;
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name);
    }
}
