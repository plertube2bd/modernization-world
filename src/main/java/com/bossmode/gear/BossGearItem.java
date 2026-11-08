package com.bossmode.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A piece of boss gear: its tier and kind, and a tooltip naming what it does beyond its numbers. */
public final class BossGearItem extends Item {
    private final BossTier tier;
    private final BossGearKind kind;

    public BossGearItem(Properties properties, BossTier tier, BossGearKind kind) {
        super(properties);
        this.tier = tier;
        this.kind = kind;
    }

    public BossTier tier() {
        return tier;
    }

    public BossGearKind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (kind.isArmor()) {
            tooltip.accept(Component.translatable("tooltip.boss_mode.gear.set").withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.boss_mode.gear.set." + tier.id()).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (kind == BossGearKind.SWORD) {
            tooltip.accept(Component.translatable("tooltip.boss_mode.gear.hit." + tier.id()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
