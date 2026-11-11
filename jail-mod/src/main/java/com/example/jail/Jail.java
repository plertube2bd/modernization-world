package com.example.jail;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Sending players to jail and back, and the sentence counting down while they are online. */
public final class Jail {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(JailMod.ID, "jail"));
    public static final int TICKS_PER_MINUTE = 20 * 60;

    private Jail() {
    }

    /**
     * Where the jail stands when the jail dimension does not exist: set by the game tests only, whose server never loads
     * datapack dimensions (a real server does).
     */
    public static @Nullable ResourceKey<Level> fallback;

    public static @Nullable ServerLevel level(MinecraftServer server) {
        ServerLevel jail = server.getLevel(DIMENSION);
        return jail == null && fallback != null ? server.getLevel(fallback) : jail;
    }

    /** Whether a level is the jail's. */
    public static boolean isJail(Level level) {
        return level instanceof ServerLevel server && server == level(server.getServer());
    }

    public static @Nullable Prisoner prisoner(ServerPlayer player) {
        return JailData.get(server(player)).get(player.getUUID());
    }

    public static boolean isJailed(ServerPlayer player) {
        return prisoner(player) != null;
    }

    private static MinecraftServer server(ServerPlayer player) {
        return player.level().getServer();
    }

    /**
     * Sends a player to jail for some minutes: their items, level, place and game mode are kept to give back, they
     * arrive empty-handed in the jail yard with the jail's tools. Jailing someone already in jail sets their time anew.
     */
    public static void jail(ServerPlayer player, int minutes) {
        MinecraftServer server = server(player);
        ServerLevel jail = level(server);
        if (jail == null) {
            throw new IllegalStateException("the jail dimension (" + DIMENSION + ") is missing");
        }
        JailBuilder.ensureBuilt(jail);
        JailData data = JailData.get(server);
        int ticks = minutes * TICKS_PER_MINUTE;
        Prisoner existing = data.get(player.getUUID());
        if (existing != null) {
            existing.remaining = ticks;
            existing.sentence = ticks;
            data.setDirty();
            player.sendSystemMessage(Component.literal("감옥 시간이 " + minutes + "분으로 바뀌었습니다.").withStyle(ChatFormatting.RED));
            return;
        }
        Inventory inventory = player.getInventory();
        List<Prisoner.Slot> items = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                items.add(new Prisoner.Slot(i, stack.copy()));
            }
        }
        player.closeContainer();
        inventory.clearContent();
        data.put(player.getUUID(), new Prisoner(player.getGameProfile().name(), ticks, ticks, player.level().dimension(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer(), player.experienceLevel, player.experienceProgress, player.totalExperience, items));
        player.setGameMode(GameType.SURVIVAL);
        toYard(player);
        giveKit(player);
        player.setHealth(player.getMaxHealth());
        player.sendSystemMessage(Component.literal("감옥에 " + minutes + "분 동안 갇혔습니다. 가지고 있던 물건은 나갈 때 돌려받습니다.")
                .withStyle(ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("미니게임으로 시간을 줄일 수 있습니다: 석탄 캐기, 파쿠르, 과녁 맞히기, 낚시, 퀴즈(독서대)")
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Lets a player out: everything from the jail (coal, fish, the jail's tools) is taken away, their own items, level
     * and game mode are given back, and they return to where they were jailed.
     */
    public static boolean release(ServerPlayer player) {
        MinecraftServer server = server(player);
        Prisoner prisoner = JailData.get(server).remove(player.getUUID());
        if (prisoner == null) {
            return false;
        }
        MiniGames.forget(player);
        player.closeContainer();
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        for (Prisoner.Slot slot : prisoner.items) {
            if (slot.slot() < inventory.getContainerSize() && inventory.getItem(slot.slot()).isEmpty()) {
                inventory.setItem(slot.slot(), slot.stack().copy());
            } else if (!inventory.add(slot.stack().copy())) {
                player.drop(slot.stack().copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        player.experienceProgress = prisoner.xpProgress;
        player.totalExperience = prisoner.xpTotal;
        player.setExperienceLevels(prisoner.xpLevel);
        ServerLevel back = server.getLevel(prisoner.dimension);
        if (back == null || back.dimension() == DIMENSION) {
            back = server.overworld();
            BlockPos top = back.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
            teleport(player, back, Vec3.atBottomCenterOf(top), 0, 0);
        } else {
            teleport(player, back, new Vec3(prisoner.x, prisoner.y, prisoner.z), prisoner.yaw, prisoner.pitch);
        }
        player.setGameMode(prisoner.gameMode);
        player.clearFire();
        player.sendSystemMessage(Component.literal("감옥에서 풀려났습니다. 물건과 레벨을 돌려받았습니다.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Takes time off a prisoner's sentence for a minigame, and says so. */
    public static void reduce(ServerPlayer player, int ticks, String why) {
        Prisoner prisoner = prisoner(player);
        if (prisoner == null) {
            return;
        }
        prisoner.remaining = Math.max(0, prisoner.remaining - ticks);
        JailData.get(server(player)).setDirty();
        player.sendSystemMessage(Component.literal(why + "! -" + seconds(ticks) + " (남은 시간 " + clock(prisoner.remaining) + ")")
                .withStyle(ChatFormatting.AQUA));
    }

    /** Every tick: the sentence counts down for prisoners online; anyone who wandered off is brought back. */
    public static void tick(MinecraftServer server) {
        JailData data = JailData.get(server);
        if (data.all().isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Prisoner prisoner = data.get(player.getUUID());
            if (prisoner == null) {
                continue;
            }
            if (prisoner.remaining <= 0) {
                release(player);
                continue;
            }
            if (!isJail(player.level()) || !JailBuilder.inside(player.position())) {
                toYard(player);
            }
            prisoner.remaining--;
            if (now % 20 == 0) {
                player.sendOverlayMessage(Component.literal("감옥 남은 시간 " + clock(prisoner.remaining)).withStyle(ChatFormatting.YELLOW));
                data.setDirty();
            }
            if (now % 100 == 0) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0F);
            }
            MiniGames.tickPrisoner(player, now);
        }
    }

    /** Puts a prisoner in the middle of the jail yard. */
    public static void toYard(ServerPlayer player) {
        ServerLevel jail = level(server(player));
        if (jail != null) {
            JailBuilder.ensureBuilt(jail);
            teleport(player, jail, JailBuilder.SPAWN, 180.0F, 0.0F);
        }
    }

    public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, false);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** The jail's tools: a pickaxe for the coal, a bow that never runs out of arrows, a fishing rod. None wear out. */
    public static void giveKit(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        inventory.add(tool(new ItemStack(Items.WOODEN_PICKAXE), "감옥 곡괭이"));
        ItemStack bow = tool(new ItemStack(Items.BOW), "감옥 활");
        Holder<Enchantment> infinity = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY);
        bow.enchant(infinity, 1);
        inventory.add(bow);
        inventory.add(new ItemStack(Items.ARROW));
        inventory.add(tool(new ItemStack(Items.FISHING_ROD), "감옥 낚싯대"));
    }

    /** Whether a player is holding the whole jail kit (given again after a death in jail). */
    public static boolean hasKit(ServerPlayer player) {
        return holds(player, Items.WOODEN_PICKAXE) && holds(player, Items.BOW) && holds(player, Items.FISHING_ROD);
    }

    /** Whether any slot holds this item, whatever its name or enchantments. */
    public static boolean holds(ServerPlayer player, net.minecraft.world.item.Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack tool(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    /** m:ss */
    public static String clock(int ticks) {
        int s = (ticks + 19) / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    /** "1분 30초", "45초" */
    public static String seconds(int ticks) {
        int s = ticks / 20;
        return s >= 60 ? s / 60 + "분" + (s % 60 > 0 ? " " + s % 60 + "초" : "") : s + "초";
    }
}
