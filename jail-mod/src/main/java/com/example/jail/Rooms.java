package com.example.jail;

import com.example.jail.MiniGames.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Each minigame has a few rooms of its own, far apart in the jail dimension, so prisoners can play together (pick a
 * room someone is in) or alone (pick an empty one). A room is a walled hall 63 x 43 with the game in it, a sign and an
 * NPC that leads back to the yard. Rooms are built the first time someone enters them.
 */
public final class Rooms {
    public static final int PER_GAME = 3;
    public static final int HX = 31, HZ = 21;

    /** The parkour course, relative to the room's origin (y absolute): 4-block jumps, diagonals, one 45-strafe jump. */
    public static final List<BlockPos> PARKOUR = List.of(
            new BlockPos(-21, 64, 0), new BlockPos(-17, 64, 0), new BlockPos(-14, 64, 3), new BlockPos(-10, 64, 3),
            new BlockPos(-7, 65, 3), new BlockPos(-3, 65, 3), new BlockPos(0, 65, 0),
            new BlockPos(5, 65, -1), new BlockPos(5, 65, 0), new BlockPos(5, 65, 1),
            new BlockPos(8, 66, 0), new BlockPos(12, 66, 0), new BlockPos(15, 66, -3), new BlockPos(20, 65, -3));
    public static final BlockPos PARKOUR_FINISH = new BlockPos(24, 65, -3);
    public static final BlockPos PARKOUR_PAD = new BlockPos(-24, JailBuilder.FLOOR, 0);
    public static final int SHOOTING_LINE = -8;
    public static final List<BlockPos> TARGETS = List.of(
            new BlockPos(29, 65, -15), new BlockPos(29, 68, -9), new BlockPos(29, 65, -3),
            new BlockPos(29, 68, 3), new BlockPos(29, 65, 9), new BlockPos(29, 68, 15));
    public static final List<BlockPos> LECTERNS = List.of(
            new BlockPos(0, 64, -8), new BlockPos(0, 64, -4), new BlockPos(0, 64, 0), new BlockPos(0, 64, 4), new BlockPos(0, 64, 8));

    /** One room: a minigame and which of its rooms (0, 1, 2). */
    public record Room(Game game, int index) {
        public BlockPos origin() {
            return new BlockPos(1000 * (game.ordinal() + 1), 64, 300 * index);
        }

        public String key() {
            return game.key + "_" + index;
        }

        public String title() {
            return game.label + " 방 " + (index + 1);
        }

        public Vec3 spawn() {
            return Vec3.atBottomCenterOf(origin().offset(-26, 0, 0));
        }

        /** A position relative to the room's origin (y unchanged). */
        public BlockPos at(BlockPos relative) {
            return new BlockPos(origin().getX() + relative.getX(), relative.getY(), origin().getZ() + relative.getZ());
        }

        public boolean contains(Vec3 pos) {
            BlockPos o = origin();
            return Math.abs(pos.x - o.getX() - 0.5) < HX && Math.abs(pos.z - o.getZ() - 0.5) < HZ
                    && pos.y > JailBuilder.FLOOR - 6 && pos.y < JailBuilder.TOP;
        }

        public BlockPos parkourFinish() {
            return at(PARKOUR_FINISH);
        }

        public Vec3 parkourStart() {
            return Vec3.atBottomCenterOf(at(PARKOUR_PAD).above());
        }

        public double shootingLineX() {
            return origin().getX() + SHOOTING_LINE + 0.5;
        }
    }

    private Rooms() {
    }

    public static Room room(Game game, int index) {
        return new Room(game, index);
    }

