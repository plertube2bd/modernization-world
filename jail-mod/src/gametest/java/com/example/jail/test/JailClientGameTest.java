package com.example.jail.test;

import com.example.jail.Jail;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/** A singleplayer world: jail the player with /감옥, look round the jail, let them out with /석방. */
public final class JailClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            String name = context.computeOnClient(client -> client.player.getGameProfile().name());
            world.getServer().runCommand("감옥 " + name + " 2");
            context.waitTicks(40);
            boolean jailed = world.getServer().computeOnServer(server -> Jail.isJailed(server.getPlayerList().getPlayerByName(name))
                    && server.getPlayerList().getPlayerByName(name).level().dimension() == Jail.DIMENSION);
            if (!jailed) {
                throw new AssertionError("/감옥 did not put " + name + " in the jail dimension");
            }
            shot(context, world, name, "jail_yard", "0.5 64 8 facing 0.5 67 -12");
            shot(context, world, name, "jail_coal", "-11.5 64 -2 facing -19 66.5 -2");
            shot(context, world, name, "jail_parkour", "0.5 64 -6 facing 0.5 68 -17");
            shot(context, world, name, "jail_range", "7.5 64 0.5 facing 19 66.5 0.5");
            shot(context, world, name, "jail_pond_quiz", "0.5 67 4 facing 3 63 15");

            world.getServer().runCommand("석방 " + name);
            context.waitTicks(20);
            boolean free = world.getServer().computeOnServer(server -> !Jail.isJailed(server.getPlayerList().getPlayerByName(name))
                    && server.getPlayerList().getPlayerByName(name).level().dimension() != Jail.DIMENSION);
            if (!free) {
                throw new AssertionError("/석방 did not let " + name + " out");
            }
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("jail_released"));
        }
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, String file, String where) {
        world.getServer().runCommand("execute in jail:jail run tp " + name + " " + where);
        context.waitTicks(15);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(TestScreenshotOptions.of(file));
    }
}
