package com.example.jail.test;

import com.example.jail.Jail;
import com.example.jail.JailBuilder;
import com.example.jail.MiniGames;
import com.example.jail.Prisoner;
import com.example.jail.RoomMenu;
import com.example.jail.Rooms;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The game test server never loads datapack dimensions, so here the jail is built in the overworld (around 0, 64, 0,
 * away from the test structures); on a real server it has its own dimension.
 */
public final class JailGameTests {
    private static final String ARENA = "jail_test:arena";

    static {
        Jail.fallback = Level.OVERWORLD;
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void jailKeepsItemsLevelAndPlaceAndGivesThemBack(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        player.setGameMode(GameType.ADVENTURE);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        inventory.setItem(5, new ItemStack(Items.APPLE, 10));
        inventory.setItem(39, new ItemStack(Items.DIAMOND_HELMET));
        player.setExperienceLevels(7);
        Vec3 before = player.position();
        Level home = player.level();

        Jail.jail(player, 5);
        helper.assertTrue(Jail.isJail(player.level()), "not in the jail: " + player.level().dimension());
        helper.assertTrue(JailBuilder.inside(player.position()), "not in the jail yard: " + player.position());
        helper.assertTrue(player.level().getBlockState(BlockPos.containing(JailBuilder.SPAWN).below()).isSolid(), "no floor under the jail yard");
        helper.assertFalse(inventory.contains(new ItemStack(Items.DIAMOND_SWORD)), "kept the sword in jail");
        helper.assertTrue(Jail.hasKit(player), "no jail tools");
        ItemStack shirt = player.getItemBySlot(EquipmentSlot.CHEST);
        helper.assertTrue(shirt.is(Items.LEATHER_CHESTPLATE) && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS), "no prison uniform");
        helper.assertValueEqual(shirt.get(DataComponents.DYED_COLOR).rgb(), Jail.UNIFORM, "uniform colour");
        helper.assertTrue(JailBuilder.inHub(player.position()), "not in the yard");
        helper.assertValueEqual(player.gameMode.getGameModeForPlayer(), GameType.SURVIVAL, "game mode in jail");
        helper.assertValueEqual(Jail.prisoner(player).remaining, 5 * Jail.TICKS_PER_MINUTE, "sentence");

        // In jail: coal mined, levels gained.
        inventory.add(new ItemStack(Items.COAL, 5));
        player.setExperienceLevels(20);

        helper.assertTrue(Jail.release(player), "release");
        helper.assertTrue(player.level() == home, "not back in the same dimension");
        helper.assertTrue(player.position().distanceTo(before) < 0.01, "not back where jailed: " + player.position() + " vs " + before);
        helper.assertTrue(inventory.getItem(0).is(Items.DIAMOND_SWORD), "sword not back in its slot");
        helper.assertTrue(inventory.getItem(5).is(Items.APPLE) && inventory.getItem(5).getCount() == 10, "apples not back");
        helper.assertTrue(inventory.getItem(39).is(Items.DIAMOND_HELMET), "helmet not back on");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the prison uniform came out of jail");
        helper.assertFalse(Jail.holds(player, Items.COAL), "coal came out of jail");
        helper.assertFalse(Jail.holds(player, Items.WOODEN_PICKAXE), "the jail pickaxe came out of jail");
        helper.assertValueEqual(player.experienceLevel, 7, "level after jail");
        helper.assertValueEqual(player.gameMode.getGameModeForPlayer(), GameType.ADVENTURE, "game mode after jail");
        helper.assertFalse(Jail.isJailed(player), "still jailed");
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void eachMinigameTakesTimeOff(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        Jail.jail(player, 10);
        Prisoner prisoner = Jail.prisoner(player);
        ServerLevel jail = (ServerLevel) player.level();
        int expected = 10 * Jail.TICKS_PER_MINUTE;

        // Coal: only coal ore may be broken.
        Rooms.enter(player, Rooms.room(MiniGames.Game.COAL, 0));
        helper.assertTrue(Rooms.room(MiniGames.Game.COAL, 0).equals(Rooms.of(player)), "not in coal room 1");
        helper.assertTrue(MiniGames.mayBreak(player, jail, Blocks.COAL_ORE.defaultBlockState()), "coal ore may not be mined");
        helper.assertFalse(MiniGames.mayBreak(player, jail, Blocks.STONE_BRICKS.defaultBlockState()), "a jail wall may be broken");
        MiniGames.coalMined(player, Rooms.room(MiniGames.Game.COAL, 0).at(new BlockPos(0, 65, -19)));
        expected -= MiniGames.COAL;
        helper.assertValueEqual(prisoner.remaining, expected, "after a coal");
        helper.assertFalse(MiniGames.mayBreak(player, jail, Blocks.COAL_ORE.defaultBlockState()), "coal may be mined again at once");
        helper.assertValueEqual(MiniGames.cooldownLeft(player, MiniGames.Game.COAL), 3 * 20, "coal cooldown");

        // Parkour: standing on the gold block takes 30 seconds off.
        Rooms.Room parkour = Rooms.room(MiniGames.Game.PARKOUR, 0);
        Rooms.enter(player, parkour);
        helper.assertTrue(jail.getBlockState(parkour.parkourFinish()).is(Blocks.GOLD_BLOCK), "no gold block at the end of the course");
        helper.assertValueEqual(MiniGames.PARKOUR, 30 * 20, "parkour time off");
        Jail.teleport(player, jail, Vec3.atBottomCenterOf(parkour.parkourFinish().above()), 0, 0);
        MiniGames.tickPrisoner(player, 1);
        expected -= MiniGames.PARKOUR;
        helper.assertValueEqual(prisoner.remaining, expected, "after the parkour");
        helper.assertTrue(player.position().distanceTo(parkour.parkourStart()) < 0.01, "not sent back to the parkour start");
        Jail.teleport(player, jail, Vec3.atBottomCenterOf(parkour.parkourFinish().above()), 0, 0);
        MiniGames.tickPrisoner(player, 1);
        helper.assertValueEqual(prisoner.remaining, expected, "a second run during the parkour cooldown counted");
        helper.assertTrue(player.position().distanceTo(parkour.parkourStart()) < 0.01, "not sent back to the start during the cooldown");

        // Fishing: fish caught are taken away.
        Rooms.enter(player, Rooms.room(MiniGames.Game.FISH, 0));
        player.getInventory().add(new ItemStack(Items.COD, 2));
        MiniGames.checkFish(player);
        expected -= 2 * MiniGames.FISH;
        helper.assertValueEqual(prisoner.remaining, expected, "after two fish");
        helper.assertFalse(Jail.holds(player, Items.COD), "the fish were kept");
        player.getInventory().add(new ItemStack(Items.SALMON));
        MiniGames.checkFish(player);
        helper.assertValueEqual(prisoner.remaining, expected, "a fish during the fishing cooldown counted");
        helper.assertFalse(Jail.holds(player, Items.SALMON), "a fish during the cooldown was kept");

        // Quiz: a number in chat answers it; other chat is chat.
        Rooms.enter(player, Rooms.room(MiniGames.Game.QUIZ, 0));
        MiniGames.setQuestion(player, "2 + 2 = ?", 4);
        helper.assertFalse(MiniGames.answer(player, "hello"), "ordinary chat was taken as an answer");
        helper.assertTrue(MiniGames.answer(player, " 4 "), "the answer was not taken");
        expected -= MiniGames.QUIZ;
        helper.assertValueEqual(prisoner.remaining, expected, "after a right answer");
        helper.assertFalse(MiniGames.answer(player, "4"), "answered with no question");
        MiniGames.ask(player);
        helper.assertFalse(MiniGames.answer(player, "0"), "a new question during the quiz cooldown");
        MiniGames.setQuestion(player, "3 × 3 = ?", 9);
        helper.assertTrue(MiniGames.answer(player, "8"), "a wrong answer was not taken");
        helper.assertValueEqual(prisoner.remaining, expected, "a wrong answer took time off");

        // Targets: an arrow from behind the line, in the middle of a target.
        Rooms.Room range = Rooms.room(MiniGames.Game.TARGET, 0);
        Rooms.enter(player, range);
        BlockPos target = range.at(Rooms.TARGETS.get(1));
        helper.assertTrue(jail.getBlockState(target).is(Blocks.TARGET), "no target on the range");
        Arrow arrow = EntityTypes.ARROW.create(jail, EntitySpawnReason.COMMAND);
        arrow.setOwner(player);
        Vec3 middle = Vec3.atCenterOf(target).add(-0.5, 0, 0);
        MiniGames.onTargetHit(jail, new BlockHitResult(middle, Direction.WEST, target, false), arrow);
        expected -= MiniGames.BULLSEYE;
        helper.assertValueEqual(prisoner.remaining, expected, "after a bullseye");
        MiniGames.onTargetHit(jail, new BlockHitResult(middle, Direction.WEST, target, false), arrow);
        helper.assertValueEqual(prisoner.remaining, expected, "a second hit at once counted");
        Vec3 edge = Vec3.atCenterOf(target).add(-0.5, 0.4, 0.3);
        helper.assertTrue(MiniGames.offCentre(new BlockHitResult(edge, Direction.WEST, target, false)) > 1.0 / 6.0, "an edge hit is a bullseye");

        Jail.release(player);
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void prisonersAreReleasedWhenTimeRunsOut(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Vec3 before = player.position();
        Jail.jail(player, 1);
        Jail.prisoner(player).remaining = 1;
        Jail.tick(server);
        Jail.tick(server);
        helper.assertFalse(Jail.isJailed(player), "still jailed after the time ran out");
        helper.assertTrue(player.position().distanceTo(before) < 0.01, "not back where jailed");
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void prisonersSurviveASave(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.setDamageValue(12);
        Prisoner prisoner = new Prisoner("Steve", 900, 1200, Level.OVERWORLD, 1.5, 70, -3.5, 90, 10, GameType.CREATIVE,
                12, 0.5F, 300, List.of(new Prisoner.Slot(3, sword)));
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = Prisoner.CODEC.encodeStart(ops, prisoner).getOrThrow();
        Prisoner loaded = Prisoner.CODEC.parse(ops, saved).getOrThrow();
        helper.assertValueEqual(loaded.name, "Steve", "name");
        helper.assertValueEqual(loaded.remaining, 900, "time left");
        helper.assertValueEqual(loaded.gameMode, GameType.CREATIVE, "game mode");
        helper.assertValueEqual(loaded.xpLevel, 12, "level");
        helper.assertTrue(loaded.dimension == Level.OVERWORLD && loaded.z == -3.5, "place");
        helper.assertTrue(loaded.items.size() == 1 && loaded.items.get(0).slot() == 3
                && ItemStack.matches(loaded.items.get(0).stack(), sword), "items: " + loaded.items);
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void koreanCommandsJailAndRelease(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        // Every mock player has the same name, so the jail command picks this one as the nearest player.
        String name = player.getGameProfile().name();
        Vec3 before = player.position();
        run(server, String.format(java.util.Locale.ROOT, "execute positioned %.2f %.2f %.2f run 감옥 @p 3", before.x, before.y, before.z));
        helper.assertTrue(Jail.isJailed(player), "/감옥 did not jail " + name);
        helper.assertValueEqual(Jail.prisoner(player).remaining, 3 * Jail.TICKS_PER_MINUTE, "sentence from the command");
        run(server, "석방 " + name);
        helper.assertFalse(Jail.isJailed(player), "/석방 did not release " + name);
        helper.assertTrue(player.position().distanceTo(before) < 0.01, "not back where jailed after /석방");
        helper.succeed();
    }

    /** Playing together: the one who did it gets all the time off, the others in the room a tenth; other rooms nothing. */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void playersInOneRoomShareATenth(GameTestHelper helper) {
        ServerPlayer miner = mockPlayer(helper);
        ServerPlayer friend = mockPlayer(helper);
        ServerPlayer alone = mockPlayer(helper);
        for (ServerPlayer p : List.of(miner, friend, alone)) {
            Jail.jail(p, 10);
        }
        Rooms.enter(miner, Rooms.room(MiniGames.Game.COAL, 2));
        Rooms.enter(friend, Rooms.room(MiniGames.Game.COAL, 2));
        Rooms.enter(alone, Rooms.room(MiniGames.Game.COAL, 1));
        helper.assertValueEqual(Rooms.playersIn(helper.getLevel().getServer(), Rooms.room(MiniGames.Game.COAL, 2)).size(), 2, "players in coal room 3");
        int full = 10 * Jail.TICKS_PER_MINUTE;
        MiniGames.coalMined(miner, Rooms.room(MiniGames.Game.COAL, 2).at(new BlockPos(0, 65, -19)));
        helper.assertValueEqual(Jail.prisoner(miner).remaining, full - MiniGames.COAL, "the miner's time");
        helper.assertValueEqual(Jail.prisoner(friend).remaining, full - MiniGames.COAL / 10, "the friend's time");
        helper.assertValueEqual(Jail.prisoner(alone).remaining, full, "the time of one in another room");
        for (ServerPlayer p : List.of(miner, friend, alone)) {
            Jail.release(p);
        }
        helper.succeed();
    }

    /**
     * The yard has an NPC for each minigame; its menu shows the rooms and a click goes there; the room's NPC leads back.
     * NPCs appear once their chunks' entities are loaded, so this waits, in a batch of its own.
     */
    @GameTest(structure = ARENA, maxTicks = 400, environment = "jail_test:alone")
    public void npcsOpenTheRoomPicker(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        Jail.jail(player, 5);
        ServerLevel jail = (ServerLevel) player.level();
        helper.succeedWhen(() -> {
            var npcs = jail.getEntitiesOfClass(Mannequin.class, new AABB(-16, 60, -16, 16, 72, 16), v -> JailBuilder.npcKey(v) != null);
            helper.assertValueEqual(npcs.size(), MiniGames.Game.values().length, "NPCs in the yard");
            helper.assertTrue(npcs.stream().allMatch(n -> n.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)), "an NPC without the uniform");
            if (Rooms.of(player) == null) {
                // A right-click as the game sends it: with the point it hit, for the main hand (and maybe the off hand too).
                Mannequin fishing = npcs.stream().filter(n -> "fish".equals(JailBuilder.npcKey(n))).findFirst().orElseThrow();
                var at = new net.minecraft.world.phys.EntityHitResult(fishing, fishing.position().add(0, 1, 0));
                UseEntityCallback.EVENT.invoker().interact(player, jail, InteractionHand.MAIN_HAND, fishing, at);
                UseEntityCallback.EVENT.invoker().interact(player, jail, InteractionHand.OFF_HAND, fishing, at);
                helper.assertTrue(player.containerMenu instanceof RoomMenu, "no room picker: " + player.containerMenu);
                helper.assertTrue(player.containerMenu.getSlot(RoomMenu.SLOTS[1]).getItem().is(Items.COD), "no icon for fishing room 2");
                player.containerMenu.clicked(RoomMenu.SLOTS[1], 0, ContainerInput.PICKUP, player);
                helper.assertTrue(player.containerMenu == player.inventoryMenu, "the room picker stayed open");
            }
            helper.assertTrue(Rooms.room(MiniGames.Game.FISH, 1).equals(Rooms.of(player)), "the click did not go to fishing room 2: " + player.position());
            var exit = jail.getEntitiesOfClass(Mannequin.class, new AABB(BlockPos.containing(Rooms.room(MiniGames.Game.FISH, 1).spawn())).inflate(6),
                    v -> "exit".equals(JailBuilder.npcKey(v)));
            helper.assertValueEqual(exit.size(), 1, "exit NPCs in the room");
            UseEntityCallback.EVENT.invoker().interact(player, jail, InteractionHand.MAIN_HAND, exit.get(0),
                    new net.minecraft.world.phys.EntityHitResult(exit.get(0), exit.get(0).position().add(0, 1, 0)));
            helper.assertTrue(JailBuilder.inHub(player.position()), "the exit NPC did not lead to the yard: " + player.position());
            Jail.release(player);
        });
    }

    /** One right-click sends a packet for each hand: the quiz is asked once, for the main hand. */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void theQuizIsAskedOncePerClick(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        Jail.jail(player, 5);
        Rooms.Room quiz = Rooms.room(MiniGames.Game.QUIZ, 2);
        Rooms.enter(player, quiz);
        BlockPos lectern = quiz.at(Rooms.LECTERNS.get(0));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(lectern), Direction.WEST, lectern, false);
        UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, hit);
        helper.assertFalse(MiniGames.hasQuestion(player), "the off hand asked a question");
        UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(MiniGames.hasQuestion(player), "the main hand asked no question");
        Jail.release(player);
        helper.succeed();
    }

    /** A yard or room whose floor has gone missing is rebuilt while a prisoner is in it, and they are put back on it. */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void aMissingFloorIsRebuilt(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        Jail.jail(player, 5);
        ServerLevel jail = (ServerLevel) player.level();
        Rooms.Room room = Rooms.room(MiniGames.Game.TARGET, 2);
        Rooms.enter(player, room);
        BlockPos o = room.origin();
        JailBuilder.fill(jail, o.getX() - 31, JailBuilder.FLOOR - 1, o.getZ() - 21, o.getX() + 31, JailBuilder.FLOOR, o.getZ() + 21,
                Blocks.AIR.defaultBlockState());
        helper.assertFalse(JailBuilder.floorIntact(jail, o, Rooms.HX, Rooms.HZ), "the floor was not taken away");
        Jail.repairAround(player);
        helper.assertTrue(JailBuilder.floorIntact(jail, o, Rooms.HX, Rooms.HZ), "the room's floor was not rebuilt");
        helper.assertTrue(player.position().distanceTo(room.spawn()) < 0.01, "not put back at the room's start");
        helper.assertTrue(jail.getBlockState(BlockPos.containing(room.spawn()).below()).isSolid(), "nothing to stand on");
        Jail.release(player);
        helper.succeed();
    }

    /** A mock player standing in the test's own space (they start at 0, 0, 0, which is the jail here). */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return player;
    }

    private static void run(MinecraftServer server, String command) {
        try {
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new AssertionError("/" + command + ": " + failure.getMessage(), failure);
        }
    }
}
