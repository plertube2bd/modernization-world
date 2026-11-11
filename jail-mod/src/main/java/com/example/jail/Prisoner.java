package com.example.jail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * One jailed player: time left, and everything taken from them at the gate to hand back on release (their items by
 * inventory slot, their level and experience, where they stood and their game mode).
 */
public final class Prisoner {
    /** One inventory slot's item. */
    public record Slot(int slot, ItemStack stack) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(Slot::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(Slot::stack)
        ).apply(i, Slot::new));
    }

    public static final Codec<Prisoner> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(p -> p.name),
            Codec.INT.fieldOf("remaining").forGetter(p -> p.remaining),
            Codec.INT.fieldOf("sentence").forGetter(p -> p.sentence),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(p -> p.dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(p -> p.x),
            Codec.DOUBLE.fieldOf("y").forGetter(p -> p.y),
            Codec.DOUBLE.fieldOf("z").forGetter(p -> p.z),
            Codec.FLOAT.fieldOf("yaw").forGetter(p -> p.yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(p -> p.pitch),
            GameType.CODEC.fieldOf("game_mode").forGetter(p -> p.gameMode),
            Codec.INT.fieldOf("xp_level").forGetter(p -> p.xpLevel),
            Codec.FLOAT.fieldOf("xp_progress").forGetter(p -> p.xpProgress),
            Codec.INT.fieldOf("xp_total").forGetter(p -> p.xpTotal),
            Slot.CODEC.listOf().fieldOf("items").forGetter(p -> p.items)
    ).apply(i, Prisoner::new));

    public final String name;
    /** Ticks left; only counts down while the player is online. */
    public int remaining;
    public int sentence;
    public final ResourceKey<Level> dimension;
    public final double x, y, z;
    public final float yaw, pitch;
    public final GameType gameMode;
    public final int xpLevel;
    public final float xpProgress;
    public final int xpTotal;
    public final List<Slot> items;

    public Prisoner(String name, int remaining, int sentence, ResourceKey<Level> dimension, double x, double y, double z,
                    float yaw, float pitch, GameType gameMode, int xpLevel, float xpProgress, int xpTotal, List<Slot> items) {
        this.name = name;
        this.remaining = remaining;
        this.sentence = sentence;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameMode = gameMode;
        this.xpLevel = xpLevel;
        this.xpProgress = xpProgress;
        this.xpTotal = xpTotal;
        this.items = new ArrayList<>(items);
    }
}
