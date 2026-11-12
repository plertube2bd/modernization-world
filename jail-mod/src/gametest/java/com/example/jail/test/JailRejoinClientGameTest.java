package com.example.jail.test;

import com.example.jail.Jail;
import com.example.jail.JailBuilder;
import com.example.jail.MiniGames;
import com.example.jail.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Leaving the game in jail and coming back: the floor under the yard and the rooms is still there, on both sides. */
public final class JailRejoinClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        String name;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            world.getConnection().waitForChunksRender();
            name = context.computeOnClient(client -> client.player.getGameProfile().name());
            world.getServer().runCommand("감옥 " + name + " 10");
            context.waitTicks(60);
            report(context, world, name, "first, in the yard");
            world.getServer().runOnServer(server -> Rooms.enter(player(server, name), Rooms.room(MiniGames.Game.COAL, 0)));
            context.waitTicks(60);
            report(context, world, name, "first, in coal room 1");
        }
        try (TestSingleplayerContext world = save.open()) {
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("jail_rejoin_room"));
            String room = report(context, world, name, "after rejoining, in the room");
            world.getServer().runOnServer(server -> Jail.toYard(player(server, name)));
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("jail_rejoin_yard"));
            String yard = report(context, world, name, "after rejoining, in the yard");
            if (room.contains("AIR") || yard.contains("AIR")) {
                throw new AssertionError("the floor is gone after rejoining: " + room + " / " + yard);
            }
        }
    }

    /** What is under the player's feet, as the server and the client see it. */
    private static String report(ClientGameTestContext context, TestSingleplayerContext world, String name, String when) {
        String server = world.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s, name);
            BlockPos below = p.blockPosition().below();
            return p.level().dimension() + " " + p.position() + " server floor " + p.level().getBlockState(below).getBlock()
                    + " built " + com.example.jail.JailData.get(s).built();
        });
        String client = context.computeOnClient(c -> "client floor "
                + c.level.getBlockState(c.player.blockPosition().below()).getBlock() + " in " + c.level.dimension());
        String line = when + ": " + server + " / " + client;
        System.out.println("[jail-rejoin] " + line);
        return line.replace("minecraft:air}", "AIR}");
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name);
    }
}
