package com.example.jail.test;

import com.example.jail.Jail;
import com.example.jail.MiniGames;
import com.example.jail.RoomMenu;
import com.example.jail.Rooms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A singleplayer world: jail the player with /감옥, look at the yard's NPCs, the room picker, each minigame's room and
 * the prison uniform, then let them out with /석방.
 */
public final class JailClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            String name = context.computeOnClient(client -> client.player.getGameProfile().name());
            world.getServer().runCommand("감옥 " + name + " 10");
            context.waitTicks(100);
            boolean jailed = world.getServer().computeOnServer(server -> Jail.isJailed(player(server, name))
                    && player(server, name).level().dimension() == Jail.DIMENSION);
            if (!jailed) {
                throw new AssertionError("/감옥 did not put " + name + " in the jail dimension");
            }
            shot(context, world, name, "jail_yard", "0.5 64 6 facing 0.5 65 -9");

            world.getServer().runOnServer(server -> RoomMenu.open(player(server, name), MiniGames.Game.PARKOUR));
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("jail_room_menu"));
            world.getServer().runOnServer(server -> player(server, name).closeContainer());

            for (MiniGames.Game game : MiniGames.Game.values()) {
                world.getServer().runOnServer(server -> Rooms.enter(player(server, name), Rooms.room(game, 0)));
                context.waitTicks(60);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot(TestScreenshotOptions.of("jail_room_" + game.key));
            }
            // The parkour course from the side.
            Rooms.Room parkour = Rooms.room(MiniGames.Game.PARKOUR, 0);
            int px = parkour.origin().getX(), pz = parkour.origin().getZ();
            shot(context, world, name, "jail_parkour_course", (px) + " 64 " + (pz + 15) + " facing " + px + " 65 " + pz);

            world.getServer().runOnServer(server -> Jail.toYard(player(server, name)));
            context.waitTicks(20);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("jail_uniform"));
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

            world.getServer().runCommand("석방 " + name);
            context.waitTicks(20);
            boolean free = world.getServer().computeOnServer(server -> !Jail.isJailed(player(server, name))
                    && player(server, name).level().dimension() != Jail.DIMENSION);
            if (!free) {
                throw new AssertionError("/석방 did not let " + name + " out");
            }
        }
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name);
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, String file, String where) {
        world.getServer().runCommand("execute in jail:jail run tp " + name + " " + where);
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(TestScreenshotOptions.of(file));
    }
}