    public static List<Room> of(Game game) {
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < PER_GAME; i++) {
            rooms.add(new Room(game, i));
        }
        return rooms;
    }

    /** The room a position is in, or null. */
    public static @Nullable Room at(Vec3 pos) {
        for (Game game : Game.values()) {
            for (int i = 0; i < PER_GAME; i++) {
                Room room = new Room(game, i);
                if (room.contains(pos)) {
                    return room;
                }
            }
        }
        return null;
    }

    /** The room a prisoner is in, or null if they are in the yard (or not in jail). */
    public static @Nullable Room of(ServerPlayer player) {
        return Jail.isJail(player.level()) ? at(player.position()) : null;
    }

    /** The prisoners in a room now. */
    public static List<ServerPlayer> playersIn(MinecraftServer server, Room room) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> Jail.isJailed(p) && room.equals(of(p)))
                .collect(Collectors.toList());
    }

    /** Takes a prisoner into a room, building it first if need be. */
    public static void enter(ServerPlayer player, Room room) {
        ServerLevel jail = Jail.level(player.level().getServer());
        if (jail == null) {
            return;
        }
        ensureBuilt(jail, room);
        List<String> others = playersIn(player.level().getServer(), room).stream()
                .filter(p -> p != player).map(p -> p.getGameProfile().name()).toList();
        Jail.teleport(player, jail, room.spawn(), -90.0F, 0.0F);
        player.sendSystemMessage(Component.literal(room.title() + "에 들어왔습니다. "
                + (others.isEmpty() ? "혼자 합니다." : "같이 있는 사람: " + String.join(", ", others)))
                .withStyle(ChatFormatting.GOLD));
    }

    /** Puts the room's NPC that leads back to the yard there if missing. */
    public static void ensureExitNpc(ServerLevel level, Room room) {
        JailBuilder.ensureNpc(level, exitNpc(room), "마당으로 돌아가기 (우클릭)", "exit", room.spawn().add(2, 0, 0));
    }

    private static BlockPos exitNpc(Room room) {
        return room.at(new BlockPos(-28, 64, -4));
    }

    /** Rebuilds a room if its floor has gone missing; returns whether it had to. */
    public static boolean repair(ServerLevel level, Room room) {
        if (JailBuilder.floorIntact(level, room.origin(), HX, HZ)) {
            return false;
        }
        build(level, room);
        return true;
    }

    public static void ensureBuilt(ServerLevel level, Room room) {
        JailData data = JailData.get(level.getServer());
        String built = room.key() + "@" + JailBuilder.VERSION;
        if (!data.roomBuilt(built)) {
            build(level, room);
            data.setRoomBuilt(built);
        }
        // The chunk the room's NPC stands in stays loaded, so it is always there.
        BlockPos npc = exitNpc(room);
        level.setChunkForced(npc.getX() >> 4, npc.getZ() >> 4, true);
        ensureExitNpc(level, room);
    }

    private static void build(ServerLevel level, Room room) {
        BlockPos o = room.origin();
        int ox = o.getX(), oz = o.getZ(), floor = JailBuilder.FLOOR;
        JailBuilder.hall(level, o, HX, HZ);
        Vec3 spawn = room.spawn();
        switch (room.game()) {
            case COAL -> {
                // Coal faces along both long walls.
                for (int side : new int[] {-1, 1}) {
                    int face = oz + side * (HZ - 2);
                    for (int x = -20; x <= 28; x++) {
                        for (int y = 64; y <= 69; y++) {
                            JailBuilder.set(level, ox + x, y, face + side, Blocks.STONE);
                            JailBuilder.set(level, ox + x, y, face, JailBuilder.hash(x * side, y) % 100 < 50 ? Blocks.COAL_ORE : Blocks.STONE);
                        }
                    }
                }
                JailBuilder.sign(level, room.at(new BlockPos(-24, 64, -3)), spawn, "§l석탄 캐기", "석탄 1개 -10초", "3초마다 1개", "45초 뒤 다시 생김");
            }
            case PARKOUR -> {
                BlockPos pad = room.at(PARKOUR_PAD);
                JailBuilder.set(level, pad.getX(), pad.getY(), pad.getZ(), Blocks.EMERALD_BLOCK);
                Block[] steps = {Blocks.CONCRETE.lime(), Blocks.CONCRETE.yellow(), Blocks.CONCRETE.orange(), Blocks.CONCRETE.lightBlue(), Blocks.CONCRETE.magenta()};
                for (int i = 0; i < PARKOUR.size(); i++) {
                    BlockPos p = room.at(PARKOUR.get(i));
                    JailBuilder.set(level, p.getX(), p.getY(), p.getZ(), steps[i % steps.length]);
                }
                BlockPos finish = room.parkourFinish();
                JailBuilder.set(level, finish.getX(), finish.getY(), finish.getZ(), Blocks.GOLD_BLOCK);
                JailBuilder.sign(level, room.at(new BlockPos(-24, 64, -3)), spawn, "§l파쿠르", "에메랄드에서 시작", "금 블록까지 -30초", "쿨타임 3분");
                JailBuilder.sign(level, room.at(new BlockPos(-15, 64, -2)), spawn, "4칸 점프", "", "", "");
                JailBuilder.sign(level, room.at(new BlockPos(2, 64, -3)), spawn, "§l45 스트레이프", "5칸 점프", "", "");
            }
            case TARGET -> {
                for (BlockPos rel : TARGETS) {
                    BlockPos t = room.at(rel);
                    JailBuilder.set(level, t.getX() + 1, t.getY(), t.getZ(), Blocks.STONE_BRICKS);
                    JailBuilder.set(level, t.getX(), t.getY(), t.getZ(), Blocks.TARGET);
                }
                for (int z = -18; z <= 18; z++) {
                    JailBuilder.set(level, ox + SHOOTING_LINE, floor, oz + z, Blocks.CONCRETE.red());
                }
                JailBuilder.sign(level, room.at(new BlockPos(-24, 64, -3)), spawn, "§l과녁 맞히기", "빨간 선 뒤에서", "-15초, 정중앙 -30초", "쿨타임 5초");
            }
            case FISH -> {
                JailBuilder.fill(level, ox - 15, floor - 5, oz - 16, ox + 28, floor - 1, oz + 16, Blocks.STONE.defaultBlockState());
                JailBuilder.fill(level, ox - 14, floor - 3, oz - 15, ox + 27, floor, oz + 15, Blocks.WATER.defaultBlockState());
                JailBuilder.fill(level, ox - 14, floor - 4, oz - 15, ox + 27, floor - 4, oz + 15, Blocks.SAND.defaultBlockState());
                JailBuilder.sign(level, room.at(new BlockPos(-24, 64, -3)), spawn, "§l낚시", "물고기 1마리 -20초", "쿨타임 20초", "");
            }
            case QUIZ -> {
                for (BlockPos rel : LECTERNS) {
                    level.setBlock(room.at(rel), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.WEST), Block.UPDATE_CLIENTS);
                }
                JailBuilder.sign(level, room.at(new BlockPos(-24, 64, -3)), spawn, "§l퀴즈", "독서대 우클릭", "채팅으로 답 -30초", "답한 뒤 30초 대기");
            }
        }
    }
}
