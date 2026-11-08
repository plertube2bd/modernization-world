package com.bossmode.mixin;

import com.bossmode.gear.BossGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Behemoth (and ultimate) armour takes less from explosions. */
@Mixin(LivingEntity.class)
public abstract class BossGearDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float boss_mode$blastArmour(float amount, ServerLevel level, DamageSource source) {
        return BossGear.adjustIncomingDamage((LivingEntity) (Object) this, source, amount);
    }
}
