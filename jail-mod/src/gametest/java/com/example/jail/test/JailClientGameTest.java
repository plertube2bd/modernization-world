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

            // A real right-click on the fishing NPC opens its room picker.
            shot(context, world, name, "jail_npc_close", "5.5 64 -7 facing 5.5 65.6 -9.5");
            net.minecraft.world.phys.Vec3 npcAt = world.getServer().computeOnServer(server -> {
                var level = Jail.level(server);
                var found = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Mannequin.class,
                        new net.minecraft.world.phys.AABB(-16, 60, -16, 16, 72, 16), m -> "fish".equals(com.example.jail.JailBuilder.npcKey(m)));
                return found.get(0).position().add(0, 1.2, 0);
            });
            context.runOnClient(client -> client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, npcAt));
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(10);
            boolean menu = world.getServer().computeOnServer(server -> player(server, name).containerMenu instanceof RoomMenu);
            if (!menu) {
                throw new AssertionError("right-clicking the fishing NPC opened no room picker");
            }
            context.takeScreenshot(TestScreenshotOptions.of("jail_room_menu"));
            world.getServer().runOnServer(server -> player(server, name).containerMenu.clicked(RoomMenu.SLOTS[0], 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, player(server, name)));
            context.waitTicks(40);
            boolean inRoom = world.getServer().computeOnServer(server -> Rooms.room(MiniGames.Game.FISH, 0).equals(Rooms.of(player(server, name))));
            if (!inRoom) {
                throw new AssertionError("clicking room 1 did not go to fishing room 1");
            }
            // A real right-click on the room's NPC goes back to the yard.
            Rooms.Room fish = Rooms.room(MiniGames.Game.FISH, 0);
            int fx = fish.origin().getX(), fz = fish.origin().getZ();
            world.getServer().runCommand("execute in jail:jail run tp " + name + " " + (fx - 27.5) + " 64 " + (fz - 1.5));
            context.waitTicks(40);
            net.minecraft.world.phys.Vec3 exitAt = world.getServer().computeOnServer(server -> Jail.level(server)
                    .getEntitiesOfClass(net.minecraft.world.entity.decoration.Mannequin.class,
                            new net.minecraft.world.phys.AABB(fx - 32, 60, fz - 10, fx - 20, 72, fz + 10), m -> "exit".equals(com.example.jail.JailBuilder.npcKey(m)))
                    .get(0).position().add(0, 1.2, 0));
            context.runOnClient(client -> client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, exitAt));
            context.waitTicks(5);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("jail_exit_npc"));
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(10);
            boolean yard = world.getServer().computeOnServer(server -> com.example.jail.JailBuilder.inHub(player(server, name).position()));
            if (!yard) {
                throw new AssertionError("right-clicking the room's NPC did not go back to the yard");
            }

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
