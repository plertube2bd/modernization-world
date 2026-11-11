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
    // Time off, in ticks (20 a second).
    public static final int COAL = 10 * 20;
    public static final int PARKOUR = 30 * 20;
    public static final int TARGET = 15 * 20;
    public static final int BULLSEYE = 30 * 20;
    public static final int FISH = 20 * 20;
    public static final int QUIZ = 30 * 20;
    /** Mined coal grows back after this long. */
    public static final int COAL_REGROW = 45 * 20;

    /** Players in the same room get this share of the time another takes off. */
    public static final int SHARE_DIVISOR = 10;

    /** Each minigame's key, name, and how long after it counts before it counts again. */
    public enum Game {
        COAL("coal", "석탄 캐기", 3 * 20),
        PARKOUR("parkour", "파쿠르", 3 * 60 * 20),
        TARGET("target", "과녁 맞히기", 5 * 20),
        FISH("fish", "낚시", 20 * 20),
        QUIZ("quiz", "퀴즈", 30 * 20);

        public final String key;
        public final String label;
        public final int cooldown;

        Game(String key, String label, int cooldown) {
            this.key = key;
            this.label = label;
            this.cooldown = cooldown;
        }

        public static Game byKey(String key) {
            for (Game game : values()) {
                if (game.key.equals(key)) {
                    return game;
                }
            }
            return null;
        }
    }

    /**
     * Time off for a prisoner, and a tenth of it for everyone else in the same room: playing together, the one who
     * did it gets it all and the others a share.
     */
    public static void reward(ServerPlayer player, int ticks, String why) {
        Jail.reduce(player, ticks, why);
        Rooms.Room room = Rooms.of(player);
        if (room == null) {
            return;
        }
        for (ServerPlayer other : Rooms.playersIn(player.level().getServer(), room)) {
            if (other != player) {
                Jail.reduce(other, ticks / SHARE_DIVISOR, player.getGameProfile().name() + "님의 " + why + " (1/" + SHARE_DIVISOR + ")");
            }
        }
    }

    private static final Map<BlockPos, Long> REGROW = new HashMap<>();
    private static final Map<UUID, Integer> ANSWERS = new HashMap<>();
    private static final Map<UUID, String> QUESTIONS = new HashMap<>();
    private static final Map<UUID, Map<Game, Long>> READY_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new HashMap<>();

    /** True for a player's first NPC click in a short while, so one right-click does one thing. */
    public static boolean firstClick(ServerPlayer player) {
        long now = player.level().getServer().getTickCount();
        Long last = LAST_CLICK.put(player.getUUID(), now);
        return last == null || now - last > 4;
    }

    private MiniGames() {
    }

    public static void forget(ServerPlayer player) {
        ANSWERS.remove(player.getUUID());
        QUESTIONS.remove(player.getUUID());
        READY_AT.remove(player.getUUID());
    }

    // ---- Cooldowns -----------------------------------------------------------------------------------

    /** Ticks until a minigame counts again for this player; 0 when it counts now. */
    public static int cooldownLeft(ServerPlayer player, Game game) {
        Long ready = READY_AT.getOrDefault(player.getUUID(), Map.of()).get(game);
        long now = player.level().getServer().getTickCount();
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    public static void startCooldown(ServerPlayer player, Game game) {
        READY_AT.computeIfAbsent(player.getUUID(), id -> new java.util.EnumMap<>(Game.class))
                .put(game, player.level().getServer().getTickCount() + (long) game.cooldown);
    }

    /** Says how long until the game counts again; true while it is cooling down. */
    private static boolean coolingDown(ServerPlayer player, Game game) {
        int left = cooldownLeft(player, game);
        if (left > 0) {
            player.sendOverlayMessage(Component.literal(game.label + " 쿨타임: " + Jail.seconds(Math.max(20, left)) + " 남음")
                    .withStyle(ChatFormatting.RED));
        }
        return left > 0;
    }

    // ---- Coal ----------------------------------------------------------------------------------------

    /** In jail only coal ore may be broken, and not while the coal cooldown runs. */
    public static boolean mayBreak(ServerPlayer player, Level level, BlockState state) {
        return Jail.isJail(level) && state.is(Blocks.COAL_ORE) && !coolingDown(player, Game.COAL);
    }

    public static void coalMined(ServerPlayer player, BlockPos pos) {
        reward(player, COAL, "석탄 캐기");
        startCooldown(player, Game.COAL);
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
        Rooms.Room room = Rooms.of(player);
        if (room != null && room.game() == Game.PARKOUR && player.blockPosition().below().equals(room.parkourFinish())) {
            if (!coolingDown(player, Game.PARKOUR)) {
                reward(player, PARKOUR, "파쿠르 완주");
                startCooldown(player, Game.PARKOUR);
            }
            Jail.teleport(player, (ServerLevel) player.level(), room.parkourStart(), -90.0F, 0.0F);
        }
        if (now % 10 == 0) {
            checkFish(player);
        }
        if (now % 100 == 0) {
            Jail.ensureKit(player);
        }
        if (room != null && now % 40 == 0) {
            Rooms.ensureExitNpc((ServerLevel) player.level(), room);
        }
    }

    /** Fish caught are taken away, for time off unless the fishing cooldown runs. */
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
        if (fish > 0 && !coolingDown(player, Game.FISH)) {
            reward(player, FISH * fish, "물고기 " + fish + "마리");
            startCooldown(player, Game.FISH);
        }
    }

    // ---- Targets -------------------------------------------------------------------------------------

    /** A projectile hit a target block: a prisoner's arrow in the jail earns time off, more for the centre. */
    public static void onTargetHit(Level level, BlockHitResult hit, Projectile projectile) {
        if (!Jail.isJail(level) || !(projectile.getOwner() instanceof ServerPlayer player) || !Jail.isJailed(player)) {
            return;
        }
        Rooms.Room room = Rooms.of(player);
        if (room == null || room.game() != Game.TARGET) {
            return;
        }
        if (player.getX() < room.shootingLineX()) {
            if (coolingDown(player, Game.TARGET)) {
                return;
            }
            startCooldown(player, Game.TARGET);
            boolean centre = offCentre(hit) < 1.0 / 6.0;
            reward(player, centre ? BULLSEYE : TARGET, centre ? "과녁 정중앙" : "과녁 명중");
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
        if (question == null && coolingDown(player, Game.QUIZ)) {
            player.sendSystemMessage(Component.literal("다음 문제까지 " + Jail.seconds(Math.max(20, cooldownLeft(player, Game.QUIZ))) + " 남았습니다.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
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

    public static boolean hasQuestion(ServerPlayer player) {
        return QUESTIONS.containsKey(player.getUUID());
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
        startCooldown(player, Game.QUIZ);
        if (given == expected) {
            reward(player, QUIZ, "퀴즈 정답");
        } else {
            player.sendSystemMessage(Component.literal("틀렸습니다. 정답은 " + expected + "였습니다. 30초 뒤에 독서대를 다시 눌러 새 문제를 받으세요.")
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }
}
