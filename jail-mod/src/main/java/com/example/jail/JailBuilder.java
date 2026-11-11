package com.example.jail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The jail, built of vanilla blocks in the empty jail dimension around (0, 64, 0): a walled yard 41 x 41 with an
 * invisible ceiling, and an area for each minigame - a coal face (west), a parkour course (north), a shooting range
 * (east), a fishing pond (south-east) and a quiz lectern (south-west). Rebuilt when VERSION goes up.
 */
public final class JailBuilder {
    public static final int VERSION = 1;
    public static final int FLOOR = 63;
    public static final int HALF = 21;
    public static final int TOP = 77;
    public static final Vec3 SPAWN = new Vec3(0.5, 64, 0.5);

    /** The coal face: x = -19, z from -12 to 8, y from 64 to 69. */
    public static final int COAL_X = -19, COAL_Z0 = -12, COAL_Z1 = 8, COAL_Y0 = 64, COAL_Y1 = 69;
    /** Parkour: start on the emerald pad, reach the gold block. */
    public static final BlockPos PARKOUR_PAD = new BlockPos(-15, FLOOR, -17);
    public static final Vec3 PARKOUR_START = new Vec3(-14.5, 64, -15.5);
    public static final List<BlockPos> PARKOUR = List.of(
            new BlockPos(-13, 64, -17), new BlockPos(-11, 65, -17), new BlockPos(-9, 65, -15), new BlockPos(-7, 66, -15),
            new BlockPos(-5, 66, -17), new BlockPos(-3, 67, -17), new BlockPos(-1, 67, -15), new BlockPos(1, 68, -15),
            new BlockPos(3, 68, -17), new BlockPos(5, 69, -17), new BlockPos(7, 69, -15), new BlockPos(9, 70, -15),
            new BlockPos(11, 70, -17));
    public static final BlockPos PARKOUR_FINISH = new BlockPos(13, 70, -17);
    /** Shooting range: targets on the east wall, shoot from behind the red line at x = 8. */
    public static final List<BlockPos> TARGETS = List.of(new BlockPos(19, 66, -6), new BlockPos(19, 66, 0), new BlockPos(19, 66, 6),
            new BlockPos(19, 68, -3), new BlockPos(19, 68, 3));
    public static final int SHOOTING_LINE_X = 8;
    /** Fishing pond: water from x 8 to 17, z 10 to 18, y 60 to 63. */
    public static final int POND_X0 = 8, POND_X1 = 17, POND_Z0 = 10, POND_Z1 = 18;
    public static final BlockPos LECTERN = new BlockPos(-8, 64, 14);

    private JailBuilder() {
    }

    /** Whether a position is inside the jail's walls. */
    public static boolean inside(Vec3 pos) {
        return Math.abs(pos.x) < HALF && Math.abs(pos.z) < HALF && pos.y > FLOOR - 6 && pos.y < TOP;
    }

    public static void ensureBuilt(ServerLevel level) {
        JailData data = JailData.get(level.getServer());
        if (data.built() < VERSION) {
            build(level);
            data.setBuilt(VERSION);
        }
    }

