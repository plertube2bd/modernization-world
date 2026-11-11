package com.example.jail.mixin;

import com.example.jail.MiniGames;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A prisoner's arrow hitting a target in the jail takes time off. */
@Mixin(TargetBlock.class)
public abstract class TargetBlockMixin {
    @Inject(method = "onProjectileHit", at = @At("HEAD"))
    private void jail$targetHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile, CallbackInfo ci) {
        if (!level.isClientSide()) {
            MiniGames.onTargetHit(level, hit, projectile);
        }
    }
}
