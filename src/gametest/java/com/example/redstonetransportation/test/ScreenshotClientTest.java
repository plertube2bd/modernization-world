package com.example.redstonetransportation.test;

import com.example.redstonetransportation.RedstoneTransportation;
import com.example.redstonetransportation.block.ConveyorBeltBlock;
import com.example.redstonetransportation.block.ConveyorShape;
import com.example.redstonetransportation.block.DriveRailBlock;
import com.example.redstonetransportation.block.StructureCoreBlock;
import com.example.redstonetransportation.client.CargoTramScreen;
import com.example.redstonetransportation.client.LandLevelerScreen;
import com.example.redstonetransportation.content.ModContent;
import com.example.redstonetransportation.integration.analogalu.ElectricPower;
import com.example.redstonetransportation.item.TrackLayerItem;
import com.example.redstonetransportation.machine.FactoryControllerBlock;
import com.example.redstonetransportation.machine.LandLevelerBlockEntity;
import com.example.redstonetransportation.machine.MachineBlock;
import com.example.redstonetransportation.machine.MachineBlockEntity;
import com.example.redstonetransportation.machine.MachineStatus;
import com.example.redstonetransportation.machine.MachineType;
import com.example.redstonetransportation.structure.MovingStructureEntity;
import com.example.redstonetransportation.structure.MovingStructures;
import com.example.redstonetransportation.structure.StructureAssembler;
import com.example.redstonetransportation.structure.StructureKind;
import com.example.redstonetransportation.structure.StructurePose;
import com.example.redstonetransportation.vehicle.CargoTramEntity;
import com.example.redstonetransportation.vehicle.ModEntityTypes;
import com.example.redstonetransportation.vehicle.RedstoneTramEntity;
import com.example.redstonetransportation.vehicle.TrainLinkManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Puts the new tram and blocks in a real client and saves screenshots to look at. */
public final class ScreenshotClientTest implements FabricClientGameTest {
    private static final int SURFACE = 80;
    private static final int RAIL_Y = SURFACE + 1;
    private static final int FURNACES_X = 62;
    private static final int FURNACES_Z = 36;
    private static final int STRUCTURES_X = 90;
    private static final int LOADING_FADE_TICKS = 60;
    private static final BlockPos ELEVATOR_CORE = new BlockPos(STRUCTURES_X - 4, RAIL_Y, 20);
    private static final BlockPos ELEVATOR_BOTTOM = ELEVATOR_CORE.offset(3, 0, 3);
    private static final BlockPos ELEVATOR_TOP = ELEVATOR_BOTTOM.above(8);
    private static final BlockPos SHIP_HELM = new BlockPos(STRUCTURES_X + 8, RAIL_Y, 34);
    private static final BlockPos AIRSHIP_HELM = new BlockPos(STRUCTURES_X + 10, RAIL_Y + 2, 20);
    /** In the middle of the near wall of a hull, on the pond's far side. */
    private static final BlockPos HULL_CORE = new BlockPos(STRUCTURES_X + 28, RAIL_Y, 30);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamerule advance_time false");
            world.getServer().runCommand("gamerule send_command_feedback false");
            world.getServer().runCommand("time set 6000");
            world.getServer().runOnServer(ScreenshotClientTest::buildShowcase);
            context.runOnClient(client -> client.gui.hud.toggle());
            context.waitTicks(20);

            // RT_SHOTS=guns,car takes only those scenes, for a quick look while working on them.
            String only = System.getenv("RT_SHOTS");
            if (only != null && !only.isBlank()) {
                for (String part : only.split(",")) {
                    switch (part.trim()) {
                        case "guns" -> testGuns(context, world);
                        case "car" -> testCar(context, world);
                        case "power" -> testPowerMarks(context, world);
                        case "electric" -> testElectricity(context, world);
                        case "music" -> testMusicDisc(context, world);
                        case "gear" -> testBossGear(context, world);
                        case "spear" -> testSpears(context, world);
                        case "none" -> { }
                        default -> throw new AssertionError("no screenshot scene called " + part);
                    }
                }
                return;
            }

            // The tram stands at x=7, the freight car at x=15, both on the rail at z=12.
            shot(context, world, "tram_side", 7.5, RAIL_Y + 1.0, 17.5, 7.5, RAIL_Y + 1.0, 12.5);
            shot(context, world, "tram_three_quarter", 2.5, RAIL_Y + 2.5, 16.5, 7.5, RAIL_Y + 0.8, 12.5);
            shot(context, world, "tram_end_on", 0.5, RAIL_Y + 0.8, 12.5, 7.5, RAIL_Y + 0.8, 12.5);
            shot(context, world, "cargo_side", 15.5, RAIL_Y + 1.0, 17.5, 15.5, RAIL_Y + 1.0, 12.5);
            closeups(context, world);
            shot(context, world, "machines", 25.5, RAIL_Y + 2.6, 13.5, 25.5, RAIL_Y, 8.5);
            shot(context, world, "rails", 12.5, RAIL_Y + 7.0, 22.5, 12.5, RAIL_Y, 26.5);
            shot(context, world, "tree_factory", 44.5, RAIL_Y + 5.0, 9.0, 44.5, RAIL_Y, 15.5);

            // Let the factory run so the machines and trees are seen working.
            world.getServer().runOnServer(ScreenshotClientTest::startFactory);
            context.waitTicks(200);
            shot(context, world, "tree_factory_running", 37.5, RAIL_Y + 7.0, 9.0, 44.5, RAIL_Y + 2, 15.5);

            world.getServer().runCommand("tp @p 7.5 " + RAIL_Y + " 12.5");
            world.getServer().runCommand("ride @p mount @e[type=redstone_transportation:redstone_tram,limit=1,sort=nearest]");
            context.waitTicks(10);
            context.runOnClient(client -> client.player.setYRot(0.0F));
            context.waitTicks(2);
            context.takeScreenshot(TestScreenshotOptions.of("cabin_rider_view"));
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(4);
            context.takeScreenshot(TestScreenshotOptions.of("cabin_third_person"));
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            world.getServer().runCommand("ride @p dismount");

            // A seated villager and zombie show where passengers end up in the cabin.
            world.getServer().runCommand("summon minecraft:zombie 60.5 " + RAIL_Y + " 12.5 {NoAI:1b,PersistenceRequired:1b}");
            context.waitTicks(2);
            world.getServer().runCommand("summon redstone_transportation:redstone_tram 60.5 " + (RAIL_Y + 0.1) + " 12.5");
            context.waitTicks(2);
            world.getServer().runCommand("ride @e[type=minecraft:zombie,limit=1,sort=nearest] mount @e[type=redstone_transportation:redstone_tram,x=60,y=" + RAIL_Y + ",z=12,distance=..3,limit=1]");
            shot(context, world, "cabin_passenger", 60.5, RAIL_Y + 1.2, 16.5, 60.5, RAIL_Y + 0.9, 12.5);

            testFurnaces(context, world);
            testMovingStructures(context, world);
            testAdvancements(context, world);
            testRealClicks(context, world);
            testTrackStyles(context, world);
            testCargoHold(context, world);
            testConveyors(context, world);
            testStation(context, world);
            testLandLeveler(context, world);
            testGuns(context, world);
            testPowerMarks(context, world);
            testCar(context, world);
            testMusicDisc(context, world);
            if (ElectricPower.isAvailable()) testElectricity(context, world);
            testCouplerClicks(context, world);
            testRecipeBook(context, world);