    public static void build(ServerLevel level) {
        int h = HALF;
        fill(level, -h - 1, FLOOR - 6, -h - 1, h + 1, TOP + 1, h + 1, Blocks.AIR.defaultBlockState());
        // Floor, with a lamp every six blocks, and stone under it.
        fill(level, -h, FLOOR - 1, -h, h, FLOOR - 1, h, Blocks.STONE.defaultBlockState());
        for (int x = -h; x <= h; x++) {
            for (int z = -h; z <= h; z++) {
                boolean lamp = Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3;
                set(level, x, FLOOR, z, lamp ? Blocks.SEA_LANTERN : (x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.ANDESITE);
            }
        }
        // Walls with barred windows, an invisible ceiling.
        for (int y = FLOOR; y <= TOP; y++) {
            for (int i = -h; i <= h; i++) {
                boolean window = y >= 66 && y <= 67 && Math.floorMod(i, 4) == 0;
                Block wall = window ? Blocks.IRON_BARS : y == 70 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS;
                set(level, i, y, -h, wall);
                set(level, i, y, h, wall);
                set(level, -h, y, i, wall);
                set(level, h, y, i, wall);
            }
        }
        fill(level, -h, TOP, -h, h, TOP, h, Blocks.BARRIER.defaultBlockState());

        // Coal face: stone backed by the wall, about half of it coal ore.
        for (int z = COAL_Z0; z <= COAL_Z1; z++) {
            for (int y = COAL_Y0; y <= COAL_Y1; y++) {
                set(level, COAL_X - 1, y, z, Blocks.STONE);
                set(level, COAL_X, y, z, hash(z, y) % 100 < 50 ? Blocks.COAL_ORE : Blocks.STONE);
            }
        }

        // Parkour: an emerald start pad, coloured steps rising to a gold block.
        set(level, PARKOUR_PAD.getX(), PARKOUR_PAD.getY(), PARKOUR_PAD.getZ(), Blocks.EMERALD_BLOCK);
        Block[] steps = {Blocks.CONCRETE.lime(), Blocks.CONCRETE.yellow(), Blocks.CONCRETE.orange(), Blocks.CONCRETE.lightBlue(), Blocks.CONCRETE.magenta()};
        for (int i = 0; i < PARKOUR.size(); i++) {
            BlockPos p = PARKOUR.get(i);
            set(level, p.getX(), p.getY(), p.getZ(), steps[i % steps.length]);
        }
        set(level, PARKOUR_FINISH.getX(), PARKOUR_FINISH.getY(), PARKOUR_FINISH.getZ(), Blocks.GOLD_BLOCK);

        // Shooting range: targets on the east wall, a red line to shoot from.
        for (BlockPos t : TARGETS) {
            set(level, t.getX() + 1, t.getY(), t.getZ(), Blocks.STONE_BRICKS);
            set(level, t.getX(), t.getY(), t.getZ(), Blocks.TARGET);
        }
        for (int z = -8; z <= 8; z++) {
            set(level, SHOOTING_LINE_X, FLOOR, z, Blocks.CONCRETE.red());
        }

        // Fishing pond: a stone basin filled with water.
        fill(level, POND_X0 - 1, FLOOR - 5, POND_Z0 - 1, POND_X1 + 1, FLOOR - 1, POND_Z1 + 1, Blocks.STONE.defaultBlockState());
        fill(level, POND_X0, FLOOR - 3, POND_Z0, POND_X1, FLOOR, POND_Z1, Blocks.WATER.defaultBlockState());
        fill(level, POND_X0, FLOOR - 4, POND_Z0, POND_X1, FLOOR - 4, POND_Z1, Blocks.SAND.defaultBlockState());

        // Quiz lectern, facing the yard.
        level.setBlock(LECTERN, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);

        // Signs.
        sign(level, new BlockPos(1, 64, 2), "§l감옥", "미니게임으로", "남은 시간을", "줄이세요!");
        sign(level, new BlockPos(-16, 64, -2), "§l석탄 캐기", "석탄 1개당", "-10초", "곡괭이로 캐기");
        sign(level, new BlockPos(-15, 64, -13), "§l파쿠르", "금 블록까지", "가면 -1분", "에메랄드에서 시작");
        sign(level, new BlockPos(6, 64, -10), "§l과녁 맞히기", "빨간 선 뒤에서", "맞히면 -15초", "정중앙 -30초");
        sign(level, new BlockPos(6, 64, 13), "§l낚시", "물고기 1마리당", "-20초", "");
        sign(level, new BlockPos(-8, 64, 11), "§l퀴즈", "독서대 우클릭", "채팅으로 답하면", "-30초");
    }

    /** A standing sign at pos facing the yard's centre, with up to four lines. */
    private static void sign(ServerLevel level, BlockPos pos, String... lines) {
        double angle = Math.atan2(pos.getX() - SPAWN.x, SPAWN.z - pos.getZ());
        int rotation = Math.floorMod((int) Math.round(angle / (2 * Math.PI) * 16), 16);
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            Component[] text = new Component[4];
            for (int i = 0; i < 4; i++) {
                text[i] = Component.literal(i < lines.length ? lines[i] : "");
            }
            sign.setText(new SignText(List.of(text), List.of(text), DyeColor.BLACK, false), SignTextSlot.FRONT);
            sign.setWaxed(true);
        }
    }

    private static int hash(int a, int b) {
        int n = a * 73856093 ^ b * 19349663;
        return Math.floorMod(n ^ (n >>> 13), 100_003);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
