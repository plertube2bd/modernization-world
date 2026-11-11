package com.example.jail;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The five ways to shorten a sentence: mining coal, finishing the parkour course, hitting the targets, catching fish
 * and answering the quiz at the lectern.
 */
public final class MiniGames {
    public static final int COAL = 10 * 20;
    public static final int PARKOUR = 60 * 20;
    public static final int TARGET = 15 * 20;
    public static final int BULLSEYE = 30 * 20;
    public static final int FISH = 20 * 20;
    public static final int QUIZ = 30 * 20;
    /** Mined coal grows back after this long. */
    public static final int COAL_REGROW = 30 * 20;
    /** At most one target hit counts every this many ticks. */
    public static final int TARGET_COOLDOWN = 40;

    private static final Map<BlockPos, Long> REGROW = new HashMap<>();
    private static final Map<UUID, Integer> ANSWERS = new HashMap<>();
    private static final Map<UUID, String> QUESTIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_TARGET = new HashMap<>();

    private MiniGames() {
    }

    public static void forget(ServerPlayer player) {
        ANSWERS.remove(player.getUUID());
        QUESTIONS.remove(player.getUUID());
        LAST_TARGET.remove(player.getUUID());
    }

    // ---- Coal ----------------------------------------------------------------------------------------

    /** In jail only coal ore may be broken. */
    public static boolean mayBreak(ServerPlayer player, Level level, BlockState state) {
        return Jail.isJail(level) && state.is(Blocks.COAL_ORE);
    }

    public static void coalMined(ServerPlayer player, BlockPos pos) {
        Jail.reduce(player, COAL, "석탄 캐기");
        REGROW.put(pos.immutable(), player.level().getServer().getTickCount() + (long) COAL_REGROW);
    }

    /** Mined coal grows back. */
    public static void tickWorld(MinecraftServer server) {
        if (REGROW.isEmpty()) {
            return;
        }
        ServerLevel jail = Jail.level(server);
        long now = server.getTickCount();
        for (Iterator<Map.Entry<BlockPos, Long>> it = REGROW.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (entry.getValue() <= now) {
                if (jail != null && jail.getBlockState(entry.getKey()).isAir()) {
                    jail.setBlock(entry.getKey(), Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_ALL);
                }
                it.remove();
            }
        }
    }

    // ---- Per prisoner, every tick: parkour finish and fish caught ------------------------------------

    public static void tickPrisoner(ServerPlayer player, long now) {
        if (!Jail.isJail(player.level())) {
            return;
        }
        if (player.blockPosition().below().equals(JailBuilder.PARKOUR_FINISH)) {
            Jail.reduce(player, PARKOUR, "파쿠르 완주");
            Jail.teleport(player, (ServerLevel) player.level(), JailBuilder.PARKOUR_START, 180.0F, 0.0F);
        }
        if (now % 10 == 0) {
            checkFish(player);
        }
        if (now % 100 == 0 && !Jail.hasKit(player)) {
            Jail.giveKit(player);
        }
    }

    /** Fish caught are taken away for time off. */
    public static void checkFish(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int fish = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ItemTags.FISHES)) {
                fish += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (fish > 0) {
            Jail.reduce(player, FISH * fish, "물고기 " + fish + "마리");
        }
    }

    // ---- Targets -------------------------------------------------------------------------------------

    /** A projectile hit a target block: a prisoner's arrow in the jail earns time off, more for the centre. */
    public static void onTargetHit(Level level, BlockHitResult hit, Projectile projectile) {
        if (!Jail.isJail(level) || !(projectile.getOwner() instanceof ServerPlayer player) || !Jail.isJailed(player)) {
            return;
        }
        if (player.getX() < JailBuilder.SHOOTING_LINE_X + 0.5) {
            long now = level.getServer().getTickCount();
            Long last = LAST_TARGET.get(player.getUUID());
            if (last != null && now - last < TARGET_COOLDOWN) {
                return;
            }
            LAST_TARGET.put(player.getUUID(), now);
            boolean centre = offCentre(hit) < 1.0 / 6.0;
            Jail.reduce(player, centre ? BULLSEYE : TARGET, centre ? "과녁 정중앙" : "과녁 명중");
        } else {
            player.sendOverlayMessage(Component.literal("빨간 선 뒤에서 쏴야 합니다!").withStyle(ChatFormatting.RED));
        }
    }

    /** How far from the middle of the face a hit landed, 0 in the centre to 0.5 at the edge. */
    public static double offCentre(BlockHitResult hit) {
        Vec3 at = hit.getLocation().subtract(Vec3.atCenterOf(hit.getBlockPos()));
        Direction.Axis axis = hit.getDirection().getAxis();
        double a = axis == Direction.Axis.X ? at.y : at.x;
        double b = axis == Direction.Axis.Z ? at.y : at.z;
        return Math.max(Math.abs(a), Math.abs(b));
    }

    // ---- Quiz ----------------------------------------------------------------------------------------

    /** Right-clicking the lectern: a sum to answer in chat. */
    public static void ask(ServerPlayer player) {
        String question = QUESTIONS.get(player.getUUID());
        if (question == null) {
            RandomSource random = player.getRandom();
            int a, b, answer;
            String op;
            switch (random.nextInt(3)) {
                case 0 -> { a = 10 + random.nextInt(90); b = 10 + random.nextInt(90); answer = a + b; op = "+"; }
                case 1 -> { a = 20 + random.nextInt(80); b = 1 + random.nextInt(a); answer = a - b; op = "-"; }
                default -> { a = 2 + random.nextInt(11); b = 2 + random.nextInt(11); answer = a * b; op = "×"; }
            }
            question = a + " " + op + " " + b + " = ?";
            setQuestion(player, question, answer);
        }
        player.sendSystemMessage(Component.literal("퀴즈: " + question + "  (채팅창에 숫자로 답하세요)").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static void setQuestion(ServerPlayer player, String question, int answer) {
        QUESTIONS.put(player.getUUID(), question);
        ANSWERS.put(player.getUUID(), answer);
    }

    /** A prisoner with a question waiting who chats a number answers it; returns whether the message was the answer. */
    public static boolean answer(ServerPlayer player, String message) {
        Integer expected = ANSWERS.get(player.getUUID());
        if (expected == null || !Jail.isJailed(player)) {
            return false;
        }
        int given;
        try {
            given = Integer.parseInt(message.trim());
        } catch (NumberFormatException notANumber) {
            return false;
        }
        ANSWERS.remove(player.getUUID());
        QUESTIONS.remove(player.getUUID());
        if (given == expected) {
            Jail.reduce(player, QUIZ, "퀴즈 정답");
        } else {
            player.sendSystemMessage(Component.literal("틀렸습니다. 정답은 " + expected + "였습니다. 독서대를 다시 눌러 새 문제를 받으세요.")
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }
}
