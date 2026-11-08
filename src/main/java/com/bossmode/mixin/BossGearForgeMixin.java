package com.bossmode.mixin;

import com.bossmode.gear.BossGear;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An ultimate piece forged from four boss pieces keeps their enchantments, each at the highest level. */
@Mixin(ShapelessRecipe.class)
public abstract class BossGearForgeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void boss_mode$keepEnchantments(CraftingInput input, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (BossGear.isUltimate(result)) BossGear.mergeEnchantments(result, input.items());
    }
}
