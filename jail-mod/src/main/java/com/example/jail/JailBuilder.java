package com.example.jail;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The jail's yard: a walled hall around (0, 64, 0) in the jail dimension where prisoners arrive, with one NPC for each
 * minigame (right-click to choose a room). Also the block-placing helpers the rooms are built with.
 */
public final class JailBuilder {
    /** Bumped when the yard changes; it is then rebuilt, and every room rebuilt when next entered. */
    public static final int VERSION = 2;
    public static final int FLOOR = 63;
    public static final int HUB_HALF = 15;
    public static final int TOP = 78;
    public static final Vec3 SPAWN = new Vec3(0.5, 64, 4.5);
    /** Where each minigame's NPC stands in the yard, in the order of {@link MiniGames.Game}. */
    public static final List<BlockPos> NPCS = List.of(new BlockPos(-9, 64, -6), new BlockPos(-5, 64, -10), new BlockPos(0, 64, -12),
            new BlockPos(5, 64, -10), new BlockPos(9, 64, -6));
    public static final String NPC_TAG = "jail_npc";

    private JailBuilder() {
    }

    /** Whether a position is in the yard. */
    public static boolean inHub(Vec3 pos) {
        return Math.abs(pos.x) < HUB_HALF && Math.abs(pos.z) < HUB_HALF && pos.y > FLOOR - 6 && pos.y < TOP;
    }

    /** Whether a position is anywhere a prisoner may be: the yard or a room. */
    public static boolean inside(Vec3 pos) {
        return inHub(pos) || Rooms.at(pos) != null;
    }

    public static void ensureBuilt(ServerLevel level) {
        JailData data = JailData.get(level.getServer());
        if (data.built() < VERSION) {
            build(level);
            data.setBuilt(VERSION);
        }
        // The yard stays loaded, so its NPCs are always there.
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    /** Puts the yard's NPCs back if missing (spawned once their chunks' entities are loaded; extra copies removed). */
    public static void ensureNpcs(ServerLevel level) {
        for (MiniGames.Game game : MiniGames.Game.values()) {
            ensureNpc(level, NPCS.get(game.ordinal()), game.label + " (우클릭)", game.key, SPAWN);
        }
    }

    public static void ensureNpc(ServerLevel level, BlockPos at, String name, String key, Vec3 looking) {
        if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(at))) {
            return;
        }
        List<Villager> found = level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(1.5), v -> key.equals(npcKey(v)));
        if (found.isEmpty()) {
            npc(level, at, name, key, looking);
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).discard();
        }
    }

    public static void build(ServerLevel level) {
        int h = HUB_HALF;
        // The first version's yard was 43 wide: clear all of it.
        fill(level, -23, FLOOR - 6, -23, 23, TOP + 1, 23, Blocks.AIR.defaultBlockState());
        hall(level, BlockPos.ZERO, h, h);
        sign(level, new BlockPos(0, 64, 1), SPAWN, "§l감옥", "NPC를 우클릭해", "미니게임 방을", "고르세요");
        sign(level, new BlockPos(-3, 64, 2), SPAWN, "같은 방에서", "같이 하면", "남이 줄인 시간의", "1/10도 줄어요");
    }

    /**
     * A walled, lit hall centred on origin (x from -hx to hx, z from -hz to hz) with an invisible ceiling. Earlier blocks in
     * its space are cleared.
     */
    public static void hall(ServerLevel level, BlockPos origin, int hx, int hz) {
        int ox = origin.getX(), oz = origin.getZ();
        fill(level, ox - hx, FLOOR - 6, oz - hz, ox + hx, TOP + 1, oz + hz, Blocks.AIR.defaultBlockState());
        fill(level, ox - hx, FLOOR - 1, oz - hz, ox + hx, FLOOR - 1, oz + hz, Blocks.STONE.defaultBlockState());
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                boolean lamp = Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3;
                set(level, ox + x, FLOOR, oz + z, lamp ? Blocks.SEA_LANTERN : (x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.ANDESITE);
            }
        }
        for (int y = FLOOR; y < TOP; y++) {
            for (int x = -hx; x <= hx; x++) {
                Block wall = wall(y, x);
                set(level, ox + x, y, oz - hz, wall);
                set(level, ox + x, y, oz + hz, wall);
            }
            for (int z = -hz; z <= hz; z++) {
                Block wall = wall(y, z);
                set(level, ox - hx, y, oz + z, wall);
                set(level, ox + hx, y, oz + z, wall);
            }
        }
        fill(level, ox - hx, TOP, oz - hz, ox + hx, TOP, oz + hz, Blocks.BARRIER.defaultBlockState());
    }

    private static Block wall(int y, int along) {
        boolean window = y >= 66 && y <= 67 && Math.floorMod(along, 4) == 0;
        return window ? Blocks.IRON_BARS : y == 70 ? Blocks.CHISELED_STONE_BRICKS : y % 9 == 0 ? Blocks.GLOWSTONE : Blocks.STONE_BRICKS;
    }

    /** A villager that stands still, cannot be hurt, and shows its name; replaces any NPC already standing there. */
    public static void npc(ServerLevel level, BlockPos at, String name, String key) {
        npc(level, at, name, key, SPAWN);
    }

    /** As above, looking towards a point. */
    public static void npc(ServerLevel level, BlockPos at, String name, String key, Vec3 looking) {
        AABB around = new AABB(at).inflate(1.5);
        level.getEntitiesOfClass(Villager.class, around, v -> v.entityTags().contains(NPC_TAG)).forEach(v -> v.discard());
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) {
            return;
        }
        Vec3 pos = Vec3.atBottomCenterOf(at);
        double yaw = Math.toDegrees(Math.atan2(-(looking.x - pos.x), looking.z - pos.z));
        villager.snapTo(pos.x, pos.y, pos.z, (float) yaw, 0.0F);
        villager.setYHeadRot((float) yaw);
        villager.setNoAi(true);
        villager.setSilent(true);
        villager.setPersistenceRequired();
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.addTag(NPC_TAG);
        villager.addTag(NPC_TAG + ":" + key);
        level.addFreshEntity(villager);
    }

    /** The key of an NPC ("coal", "exit"...), or null if the entity is not a jail NPC. */
    public static String npcKey(net.minecraft.world.entity.Entity entity) {
        if (!entity.entityTags().contains(NPC_TAG)) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(NPC_TAG + ":")) {
                return tag.substring(NPC_TAG.length() + 1);
            }
        }
        return null;
    }

    /** A standing sign at pos facing the point it is read from, with up to four lines. */
    public static void sign(ServerLevel level, BlockPos pos, Vec3 facing, String... lines) {
        double angle = Math.atan2(pos.getX() + 0.5 - facing.x, facing.z - pos.getZ() - 0.5);
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

    public static int hash(int a, int b) {
        int n = a * 73856093 ^ b * 19349663;
        return Math.floorMod(n ^ (n >>> 13), 100_003);
    }

    public static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