            // Send the tram off down the display track to see the running lights.
            world.getServer().runOnServer(ScreenshotClientTest::launchTram);
            shot(context, world, "tram_moving", 30.5, RAIL_Y + 2.0, 16.5, 40.5, RAIL_Y + 0.8, 12.5);
        }
    }

    /** Earned the ordinary way, through items and riding, then the tab itself as a Korean player sees it. */
    private static void testAdvancements(ClientGameTestContext context, TestSingleplayerContext world) {
        for (String item : List.of("drive_rail", "upgraded_furnace_3", "upgraded_blast_furnace_3", "upgraded_smoker_3")) {
            world.getServer().runCommand("give @p redstone_transportation:" + item);
        }
        context.waitTicks(10);
        for (String path : List.of("root", "rail/drive_rail", "rail/tram", "furnace/third", "furnace/all", "structure/first", "structure/elevator",
                "structure/ship", "structure/airship")) {
            boolean done = world.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                AdvancementHolder advancement = server.getAdvancements().get(Identifier.fromNamespaceAndPath(RedstoneTransportation.MOD_ID, path));
                return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
            });
            if (!done) throw new AssertionError("the player did not earn " + path);
        }

        switchLanguage(context, "ko_kr");
        context.setScreen(() -> new AdvancementsScreen(Minecraft.getInstance().getConnection().getAdvancements()));
        context.runOnClient(client -> {
            ClientAdvancements advancements = client.getConnection().getAdvancements();
            advancements.setSelectedTab(advancements.get(Identifier.fromNamespaceAndPath(RedstoneTransportation.MOD_ID, "root")), true);
        });
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("advancements"));
        context.setScreen(() -> null);
        switchLanguage(context, "en_us");
    }

    private static void switchLanguage(ClientGameTestContext context, String language) {
        CompletableFuture<?> reloaded = context.computeOnClient(client -> {
            client.getLanguageManager().setSelected(language);
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> reloaded.isDone());
        // The loading screen fades out over this, and it swallows clicks until it has gone.
        context.waitTicks(LOADING_FADE_TICKS);
    }

    /** All nine upgraded blocks in a row with the last three lit, and a real click on one. */
    private static void testFurnaces(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int i = 0; i < ModContent.UPGRADED_FURNACES.size(); i++) {
                BlockPos pos = new BlockPos(FURNACES_X + i, RAIL_Y, FURNACES_Z);
                level.setBlock(pos, ModContent.UPGRADED_FURNACES.get(i).defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
                if (level.getBlockEntity(pos) instanceof Container furnace && i % 3 == 2) {
                    furnace.setItem(0, new ItemStack(i < 6 ? Items.RAW_IRON : Items.BEEF, 64));
                    furnace.setItem(1, new ItemStack(Items.COAL_BLOCK, 8));
                }
            }
        });
        context.waitTicks(10);
        shot(context, world, "upgraded_furnaces", FURNACES_X + 4.5, RAIL_Y + 2.0, FURNACES_Z + 6.5, FURNACES_X + 4.5, RAIL_Y + 0.5, FURNACES_Z);
        BlockPos clicked = rightClick(context, world, FURNACES_X + 4.5, RAIL_Y, FURNACES_Z + 3.5, new BlockPos(FURNACES_X + 4, RAIL_Y, FURNACES_Z));
        if (!(context.computeOnClient(client -> client.player.containerMenu) instanceof BlastFurnaceMenu)) {
            throw new AssertionError("clicking the upgraded blast furnace at " + clicked + " opened nothing");
        }
        context.takeScreenshot(TestScreenshotOptions.of("upgraded_furnace_menu"));
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    /** A real player on real decks, where the client works out where the player stands. */
    private static void testMovingStructures(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(ScreenshotClientTest::buildStructures);
        context.waitTicks(10);
        shot(context, world, "structures_built", STRUCTURES_X + 10.5, RAIL_Y + 9.0, 8.5, STRUCTURES_X + 10.5, RAIL_Y + 3.0, 34.5);

        // Assembled through the block's own click, the way a player does it.
        world.getServer().runCommand("gamemode survival @p");
        assemble(context, world, ELEVATOR_CORE);
        assemble(context, world, SHIP_HELM);
        assemble(context, world, AIRSHIP_HELM);
        context.waitTicks(5);
        shot(context, world, "structures_assembled", STRUCTURES_X + 10.5, RAIL_Y + 9.0, 8.5, STRUCTURES_X + 10.5, RAIL_Y + 3.0, 34.5);

        // Standing on the car while it rises, then coming back down.
        double carTop = ELEVATOR_CORE.getY() + 1.0;
        world.getServer().runCommand("tp @p " + (ELEVATOR_CORE.getX() + 1.5) + " " + carTop + " " + (ELEVATOR_CORE.getZ() + 0.5));
        context.waitTicks(10);
        world.getServer().runOnServer(server -> MovingStructureEntity.callTo(server.overworld(), ELEVATOR_TOP));
        context.waitTicks(30);
        context.takeScreenshot(TestScreenshotOptions.of("elevator_riding"));
        context.waitTicks(40);
        double risen = context.computeOnClient(client -> client.player.getY() - carTop);
        if (risen < 7.5 || risen > 8.5) throw new AssertionError("the player did not rise with the elevator: " + risen);
        world.getServer().runOnServer(server -> MovingStructureEntity.callTo(server.overworld(), ELEVATOR_BOTTOM));
        context.waitTicks(70);
        double back = context.computeOnClient(client -> client.player.getY() - carTop);
        if (Math.abs(back) > 0.5) throw new AssertionError("the player fell off the elevator on the way down: " + back);

        // Standing on deck while someone else steers, with a turn in it.
        BlockPos deck = SHIP_HELM.offset(0, 1, -3);
        world.getServer().runCommand("tp @p " + (deck.getX() + 0.5) + " " + deck.getY() + " " + (deck.getZ() + 0.5));
        context.waitTicks(10);
        world.getServer().runOnServer(ScreenshotClientTest::putDriverOnShip);
        context.waitTicks(60);
        double above = context.computeOnClient(client -> client.player.getY() - deck.getY());
        if (Math.abs(above) > 0.3) throw new AssertionError("the player fell off the deck: " + above);
        double travelled = context.computeOnClient(client -> client.player.position().distanceTo(Vec3.atBottomCenterOf(deck)));
        if (travelled < 5.0) throw new AssertionError("the ship left the player behind: moved " + travelled);
        context.takeScreenshot(TestScreenshotOptions.of("ship_deck_riding"));
        world.getServer().runOnServer(ScreenshotClientTest::stopDriver);
        context.waitTicks(40);
        Vec3 ship = world.getServer().computeOnServer(server -> ship(server).position());
        shot(context, world, "ship_sailing", ship.x - 8.5, ship.y + 7.0, ship.z - 6.5, ship.x, ship.y, ship.z);
        testChestAboard(context, world);
        testHull(context, world);

        // Steering with the player's own keys from the seat.
        world.getServer().runCommand("tp @p " + (AIRSHIP_HELM.getX() + 0.5) + " " + (AIRSHIP_HELM.getY() + 1.0) + " " + (AIRSHIP_HELM.getZ() + 0.5));
        world.getServer().runCommand("execute as @p at @s run ride @s mount @e[type=redstone_transportation:moving_structure,limit=1,sort=nearest]");
        context.waitTicks(10);
        double startX = context.computeOnClient(client -> client.player.getX());
        double startZ = context.computeOnClient(client -> client.player.getZ());
        context.getInput().holdKeyFor(options -> options.keyUp, 40);
        context.getInput().holdKeyFor(options -> options.keyJump, 20);
        context.waitTicks(4);
        double flown = context.computeOnClient(client -> Math.hypot(client.player.getX() - startX, client.player.getZ() - startZ));
        if (flown < 4.0) throw new AssertionError("the helm did not steer the airship: moved " + flown);
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("airship_flying"));
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        world.getServer().runCommand("ride @p dismount");
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("clear @p");
        measureBiggestStructure(context, world);
    }

    /** Frames per second looking at open sky, then at a structure of the largest size there is. */
    private static void measureBiggestStructure(ClientGameTestContext context, TestSingleplayerContext world) {
        // Clear of the elevator's posts, which a plate this wide would otherwise join onto.
        BlockPos core = new BlockPos(STRUCTURES_X + 10, SURFACE + 14, -24);
        world.getServer().runCommand("tp @p " + core.getX() + " " + (core.getY() - 3) + " " + core.getZ() + " facing " + core.getX() + " " + core.getY() + " " + (core.getZ() + 8));
        context.waitTicks(40);
        int before = context.computeOnClient(client -> client.getFps());
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int x = -20; x < 20; x++) {
                for (int z = -6; z <= 18; z++) {
                    boolean balloon = (x + 20) % 4 == 0 && (z + 6) % 4 == 0;
                    level.setBlock(core.offset(x, 0, z), (balloon ? ModContent.LIFT_BALLOON : Blocks.OAK_PLANKS).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            level.setBlock(core, ModContent.AIRSHIP_HELM.defaultBlockState(), Block.UPDATE_CLIENTS);
            fuelTank(level, core.west(), 8);
            MovingStructureEntity big = StructureAssembler.assemble(level, core, null).structure();
            if (big == null) throw new AssertionError("the big structure did not assemble");
            big.steerForTest(new Input(true, false, false, true, false, false, false));
        });
        context.waitTicks(60);
        int after = context.computeOnClient(client -> client.getFps());
        LogUtils.getLogger().info("Frames per second: {} with no structure in view, {} with a moving {}-block structure", before, after, 40 * 25);
        world.getServer().runOnServer(server -> {
            for (MovingStructureEntity structure : MovingStructures.in(server.overworld())) {
                if (structure.blocks().size() > 900) structure.discard();
            }
        });
    }

    /** A real click on a chest that is part of a ship, through the structure aiming rather than the world's. */
    private static void testChestAboard(ClientGameTestContext context, TestSingleplayerContext world) {
        Vec3[] spots = world.getServer().computeOnServer(server -> {
            StructurePose pose = ship(server).pose();
            return new Vec3[]{
                    new Vec3(pose.worldX(-2.0D, 0.0D), pose.position().y + 1.0D, pose.worldZ(-2.0D, 0.0D)),
                    new Vec3(pose.worldX(-2.0D, 2.0D), pose.position().y + 1.4D, pose.worldZ(-2.0D, 2.0D)),
                    new Vec3(pose.worldX(0.0D, -1.0D), pose.position().y + 1.0D, pose.worldZ(0.0D, -1.0D))
            };
        });
        world.getServer().runCommand("tp @p " + spots[0].x + " " + spots[0].y + " " + spots[0].z);
        context.waitTicks(15);
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, spots[2]));
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of("aiming_aboard"));
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, spots[1]));
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(8);
        boolean opened = context.computeOnClient(client -> client.player.containerMenu instanceof ChestMenu);
        if (!opened) throw new AssertionError("clicking the chest aboard opened nothing");
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    /** A hull sitting below the waterline: dry to stand in and dry to look at. */
    private static void testHull(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            if (StructureAssembler.assemble(server.overworld(), HULL_CORE, null).structure() == null) {
                throw new AssertionError("the hull did not assemble");
            }
        });
        context.waitTicks(30);
        BlockPos floor = HULL_CORE.offset(0, -1, 3);
        boolean flooded = world.getServer().computeOnServer(server -> server.overworld().getFluidState(floor).is(FluidTags.WATER));
        if (!flooded) throw new AssertionError("the sea did not fill the hull's place, so this tests nothing");

        shot(context, world, "ship_hull_above", floor.getX() - 6.5, floor.getY() + 6.0, floor.getZ() - 6.5, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        world.getServer().runCommand("tp @p " + (floor.getX() + 0.5) + " " + floor.getY() + " " + (floor.getZ() + 0.5));
        context.waitTicks(15);
        boolean wet = context.computeOnClient(client -> client.player.isInWater() || client.player.isUnderWater());
        if (wet) throw new AssertionError("the player is in the water inside the hull");
        context.takeScreenshot(TestScreenshotOptions.of("ship_hull_inside"));
    }

    private static MovingStructureEntity ship(MinecraftServer server) {
        return server.overworld().getEntitiesOfClass(MovingStructureEntity.class, new AABB(SHIP_HELM).inflate(60.0D),
                structure -> structure.kind() == StructureKind.SHIP).getFirst();
    }

    private static void assemble(ClientGameTestContext context, TestSingleplayerContext world, BlockPos core) {
        world.getServer().runCommand("tp @p " + (core.getX() + 0.5) + " " + (core.getY() + 1.0) + " " + (core.getZ() + 2.5));
        context.waitTicks(15);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(core, 1.0D)));
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        boolean gone = world.getServer().computeOnServer(server -> server.overworld().getBlockState(core).isAir());
        if (!gone) throw new AssertionError("clicking the core did not assemble " + core);
    }

    private static void buildStructures(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Block planks = Blocks.OAK_PLANKS;
        Block wool = ModContent.LIFT_BALLOON;

        // A car between four posts that stop short of touching it, and a call block at two floors.
        BlockPos e = ELEVATOR_CORE;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(e.offset(x, 0, z), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int y = 0; y < 14; y++) {
            for (BlockPos corner : List.of(e.offset(2, y, 2), e.offset(-2, y, 2), e.offset(2, y, -2), e.offset(-2, y, -2))) {
                level.setBlock(corner, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(e, ModContent.ELEVATOR_CORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(ELEVATOR_BOTTOM, ModContent.ELEVATOR_CALL.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(ELEVATOR_TOP, ModContent.ELEVATOR_CALL.defaultBlockState(), Block.UPDATE_CLIENTS);

        // A deep pond with a small ship on it, bow to the east.
        for (int x = STRUCTURES_X - 1; x < STRUCTURES_X + 35; x++) {
            for (int z = 25; z < 43; z++) {
                boolean bank = x < STRUCTURES_X || x >= STRUCTURES_X + 34 || z < 26 || z >= 42;
                level.setBlock(new BlockPos(x, SURFACE - 3, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = SURFACE - 2; y <= SURFACE; y++) {
                    level.setBlock(new BlockPos(x, y, z), (bank ? Blocks.STONE : Blocks.WATER).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        // An open-topped hull whose floor is under water, at the far end of the pond.
        BlockPos hull = HULL_CORE.offset(0, -2, 3);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlock(hull.offset(x, 0, z), planks.defaultBlockState(), Block.UPDATE_CLIENTS);
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    level.setBlock(hull.offset(x, 1, z), planks.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(hull.offset(x, 2, z), planks.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlock(HULL_CORE, ModContent.SHIP_HELM.defaultBlockState().setValue(StructureCoreBlock.FACING, Direction.WEST), Block.UPDATE_CLIENTS);
        fuelTank(level, HULL_CORE.east(), 8);

        BlockPos s = SHIP_HELM;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) continue;
                level.setBlock(s.offset(x, 0, z), planks.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(s.offset(3, 1, 0), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(s.offset(-2, 1, 2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(s.offset(-2, 1, -2), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
        level.setBlock(s, ModContent.SHIP_HELM.defaultBlockState().setValue(StructureCoreBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        fuelTank(level, s.west(), 8);

        // A small airship standing on legs above the grass.
        BlockPos a = AIRSHIP_HELM;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(a.offset(x, -1, z), planks.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(a.offset(x, 3, z), wool.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int y = 0; y <= 2; y++) {
            level.setBlock(a.offset(2, y, 2), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(a.offset(-2, y, 2), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(a.offset(2, y, -2), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(a.offset(-2, y, -2), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(a, ModContent.AIRSHIP_HELM.defaultBlockState().setValue(StructureCoreBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
        fuelTank(level, a.below(), 8);
    }

    /** Ships and airships run on diesel: a filled tank in place of a deck block. */
    private static void fuelTank(ServerLevel level, BlockPos pos, int diesel) {
        level.setBlock(pos, ModContent.FUEL_TANK.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof com.example.redstonetransportation.block.FuelTankBlockEntity tank) {
            for (int i = 0; i < diesel; i++) tank.addDiesel();
        }
    }

    private static void putDriverOnShip(MinecraftServer server) {
        ServerLevel level = server.overworld();
        MovingStructureEntity ship = level.getEntitiesOfClass(MovingStructureEntity.class, new AABB(SHIP_HELM).inflate(4.0D)).getFirst();
        ship.steerForTest(new Input(true, false, false, true, false, false, false));
    }

    private static void stopDriver(MinecraftServer server) {
        for (MovingStructureEntity structure : server.overworld().getEntitiesOfClass(MovingStructureEntity.class, new AABB(SHIP_HELM).inflate(60.0D))) {
            structure.steerForTest(null);
        }
    }

    /** The items through real mouse input, rather than by calling their code. */
    private static void testRealClicks(ClientGameTestContext context, TestSingleplayerContext world) {
        // Two clicks with the track layer lay the line between them.
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p redstone_transportation:track_layer");
        BlockPos from = rightClick(context, world, 50.5, RAIL_Y, 35.5, new BlockPos(50, SURFACE, 38));
        BlockPos to = rightClick(context, world, 58.5, RAIL_Y, 35.5, new BlockPos(58, SURFACE, 38));
        boolean laid = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(from.above(), to.above())) {
                if (!BaseRailBlock.isRail(level, pos)) return false;
            }
            return true;
        });
        if (!laid) throw new AssertionError("two clicks with the track layer did not lay the line " + from + " to " + to);
        shot(context, world, "track_layer_result", 54.5, RAIL_Y + 3.0, 33.5, 54.5, RAIL_Y, 38.5);

        // A drive rail placed by hand points the way the player faces.
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p redstone_transportation:drive_rail");
        BlockPos railSpot = rightClick(context, world, 49.5, RAIL_Y, 30.5, new BlockPos(52, SURFACE, 30)).above();
        boolean placed = world.getServer().computeOnServer(server -> {
            BlockState state = server.overworld().getBlockState(railSpot);
            return state.is(ModContent.DRIVE_RAIL) && DriveRailBlock.heading(state, false).x > 0.5D;
        });
        if (!placed) throw new AssertionError("a hand-placed drive rail did not point the way the player faced at " + railSpot);
    }

    /** A real right-click with the coupler must link chest, hopper and furnace carts instead of opening them. */
    private static void testCouplerClicks(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            spawn(level, EntityTypes.CHEST_MINECART, 64, 12);
            spawn(level, EntityTypes.HOPPER_MINECART, 66, 12);
            spawn(level, EntityTypes.FURNACE_MINECART, 68, 12);
        });
        context.waitTicks(10);
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p redstone_transportation:tram_coupler");
        world.getServer().runCommand("tp @p 66.5 " + RAIL_Y + " 14.5");
        context.waitTicks(6);

        clickCart(context, new Vec3(64.5, RAIL_Y + 0.4, 12.5));
        clickCart(context, new Vec3(66.5, RAIL_Y + 0.4, 12.5));
        clickCart(context, new Vec3(66.5, RAIL_Y + 0.4, 12.5));
        clickCart(context, new Vec3(68.5, RAIL_Y + 0.4, 12.5));

        boolean noScreen = context.computeOnClient(client -> client.gui.screen() == null);
        if (!noScreen) throw new AssertionError("the coupler opened a cart's inventory instead of linking it");
        int links = world.getServer().computeOnServer(server -> {
            int total = 0;
            for (AbstractMinecart cart : server.overworld().getEntitiesOfClass(AbstractMinecart.class,
                    new AABB(63, RAIL_Y - 1, 11, 70, RAIL_Y + 2, 14))) {
                total += TrainLinkManager.getLinks(cart).size();
            }
            return total;
        });
        if (links != 4) throw new AssertionError("expected chest-hopper-furnace coupled (4 link ends), found " + links);
    }

    private static void clickCart(ClientGameTestContext context, Vec3 cart) {
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, cart));
        context.waitTicks(3);
        boolean aiming = context.computeOnClient(client -> client.hitResult instanceof EntityHitResult);
        if (!aiming) throw new AssertionError("not aiming at the cart at " + cart);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(4);
    }

    /** Every recipe must reach the player's recipe book on joining, and show in a real crafting table. */
    private static void testRecipeBook(ClientGameTestContext context, TestSingleplayerContext world) {
        boolean known = world.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            String machine = FabricLoader.getInstance().isModLoaded("analog_alu") ? "tree_cutter" : "tree_cutter_without_alu";
            for (String path : List.of(machine, "drive_rail", "track_layer", "cargo_tram", "redstone_motor")) {
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath("redstone_transportation", path));
                if (!player.getRecipeBook().contains(key)) return false;
            }
            return true;
        });
        if (!known) throw new AssertionError("the mod's recipes were not unlocked on joining");

        BlockPos table = new BlockPos(70, RAIL_Y, 16);
        world.getServer().runCommand("setblock " + table.getX() + " " + table.getY() + " " + table.getZ() + " minecraft:crafting_table");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("tp @p 67.5 " + RAIL_Y + " 16.5");
        context.waitTicks(6);
        context.runOnClient(client -> {
            client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
            client.player.getRecipeBook().setFiltering(RecipeBookType.CRAFTING, false);
            client.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(table));
        });
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitForScreen(CraftingScreen.class);
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("recipe_book"));

        // Look a machine up the way a player would: search the book, then click the result to lay out its recipe.
        context.getInput().setCursorPos(240, 113);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().typeChars("quarry");
        context.waitTicks(10);
        context.getInput().setCursorPos(155, 155);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().setCursorPos(30, 30);
        context.waitTicks(10);
        context.takeScreenshot(TestScreenshotOptions.of("recipe_book_machine"));
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    /** Real clicks lay a diagonal, then an L after clicking the air, and a real tram rides the diagonal. */
    private static void testTrackStyles(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p redstone_transportation:track_layer");
        // The last line is still waiting to be extended; shift-clicking lets go of it.
        context.getInput().holdKey(options -> options.keyShift);
        rightClick(context, world, 60.5, RAIL_Y, -13.5, new BlockPos(62, SURFACE, -14));
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(3);
        rightClick(context, world, 60.5, RAIL_Y, -13.5, new BlockPos(62, SURFACE, -14));
        rightClick(context, world, 78.5, RAIL_Y, 0.5, new BlockPos(76, SURFACE, 0));
        boolean diagonal = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            return BaseRailBlock.isRail(level, new BlockPos(69, RAIL_Y, -7)) && !BaseRailBlock.isRail(level, new BlockPos(76, RAIL_Y, -14));
        });
        if (!diagonal) throw new AssertionError("two clicks out of line did not lay a diagonal");

        context.runOnClient(client -> client.player.setXRot(-90.0F));
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(12);
        rightClick(context, world, 88.5, RAIL_Y, 4.5, new BlockPos(86, SURFACE, 6));
        boolean corner = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            return TrackLayerItem.joinedRails(level, new BlockPos(76, RAIL_Y, 0)).size() == 2
                    && TrackLayerItem.joinedRails(level, new BlockPos(86, RAIL_Y, 0)).size() == 2
                    && level.getBlockState(new BlockPos(86, RAIL_Y, 0)).getValue(BlockStateProperties.RAIL_SHAPE) == RailShape.SOUTH_WEST;
        });
        if (!corner) throw new AssertionError("clicking the air did not switch to an L that joins the diagonal");
        shot(context, world, "track_layer_diagonal", 74.5, RAIL_Y + 14.0, -18.5, 74.5, RAIL_Y, -3.5);

        world.getServer().runCommand("tp @p 74.5 " + RAIL_Y + " -11.5 facing 67.5 " + RAIL_Y + " -8.5");
        context.waitTicks(10);
        drivePoint(context, world, new BlockPos(61, RAIL_Y, -14));
        world.getServer().runCommand("setblock 62 " + RAIL_Y + " -14 redstone_transportation:drive_rail[shape=east_west,powered=true]");
        world.getServer().runCommand("summon redstone_transportation:redstone_tram 62.5 " + (RAIL_Y + 0.1) + " -13.5");
        // Each rail of the stairs bends, but a long body riding them should simply point along the diagonal.
        AABB stairs = new AABB(64.0D, RAIL_Y - 1.0D, -13.0D, 75.0D, RAIL_Y + 2.0D, -2.0D);
        for (int tick = 0; tick < 24; tick++) {
            context.waitTick();
            float swing = context.computeOnClient(client -> {
                for (RedstoneTramEntity tram : client.level.getEntitiesOfClass(RedstoneTramEntity.class, stairs)) {
                    float drawn = tram.getBehavior() instanceof NewMinecartBehavior behavior && behavior.cartHasPosRotLerp()
                            ? behavior.getCartLerpYRot(0.5F) : tram.getYRot();
                    return Mth.degreesDifferenceAbs(drawn, -45.0F);
                }
                return 0.0F;
            });
            if (swing > 5.0F) throw new AssertionError("the tram swings " + swing + " degrees off the diagonal");
            if (tick == 15) context.takeScreenshot(TestScreenshotOptions.of("tram_on_diagonal"));
        }
    }

    /** Whatever makes a block go at this spot: a battery circuit where ALU has them, redstone where not. */
    private static void drivePoint(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
        if (ElectricPower.isAvailable()) {
            world.getServer().runOnServer(server -> TestWorld.circuit(server.overworld(), pos));
            context.waitTicks(45);
        } else {
            world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:redstone_block");
        }
    }

    /** A circuit built as a player would: nothing runs until a real click closes its switch, and a second click stops it all. */
    private static void testElectricity(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = 108;
        int top = -8;
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            BlockState cable = aluBlock("copper_cable").defaultBlockState();
            for (int z = top; z <= top + 2; z++) {
                level.setBlock(new BlockPos(x, RAIL_Y, z), cable, Block.UPDATE_ALL);
                level.setBlock(new BlockPos(x + 4, RAIL_Y, z), cable, Block.UPDATE_ALL);
            }
            level.setBlock(new BlockPos(x + 2, RAIL_Y, top), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 1, RAIL_Y, top + 2), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 3, RAIL_Y, top + 2), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 1, RAIL_Y, top), device("circuit_switch"), Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 3, RAIL_Y, top), device("resistor_load"), Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 2, RAIL_Y, top + 2), device("battery"), Block.UPDATE_ALL);
            BlockState belt = ModContent.CONVEYOR_BELT.defaultBlockState().setValue(ConveyorBeltBlock.FACING, Direction.EAST);
            for (int beltX = x + 5; beltX <= x + 9; beltX++) level.setBlock(new BlockPos(beltX, RAIL_Y, top + 1), belt, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 10, RAIL_Y, top + 1), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            // The tree cutter runs on any live cable it touches; the other machines take power through a controller.
            level.setBlock(new BlockPos(x + 5, RAIL_Y, top + 2), ModContent.MACHINES.get(MachineType.TREE_CUTTER).defaultBlockState()
                    .setValue(MachineBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        });
        BlockPos belt = new BlockPos(x + 9, RAIL_Y, top + 1);
        BlockPos machine = new BlockPos(x + 5, RAIL_Y, top + 2);
        context.waitTicks(50);
        if (!isBeltStopped(world, belt)) throw new AssertionError("the belt ran with the switch open");

        BlockPos circuitSwitch = new BlockPos(x + 1, RAIL_Y, top);
        BlockPos clickedSwitch = rightClick(context, world, x + 1.5D, RAIL_Y, top + 2.5D - 5.0D, circuitSwitch);
        boolean closed = world.getServer().computeOnServer(server -> server.overworld().getBlockState(circuitSwitch)
                .getValue(com.example.analogalu.block.CircuitDeviceBlock.CLOSED));
        if (!closed) throw new AssertionError("clicking the switch at " + clickedSwitch + " did not close it");
        world.getServer().runCommand("tp @p " + (x + 4.5D) + " " + RAIL_Y + " 0.5");
        context.waitTicks(3);
        world.getServer().runCommand("summon minecraft:item " + (x + 6.5D) + " " + (RAIL_Y + 0.5D) + " " + (top + 1.5D) + " {Item:{id:\"minecraft:diamond\",count:1}}");
        BlockPos chest = new BlockPos(x + 10, RAIL_Y, top + 1);
        boolean arrived = false;
        for (int wait = 0; wait < 30 && !arrived; wait++) {
            context.waitTicks(10);
            arrived = world.getServer().computeOnServer(server -> server.overworld().getBlockEntity(chest) instanceof Container box && box.countItem(Items.DIAMOND) == 1);
        }
        if (!arrived) throw new AssertionError("closing the switch did not run the belt");
        boolean running = world.getServer().computeOnServer(server -> server.overworld().getBlockState(machine).getValue(MachineBlock.STATUS) != MachineStatus.OFF);
        if (!running) throw new AssertionError("closing the switch did not run the machine");
        shot(context, world, "electric_circuit", x + 5.5D, RAIL_Y + 6.0D, top + 9.5D, x + 5.5D, RAIL_Y, top + 1.5D);
        boolean reaches = world.getServer().computeOnServer(server -> {
            BlockState feed = server.overworld().getBlockState(new BlockPos(x + 4, RAIL_Y, top + 1));
            return feed.getValue((BooleanProperty) feed.getBlock().getStateDefinition().getProperty("east"));
        });
        if (!reaches) throw new AssertionError("the cable feeding the belt draws no arm into it");
        shot(context, world, "electric_connection", x + 3.2D, RAIL_Y + 2.6D, top + 4.4D, x + 5.0D, RAIL_Y + 0.4D, top + 1.9D);

        rightClick(context, world, x + 1.5D, RAIL_Y, top + 2.5D - 5.0D, circuitSwitch);
        context.waitTicks(60);
        if (!isBeltStopped(world, belt)) throw new AssertionError("opening the switch did not stop the belt");
    }

    /**
     * Off to one side, on ground of its own: a hill with a tree on it and two holes beyond, then the
     * leveler opened with a real click, started from its menu and watched cutting through.
     */
    private static void testLandLeveler(ClientGameTestContext context, TestSingleplayerContext world) {
        int laneX = 150;
        int startZ = 2;
        int stopZ = 24;
        BlockPos start = new BlockPos(laneX, RAIL_Y, startZ);
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int x = laneX - 12; x <= laneX + 12; x++) {
                for (int z = startZ - 8; z <= stopZ + 4; z++) {
                    level.setBlock(new BlockPos(x, SURFACE - 1, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(new BlockPos(x, SURFACE, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    for (int y = RAIL_Y; y < RAIL_Y + 24; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            for (int x = laneX - 9; x <= laneX + 9; x++) {
                for (int z = 8; z <= 15; z++) {
                    int height = 6 - Math.max(Math.abs(z - 11) * 2 - 1, 0) - Math.abs(x - laneX) / 4;
                    for (int y = 0; y < height; y++) {
                        BlockState block = y == height - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : y >= height - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        level.setBlock(new BlockPos(x, RAIL_Y + y, z), block, Block.UPDATE_CLIENTS);
                    }
                }
            }
            BlockPos trunk = new BlockPos(laneX + 2, RAIL_Y + 6, 11);
            for (int y = 0; y < 4; y++) level.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 2; dy <= 4; dy++) {
                        BlockPos leaf = trunk.offset(dx, dy, dz);
                        if (level.getBlockState(leaf).isAir() && Math.abs(dx) + Math.abs(dz) < 4) {
                            level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            for (int x = laneX - 4; x <= laneX + 4; x++) {
                for (int z = 18; z <= 20; z++) {
                    if ((x + z) % 3 != 0) level.setBlock(new BlockPos(x, SURFACE, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            level.setBlock(new BlockPos(laneX + 3, SURFACE, 21), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(laneX, RAIL_Y, stopZ), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(start, ModContent.LAND_LEVELER.defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            LandLevelerBlockEntity leveler = (LandLevelerBlockEntity) level.getBlockEntity(start);
            leveler.setItem(LandLevelerBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 8));
            leveler.setItem(LandLevelerBlockEntity.FIRST_STORAGE, new ItemStack(Items.COBBLESTONE, 16));
            leveler.setItem(LandLevelerBlockEntity.FILL_SLOT, new ItemStack(Items.STONE_BRICKS, 64));
            leveler.setItem(2, new ItemStack(Items.STONE_BRICKS, 64));
            leveler.setItem(LandLevelerBlockEntity.LIGHTER_SLOT, new ItemStack(Items.FLINT_AND_STEEL));
            // A chest on top carries the rest of the bricks and rides along.
            level.setBlock(start.above(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
            Container load = (Container) level.getBlockEntity(start.above());
            load.setItem(0, new ItemStack(Items.STONE_BRICKS, 64));
            load.setItem(1, new ItemStack(Items.STONE_BRICKS, 64));
        });
        context.waitTicks(10);

        switchLanguage(context, "ko_kr");
        rightClick(context, world, laneX + 0.5D, RAIL_Y, startZ - 2.5D, start);
        context.waitForScreen(LandLevelerScreen.class);
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("land_leveler_menu"));
        context.runOnClient(client -> client.gameMode.handleInventoryButtonClick(client.player.containerMenu.containerId, LandLevelerBlockEntity.BUTTON_RUN));
        context.waitTicks(30);
        context.takeScreenshot(TestScreenshotOptions.of("land_leveler_menu_running"));
        boolean following = context.computeOnClient(client -> client.gui.screen() instanceof LandLevelerScreen);
        if (!following) throw new AssertionError("the leveler's menu closed when it moved");
        context.setScreen(() -> null);
        switchLanguage(context, "en_us");

        BlockPos halfway = new BlockPos(laneX, RAIL_Y, 11);
        boolean reached = false;
        for (int wait = 0; wait < 40 && !reached; wait++) {
            context.waitTicks(10);
            reached = world.getServer().computeOnServer(server -> server.overworld().getBlockState(halfway).is(ModContent.LAND_LEVELER));
        }
        if (!reached) throw new AssertionError("the leveler did not get into the hill");
        shot(context, world, "land_leveler_working", laneX + 13.5D, RAIL_Y + 9.0D, 4.5D, laneX + 0.5D, RAIL_Y + 1.0D, 12.5D);

        BlockPos end = new BlockPos(laneX, RAIL_Y, stopZ - 1);
        boolean done = false;
        for (int wait = 0; wait < 60 && !done; wait++) {
            context.waitTicks(10);
            done = world.getServer().computeOnServer(server -> server.overworld().getBlockState(end).is(ModContent.LAND_LEVELER));
        }
        if (!done) throw new AssertionError("the leveler did not reach the chest");
        boolean flat = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int x = laneX - 4; x <= laneX + 4; x++) {
                for (int z = startZ + 1; z < stopZ - 1; z++) {
                    if (!level.getBlockState(new BlockPos(x, RAIL_Y, z)).isAir()) return false;
                    if (!level.getBlockState(new BlockPos(x, SURFACE, z)).is(Blocks.STONE_BRICKS)) return false;
                }
            }
            return true;
        });
        if (!flat) throw new AssertionError("the strip behind the leveler is not a flat stone brick road");
        boolean riding = world.getServer().computeOnServer(server -> server.overworld().getBlockState(end.above()).is(Blocks.CHEST));
        if (!riding) throw new AssertionError("the chest on top did not ride along");
        shot(context, world, "land_leveler_done", laneX + 0.5D, RAIL_Y + 11.0D, startZ - 7.5D, laneX + 0.5D, RAIL_Y, 14.5D);
    }

    /** Flat ground of its own, well clear of everything else, cleared to a height of 24. */
    private static void clearGround(ServerLevel level, int fromX, int toX, int fromZ, int toZ) {
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                level.setBlock(new BlockPos(x, SURFACE - 1, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, SURFACE, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = RAIL_Y; y < RAIL_Y + 24; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Each gun held in first person and from behind, and a revolver fired for real: R to cock and a
     * right-click, at a husk standing ten blocks off.
     */
    private static void testGuns(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = 190;
        int z = 10;
        world.getServer().runOnServer(server -> clearGround(server.overworld(), x - 8, x + 8, z - 6, z + 20));
        world.getServer().runCommand("summon minecraft:husk " + (x + 0.5D) + " " + RAIL_Y + " " + (z + 10.5D)
                + " {NoAI:1b,PersistenceRequired:1b,Health:100f,attributes:[{id:\"minecraft:max_health\",base:100d}]}");
        world.getServer().runCommand("tp @p " + (x + 0.5D) + " " + RAIL_Y + " " + (z + 0.5D) + " facing " + (x + 0.5D) + " " + (RAIL_Y + 1.4D) + " " + (z + 10.5D));
        world.getServer().runCommand("item replace entity @p hotbar.0 with analog_alu:revolver[analog_alu:gun_rounds=6]");
        world.getServer().runCommand("item replace entity @p hotbar.1 with analog_alu:musket");
        world.getServer().runCommand("item replace entity @p hotbar.2 with analog_alu:shotgun[analog_alu:gun_rounds=2]");
        context.runOnClient(client -> client.gui.hud.toggle());
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        context.waitTicks(4);
        Vec3 chest = new Vec3(x + 0.5D, RAIL_Y + 1.2D, z + 10.5D);
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, chest));
        context.waitTicks(6);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(TestScreenshotOptions.of("gun_revolver"));

        float before = world.getServer().computeOnServer(server -> healthOfHusk(server.overworld()));
        context.getInput().pressKey(options -> com.example.analogalu.gun.GunClient.ACTION);
        context.waitTicks(4);
        boolean cocked = context.computeOnClient(client -> com.example.analogalu.gun.GunItem.isCocked(client.player.getMainHandItem()));
        if (!cocked) throw new AssertionError("pressing R did not cock the revolver");
        // Right-click held: aim down the sights, which zooms the view in.
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(6);
        boolean aiming = context.computeOnClient(client -> com.example.analogalu.gun.GunItem.isAiming(client.player, client.player.getMainHandItem()));
        if (!aiming) throw new AssertionError("holding right-click did not aim the revolver");
        context.takeScreenshot(TestScreenshotOptions.of("gun_revolver_aiming"));
        // Left-click: fire.
        context.getInput().pressKey(options -> options.keyAttack);
        context.waitTicks(2);
        context.takeScreenshot(TestScreenshotOptions.of("gun_revolver_fired"));
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(4);
        float after = world.getServer().computeOnServer(server -> healthOfHusk(server.overworld()));
        if (!(before - after > 20.0F)) throw new AssertionError("the revolver shot took " + (before - after) + " off the husk");

        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
        context.waitTicks(6);
        context.takeScreenshot(TestScreenshotOptions.of("gun_musket"));
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(6);
        context.takeScreenshot(TestScreenshotOptions.of("gun_musket_aiming"));
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(2);
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
        context.waitTicks(6);
        context.takeScreenshot(TestScreenshotOptions.of("gun_shotgun"));
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        context.runOnClient(client -> client.gui.hud.toggle());

        // Held in the hand as others see it: three armor stands facing east, seen from their right side.
        String[] guns = {"revolver", "musket", "shotgun"};
        for (int i = 0; i < guns.length; i++) {
            world.getServer().runCommand("summon minecraft:armor_stand " + (x - 3.5D + 3 * i) + " " + RAIL_Y + " " + (z + 4.5D)
                    + " {ShowArms:1b,NoGravity:1b,Rotation:[-90f,0f],equipment:{mainhand:{id:\"analog_alu:" + guns[i] + "\",count:1}}}");
        }
        shot(context, world, "gun_third_person", x + 0.5D, RAIL_Y + 1.6D, z + 8.0D, x + 0.5D, RAIL_Y + 1.0D, z + 4.5D);
        world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        world.getServer().runCommand("kill @e[type=minecraft:husk]");
        world.getServer().runCommand("clear @p");
    }

    private static final int CAR_X = 230;
    private static final int CAR_Z = 10;
    private static final BlockPos CAR_CORE = new BlockPos(CAR_X, RAIL_Y + 1, CAR_Z);

    /**
     * A car built block by block, assembled with a click on its steering wheel, then driven from
     * its seat with the player's own keys.
     */
    private static void testCar(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            clearGround(level, CAR_X - 10, CAR_X + 10, CAR_Z - 8, CAR_Z + 44);
            // The chassis: wheels on the four corners, the engine in the nose, red bodywork between.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -2; dz <= 1; dz++) {
                    BlockPos at = new BlockPos(CAR_X + dx, RAIL_Y, CAR_Z + dz);
                    boolean corner = dx != 0 && (dz == -2 || dz == 1);
                    Block block = corner ? ModContent.CAR_WHEEL : dx == 0 && dz == 1 ? ModContent.CAR_ENGINE : BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("red_concrete"));
                    level.setBlock(at, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            level.setBlock(CAR_CORE, ModContent.STEERING_WHEEL.defaultBlockState().setValue(StructureCoreBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
            level.setBlock(CAR_CORE.north(), ModContent.CAR_SEAT.defaultBlockState()
                    .setValue(com.example.redstonetransportation.block.CarSeatBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
            BlockPos tank = CAR_CORE.north(2);
            level.setBlock(tank, ModContent.FUEL_TANK.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(tank) instanceof com.example.redstonetransportation.block.FuelTankBlockEntity fuel) {
                for (int i = 0; i < 4; i++) fuel.addDiesel();
            }
        });
        context.waitTicks(10);
        shot(context, world, "car_built", CAR_X + 4.5, RAIL_Y + 3.0, CAR_Z + 5.5, CAR_X + 0.5, RAIL_Y + 0.8, CAR_Z);

        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("gamemode survival @p");
        assemble(context, world, CAR_CORE);
        boolean car = world.getServer().computeOnServer(server -> MovingStructures.in(server.overworld()).stream()
                .anyMatch(structure -> structure.kind() == StructureKind.CAR));
        if (!car) throw new AssertionError("clicking the steering wheel made no car");

        // Into the driver's seat and away down the road.
        world.getServer().runCommand("tp @p " + (CAR_X + 0.5) + " " + (RAIL_Y + 2.0) + " " + (CAR_Z - 0.5) + " 0 10");
        world.getServer().runCommand("execute as @p at @s run ride @s mount @e[type=redstone_transportation:moving_structure,limit=1,sort=nearest]");
        context.waitTicks(10);
        boolean riding = context.computeOnClient(client -> client.player.getVehicle() instanceof MovingStructureEntity);
        if (!riding) throw new AssertionError("the player is not sitting in the car");
        // A sitting player's hips are 0.6 above their feet; they belong on the cushion, 7/16 up the seat.
        double hips = context.computeOnClient(client -> client.player.getY()) + 0.6 - (RAIL_Y + 1);
        if (Math.abs(hips - 0.45) > 0.1) throw new AssertionError("the driver sits " + hips + " up the seat, not on its cushion");
        double startZ = context.computeOnClient(client -> client.player.getZ());
        context.runOnClient(client -> client.player.setYRot(0.0F));
        context.getInput().holdKeyFor(options -> options.keyUp, 40);
        double driven = context.computeOnClient(client -> client.player.getZ()) - startZ;
        if (driven < 6.0) throw new AssertionError("the car went only " + driven + " blocks");
        context.takeScreenshot(TestScreenshotOptions.of("car_driver_view"));
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().holdKeyFor(options -> options.keyUp, 10);
        context.takeScreenshot(TestScreenshotOptions.of("car_driving"));
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(40);
        world.getServer().runCommand("ride @p dismount");
        Vec3 parked = world.getServer().computeOnServer(server -> MovingStructures.in(server.overworld()).stream()
                .filter(structure -> structure.kind() == StructureKind.CAR).findFirst().orElseThrow().position());
        shot(context, world, "car_parked", parked.x + 4.5, parked.y + 2.5, parked.z + 5.5, parked.x, parked.y + 0.5, parked.z);
        world.getServer().runCommand("gamemode creative @p");
    }

    /**
     * A disc put in a jukebox with a real click, which starts the song on the client, and an armor stand
     * in sneakers beside it.
     */
    /**
     * The netherite spear and two boss spears side by side: held, mid-jab and charging in first person, then from the
     * front in third person. The boss spears should sit in the hand and move exactly like the vanilla one.
     */
    private static void testSpears(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = 320;
        int z = 10;
        world.getServer().runOnServer(server -> clearGround(server.overworld(), x - 6, x + 6, z - 6, z + 12));
        world.getServer().runCommand("tp @p " + (x + 0.5D) + " " + RAIL_Y + " " + (z + 0.5D) + " facing " + (x + 0.5D) + " " + (RAIL_Y + 1.6D) + " " + (z + 10.5D));
        world.getServer().runCommand("clear @p");
        String[] spears = {"minecraft:netherite_spear", "boss_mode:ultimate_spear", "boss_mode:void_spear"};
        String[] names = {"netherite", "ultimate", "void"};
        for (int i = 0; i < spears.length; i++) {
            world.getServer().runCommand("item replace entity @p hotbar." + i + " with " + spears[i]);
        }
        context.runOnClient(client -> client.gui.hud.toggle());
        for (int i = 0; i < spears.length; i++) {
            final int slot = i;
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
            // Long enough for the spear to rise all the way after the switch.
            context.waitTicks(50);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("spear_" + names[i] + "_held"));
            context.getInput().pressKey(options -> options.keyAttack);
            context.waitTicks(2);
            context.takeScreenshot(TestScreenshotOptions.of("spear_" + names[i] + "_jab"));
            context.waitTicks(50);
            context.getInput().holdKey(options -> options.keyUse);
            context.waitTicks(12);
            context.takeScreenshot(TestScreenshotOptions.of("spear_" + names[i] + "_charge"));
            context.getInput().releaseKey(options -> options.keyUse);
            context.waitTicks(10);
        }
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        for (int i = 0; i < spears.length; i++) {
            final int slot = i;
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
            context.waitTicks(12);
            context.takeScreenshot(TestScreenshotOptions.of("spear_" + names[i] + "_third_person"));
        }
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.runOnClient(client -> client.gui.hud.toggle());
        world.getServer().runCommand("clear @p");
    }

    /** The five boss sets on armour stands, each with its sword and spear, seen from the front and the back. */
    private static void testBossGear(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = 290;
        int z = 10;
        world.getServer().runOnServer(server -> clearGround(server.overworld(), x - 4, x + 14, z - 8, z + 8));
        String[] tiers = {"shard", "tempest", "behemoth", "void", "ultimate"};
        for (int i = 0; i < tiers.length; i++) {
            String t = "boss_mode:" + tiers[i] + "_";
            world.getServer().runCommand("summon minecraft:armor_stand " + (x + i * 2.5D + 0.5D) + " " + RAIL_Y + " " + (z + 0.5D)
                    + " {ShowArms:1b,NoGravity:1b,NoBasePlate:1b,Rotation:[0f,0f],equipment:{"
                    + "head:{id:\"" + t + "helmet\",count:1},chest:{id:\"" + t + "chestplate\",count:1},"
                    + "legs:{id:\"" + t + "leggings\",count:1},feet:{id:\"" + t + "boots\",count:1},"
                    + "mainhand:{id:\"" + t + "sword\",count:1},offhand:{id:\"" + t + "spear\",count:1}}}");
        }
        double middle = x + 5.5D;
        shot(context, world, "boss_gear_front", middle, RAIL_Y + 1.6D, z + 6.0D, middle, RAIL_Y + 1.0D, z + 0.5D);
        shot(context, world, "boss_gear_back", middle, RAIL_Y + 1.6D, z - 5.0D, middle, RAIL_Y + 1.0D, z + 0.5D);
        world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
    }

    private static void testMusicDisc(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = 260;
        int z = 10;
        BlockPos jukebox = new BlockPos(x, RAIL_Y, z);
        world.getServer().runOnServer(server -> {
            clearGround(server.overworld(), x - 6, x + 6, z - 6, z + 8);
            server.overworld().setBlock(jukebox, Blocks.JUKEBOX.defaultBlockState(), Block.UPDATE_ALL);
        });
        world.getServer().runCommand("summon minecraft:armor_stand " + (x + 1.5D) + " " + RAIL_Y + " " + (z + 0.5D)
                + " {ShowArms:1b,NoGravity:1b,Rotation:[180f,0f],equipment:{feet:{id:\"analog_alu:sneakers\",count:1}}}");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("item replace entity @p hotbar.0 with modernization_world:music_disc_snowman");
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        context.runOnClient(client -> client.gui.hud.toggle());
        rightClick(context, world, x + 0.5D, RAIL_Y, z + 3.5D, jukebox);
        context.waitTicks(10);
        boolean playing = world.getServer().computeOnServer(server -> server.overworld().getBlockEntity(jukebox)
                instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity box && box.getSongPlayer().isPlaying());
        if (!playing) throw new AssertionError("clicking the jukebox with the Snowman disc played nothing");
        // The client has the song: its sound event resolves and its audio file is in the resources.
        boolean known = context.computeOnClient(client -> client.getSoundManager().getSoundEvent(
                Identifier.fromNamespaceAndPath("modernization_world", "music_disc.snowman")) != null);
        if (!known) throw new AssertionError("the client does not know the Snowman sound");
        boolean audio = context.computeOnClient(client -> client.getResourceManager().getResource(
                Identifier.fromNamespaceAndPath("modernization_world", "sounds/records/snowman.ogg")).isPresent());
        if (!audio) throw new AssertionError("the Snowman audio is not in the resources");
        context.takeScreenshot(TestScreenshotOptions.of("music_disc_playing"));
        context.runOnClient(client -> client.gui.hud.toggle());
        world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        world.getServer().runOnServer(server -> server.overworld().setBlock(jukebox, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        world.getServer().runCommand("clear @p");
    }

    private static float healthOfHusk(ServerLevel level) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class,
                new AABB(150.0D, 60.0D, -20.0D, 230.0D, 120.0D, 60.0D)).stream()
                .findFirst().map(net.minecraft.world.entity.LivingEntity::getHealth).orElse(-1.0F);
    }

    /**
     * Every power source in a row, seen from the + side and from the - side, then two batteries in
     * series running a load, with their cables lit.
     */
    private static void testPowerMarks(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = 190;
        int z = 40;
        String[] sources = {"battery", "rechargeable_battery", "coal_generator", "diesel_generator",
                "solar_generator", "wind_generator", "step_up_converter", "step_down_transformer"};
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            clearGround(level, x - 4, x + 20, z - 8, z + 14);
            for (int i = 0; i < sources.length; i++) {
                level.setBlock(new BlockPos(x + i * 2, RAIL_Y, z), aluBlock(sources[i]).defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
            }
            // Two 9 V batteries in series, + of one on - of the next, through a load.
            BlockState cable = aluBlock("copper_cable").defaultBlockState();
            int sz = z + 8;
            level.setBlock(new BlockPos(x, RAIL_Y, sz), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 1, RAIL_Y, sz), device("battery"), Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 2, RAIL_Y, sz), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 3, RAIL_Y, sz), device("battery"), Block.UPDATE_ALL);
            for (int dz = 0; dz <= 2; dz++) level.setBlock(new BlockPos(x + 4, RAIL_Y, sz + dz), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x + 3, RAIL_Y, sz + 2), device("resistor_load"), Block.UPDATE_ALL);
            for (int dx = 0; dx <= 2; dx++) level.setBlock(new BlockPos(x + dx, RAIL_Y, sz + 2), cable, Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x, RAIL_Y, sz + 1), cable, Block.UPDATE_ALL);
        });
        context.waitTicks(40);
        shot(context, world, "power_marks_positive", x + 7.5D, RAIL_Y + 2.2D, z - 5.5D, x + 7.5D, RAIL_Y + 0.5D, z + 0.5D);
        shot(context, world, "power_marks_negative", x + 7.5D, RAIL_Y + 2.2D, z + 5.5D, x + 7.5D, RAIL_Y + 0.5D, z + 0.5D);
        double across = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            double high = ((com.example.analogalu.block.CopperCableBlockEntity) level.getBlockEntity(new BlockPos(x + 4, RAIL_Y, z + 9))).getVoltage();
            double low = ((com.example.analogalu.block.CopperCableBlockEntity) level.getBlockEntity(new BlockPos(x, RAIL_Y, z + 9))).getVoltage();
            return high - low;
        });
        if (!(across > 17.0D)) throw new AssertionError("two batteries in series gave " + across + " V");
        shot(context, world, "power_series", x + 2.5D, RAIL_Y + 4.5D, z + 4.0D, x + 2.5D, RAIL_Y, z + 9.0D);
    }

    private static boolean isBeltStopped(TestSingleplayerContext world, BlockPos belt) {
        return world.getServer().computeOnServer(server -> server.overworld().getBlockState(belt).getValue(ConveyorBeltBlock.POWERED));
    }

    private static BlockState device(String name) {
        return aluBlock(name).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
    }

    private static Block aluBlock(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("analog_alu", name));
    }

    /** A survival player's tram leaves their inventory for the rail, and climbing aboard sends it off. */
    private static void testStation(ClientGameTestContext context, TestSingleplayerContext world) {
        int railZ = -14;
        for (int x = 108; x <= 128; x++) {
            world.getServer().runCommand("setblock " + x + " " + RAIL_Y + " " + railZ + " minecraft:rail[shape=east_west]");
        }
        world.getServer().runCommand("setblock 112 " + RAIL_Y + " " + railZ + " redstone_transportation:drive_rail[shape=east_west]");
        BlockPos station = new BlockPos(112, RAIL_Y, railZ + 1);
        world.getServer().runCommand("setblock 112 " + station.getY() + " " + station.getZ() + " redstone_transportation:vehicle_station[facing=north]");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("gamemode survival @p");
        context.waitTicks(4);

        rightClick(context, world, 112.5D, RAIL_Y, railZ + 3.5D, station);
        if (countTrams(world, station) != 0) throw new AssertionError("the station summoned a tram from nothing");

        world.getServer().runCommand("item replace entity @p inventory.5 with redstone_transportation:redstone_tram");
        context.waitTicks(4);
        rightClick(context, world, 112.5D, RAIL_Y, railZ + 3.5D, station);
        if (countTrams(world, station) != 1) throw new AssertionError("the tram in the inventory was not summoned");
        int left = context.computeOnClient(client -> client.player.getInventory().countItem(ModContent.REDSTONE_TRAM_ITEM));
        if (left != 0) throw new AssertionError("the summoned tram was not taken from the inventory");
        context.waitTicks(10);
        shot(context, world, "station_summoned", 115.5D, RAIL_Y + 2.0D, railZ + 5.5D, 112.5D, RAIL_Y + 0.5D, railZ);

        Vec3 stop = new Vec3(112.5D, RAIL_Y + 0.4D, railZ + 0.5D);
        world.getServer().runCommand("tp @p 114.5 " + RAIL_Y + " " + (railZ + 2.0D));
        context.waitTicks(6);
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, stop));
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(8);
        if (!context.computeOnClient(client -> client.player.isPassenger())) throw new AssertionError("the click did not board the tram");
        context.waitTicks(30);
        double rode = context.computeOnClient(client -> client.player.getX());
        if (rode < 116.0D) throw new AssertionError("boarding did not send the tram off: still at x=" + rode);
        world.getServer().runCommand("ride @p dismount");
        world.getServer().runCommand("gamemode creative @p");
        context.waitTicks(4);
    }

    private static int countTrams(TestSingleplayerContext world, BlockPos near) {
        return world.getServer().computeOnServer(server -> server.overworld()
                .getEntitiesOfClass(RedstoneTramEntity.class, new AABB(near).inflate(6.0D)).size());
    }

    /** Real clicks lay a belt round a corner and an item rides it; then every shape on show. */
    private static void testConveyors(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p redstone_transportation:conveyor_belt 16");
        for (int x = 92; x <= 94; x++) {
            placeOnGround(context, world, x - 2.5D, -11.5D, new Vec3(x + 0.8D, RAIL_Y, -11.5D));
        }
        placeOnGround(context, world, 94.5D, -13.5D, new Vec3(94.5D, RAIL_Y, -10.2D));
        placeOnGround(context, world, 94.5D, -12.5D, new Vec3(94.5D, RAIL_Y, -9.2D));

        BlockPos chest = new BlockPos(94, RAIL_Y, -9);
        world.getServer().runCommand("setblock 94 " + RAIL_Y + " -9 minecraft:chest");
        // Far enough off that the player does not simply pick the item up.
        world.getServer().runCommand("tp @p 88.5 " + RAIL_Y + " -3.5");
        context.waitTicks(3);
        if (ElectricPower.isAvailable()) {
            world.getServer().runOnServer(server -> TestWorld.circuit(server.overworld(), new BlockPos(91, RAIL_Y, -12)));
        }
        world.getServer().runCommand("summon minecraft:item 92.5 " + (RAIL_Y + 0.5) + " -11.5 {Item:{id:\"minecraft:diamond\",count:1}}");
        boolean turned = world.getServer().computeOnServer(server -> {
            BlockState corner = server.overworld().getBlockState(new BlockPos(94, RAIL_Y, -12));
            return corner.getValue(ConveyorBeltBlock.FACING) == Direction.SOUTH && corner.getValue(ConveyorBeltBlock.SHAPE) == ConveyorShape.RIGHT;
        });
        if (!turned) throw new AssertionError("laying on round a corner did not turn the end of the line");
        boolean arrived = false;
        for (int wait = 0; wait < 30 && !arrived; wait++) {
            context.waitTicks(10);
            arrived = world.getServer().computeOnServer(server -> server.overworld().getBlockEntity(chest) instanceof Container box && box.countItem(Items.DIAMOND) == 1);
        }
        if (!arrived) throw new AssertionError("the item did not ride round the corner into the chest");

        world.getServer().runOnServer(ScreenshotClientTest::buildConveyorShapes);
        context.waitTicks(10);
        shot(context, world, "conveyor_shapes", 104.5D, RAIL_Y + 6.0D, -3.5D, 104.5D, RAIL_Y, -10.5D);
        shot(context, world, "conveyor_slope", 104.0D, RAIL_Y + 1.2D, -17.5D, 104.0D, RAIL_Y + 0.6D, -12.5D);
        shot(context, world, "conveyor_both", 100.5D, RAIL_Y + 4.0D, 0.5D, 100.5D, RAIL_Y, -4.5D);
    }

    /** Aims at the far edge of the ground, so the look clears the belt just laid and sets the new one's facing. */
    private static void placeOnGround(ClientGameTestContext context, TestSingleplayerContext world, double x, double z, Vec3 aim) {
        world.getServer().runCommand("tp @p " + x + " " + RAIL_Y + " " + z);
        context.waitTicks(3);
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, aim));
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(6);
    }

    /** Over a step and back, a bend each way, and a stopped stretch. */
    private static void buildConveyorShapes(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (ElectricPower.isAvailable()) TestWorld.circuit(level, new BlockPos(99, RAIL_Y, -12));
        BlockState east = ModContent.CONVEYOR_BELT.defaultBlockState().setValue(ConveyorBeltBlock.FACING, Direction.EAST);
        BlockState south = east.setValue(ConveyorBeltBlock.FACING, Direction.SOUTH);
        BlockState west = east.setValue(ConveyorBeltBlock.FACING, Direction.WEST);
        level.setBlock(new BlockPos(103, RAIL_Y, -12), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(104, RAIL_Y, -12), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(105, RAIL_Y, -12), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int x = 100; x <= 102; x++) level.setBlock(new BlockPos(x, RAIL_Y, -12), east, Block.UPDATE_CLIENTS);
        for (int x = 103; x <= 105; x++) level.setBlock(new BlockPos(x, RAIL_Y + 1, -12), east, Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(106, RAIL_Y, -12), east, Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(107, RAIL_Y, -12), south, Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(107, RAIL_Y, -11), south, Block.UPDATE_CLIENTS);
        for (int x = 107; x >= 104; x--) level.setBlock(new BlockPos(x, RAIL_Y, -10), west, Block.UPDATE_CLIENTS);
        for (int z = -10; z <= -7; z++) level.setBlock(new BlockPos(103, RAIL_Y, z), south, Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(102, RAIL_Y, -8), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        // Two lines meeting head on, sharing one way out.
        for (int x = 97; x <= 99; x++) level.setBlock(new BlockPos(x, RAIL_Y, -5), east, Block.UPDATE_CLIENTS);
        for (int x = 103; x >= 101; x--) level.setBlock(new BlockPos(x, RAIL_Y, -5), west, Block.UPDATE_CLIENTS);
        for (int z = -5; z <= -3; z++) level.setBlock(new BlockPos(100, RAIL_Y, z), south, Block.UPDATE_CLIENTS);
    }

    /** A real click opens the hold, in its own four rows of eight. */
    private static void testCargoHold(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("clear @p");
        String tram = "@e[type=redstone_transportation:cargo_tram,limit=1]";
        world.getServer().runCommand("item replace entity " + tram + " container.0 with minecraft:iron_ingot 32");
        world.getServer().runCommand("item replace entity " + tram + " container.31 with minecraft:diamond 5");
        Vec3 hold = context.computeOnClient(client -> client.level.getEntitiesOfClass(CargoTramEntity.class,
                new AABB(-20.0D, 60.0D, -20.0D, 140.0D, 100.0D, 50.0D)).getFirst().position());
        world.getServer().runCommand("tp @p " + hold.x + " " + RAIL_Y + " " + (hold.z - 2.5));
        context.waitTicks(6);
        context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, hold.add(0.0D, 0.4D, 0.0D)));
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitForScreen(CargoTramScreen.class);
        context.waitTicks(6);
        int slots = context.computeOnClient(client -> client.player.containerMenu.slots.size());
        if (slots != 32 + 36) throw new AssertionError("the hold opened with " + slots + " slots");
        context.takeScreenshot(TestScreenshotOptions.of("cargo_tram_menu"));
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    /** Returns the block the click really landed on. */
    private static BlockPos rightClick(ClientGameTestContext context, TestSingleplayerContext world,
                                       double x, double y, double z, BlockPos target) {
        world.getServer().runCommand("tp @p " + x + " " + y + " " + z);
        context.waitTicks(3);
        // A rotation sent with the teleport can land after the first look; look again until aimed at the target.
        BlockPos hit = null;
        for (int attempt = 0; attempt < 5 && !target.equals(hit); attempt++) {
            context.getInput().lookAt(target);
            context.waitTicks(3);
            hit = context.computeOnClient(client -> client.hitResult instanceof BlockHitResult block ? block.getBlockPos().immutable() : null);
        }
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(6);
        return hit;
    }

    /** Standing in a trench puts the camera at wheel height, where overlapping parts show up. */
    private static void closeups(ClientGameTestContext context, TestSingleplayerContext world) {
        double wheelHeight = RAIL_Y + 0.3;
        shot(context, world, "closeup_tram_wheels", 4.5, SURFACE, 15.5, 5.5, wheelHeight, 12.5);
        shot(context, world, "closeup_tram_side_low", 7.5, SURFACE, 16.5, 7.5, wheelHeight + 0.4, 12.5);
        shot(context, world, "closeup_tram_nose", 10.5, SURFACE, 15.5, 8.8, wheelHeight + 0.6, 12.5);
        shot(context, world, "closeup_cargo_wheels", 12.5, SURFACE, 15.5, 13.5, wheelHeight, 12.5);
        shot(context, world, "closeup_cargo_end", 18.5, SURFACE, 15.5, 16.8, wheelHeight + 0.4, 12.5);
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name,
                             double x, double y, double z, double lookX, double lookY, double lookZ) {
        world.getServer().runCommand("tp @p " + x + " " + y + " " + z + " facing " + lookX + " " + lookY + " " + lookZ);
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(TestScreenshotOptions.of(name));
    }

    private static void buildShowcase(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int x = -12; x < 130; x++) {
            for (int z = -16; z < 44; z++) {
                level.setBlock(new BlockPos(x, SURFACE, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = SURFACE + 1; y < SURFACE + 18; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        // A trench beside the track, for looking at the running gear from wheel height.
        for (int x = 2; x < 22; x++) {
            for (int z = 14; z < 19; z++) {
                level.setBlock(new BlockPos(x, SURFACE, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, SURFACE - 1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        // Display track with the two vehicles standing on it.
        for (int x = -6; x < 72; x++) {
            level.setBlock(new BlockPos(x, RAIL_Y, 12), Blocks.RAIL.defaultBlockState()
                    .setValue(RailBlock.SHAPE, RailShape.EAST_WEST), Block.UPDATE_ALL);
        }
        spawn(level, ModEntityTypes.REDSTONE_TRAM, 7, 12);
        spawn(level, ModEntityTypes.CARGO_TRAM, 15, 12);

        // Every machine, front face toward the camera, cycling through the status colours.
        int x = 21;
        for (MachineType type : MachineType.values()) {
            level.setBlock(new BlockPos(x, RAIL_Y, 8), ModContent.MACHINES.get(type).defaultBlockState()
                    .setValue(MachineBlock.FACING, Direction.SOUTH)
                    .setValue(MachineBlock.STATUS, MachineStatus.values()[x % 4]), Block.UPDATE_CLIENTS);
            x++;
        }
        level.setBlock(new BlockPos(29, RAIL_Y, 8), ModContent.FACTORY_CONTROLLER.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int belt = 21; belt < 30; belt++) {
            level.setBlock(new BlockPos(belt, RAIL_Y, 10), ModContent.CONVEYOR_BELT.defaultBlockState()
                    .setValue(ConveyorBeltBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        }

        // Drive rails in every shape, alternating off and lit, each with its slope support.
        for (int rail = 0; rail < RailShape.values().length; rail++) {
            RailShape shape = RailShape.values()[rail];
            BlockPos pos = new BlockPos(3 + rail * 2, RAIL_Y, 26);
            if (shape.isSlope()) {
                Direction high = switch (shape) {
                    case ASCENDING_EAST -> Direction.EAST;
                    case ASCENDING_WEST -> Direction.WEST;
                    case ASCENDING_NORTH -> Direction.NORTH;
                    default -> Direction.SOUTH;
                };
                level.setBlock(pos.relative(high), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.setBlock(pos, ModContent.DRIVE_RAIL.defaultBlockState()
                    .setValue(DriveRailBlock.SHAPE, shape)
                    .setValue(DriveRailBlock.POWERED, rail % 2 == 0), Block.UPDATE_CLIENTS);
        }

        // The tree factory: planter, cutter and booster in a row, with the tree growing against the cutter.
        BlockPos cutter = new BlockPos(44, RAIL_Y, 14);
        placeMachine(level, cutter, MachineType.TREE_CUTTER);
        placeMachine(level, cutter.west(), MachineType.AUTO_PLANTER);
        placeMachine(level, cutter.east(), MachineType.GROWTH_BOOSTER);
        placeMachine(level, cutter.north().east(), MachineType.ITEM_COLLECTOR);
        level.setBlock(cutter.north(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cutter.north().west(), ModContent.FACTORY_CONTROLLER.defaultBlockState()
                .setValue(FactoryControllerBlock.MODE, FactoryControllerBlock.Mode.RUNNING), Block.UPDATE_ALL);
        // The display factory is not wired; it gets its 220 V the way the game tests give it.
        com.example.redstonetransportation.machine.ControllerPower.feedForTest(level, cutter.north().west(), 220.0D);
    }

    private static void placeMachine(ServerLevel level, BlockPos pos, MachineType type) {
        level.setBlock(pos, ModContent.MACHINES.get(type).defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
    }

    private static void startFactory(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int x = 43; x <= 45; x++) {
            if (!(level.getBlockEntity(new BlockPos(x, RAIL_Y, 14)) instanceof MachineBlockEntity machine)) continue;
            if (machine.type() == MachineType.AUTO_PLANTER) machine.setItem(0, new ItemStack(Items.OAK_SAPLING, 16));
            if (machine.type() == MachineType.GROWTH_BOOSTER) machine.setItem(0, new ItemStack(Items.BONE_MEAL, 64));
        }
    }

    private static void launchTram(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // Only the display tram: the one seating a passenger stands much further down the line.
        for (AbstractMinecart cart : level.getEntities(ModEntityTypes.REDSTONE_TRAM, entity -> entity.getX() < 20.0D)) {
            cart.setPos(36.5, RAIL_Y + 0.1, 12.5);
            cart.setDeltaMovement(0.25D, 0.0D, 0.0D);
        }
    }

    private static void spawn(ServerLevel level, EntityType<? extends AbstractMinecart> type, int x, int z) {
        AbstractMinecart cart = type.create(level, EntitySpawnReason.COMMAND);
        cart.setPos(x + 0.5, RAIL_Y + 0.1, z + 0.5);
        level.addFreshEntity(cart);
    }
}
