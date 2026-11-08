package com.bossmode.entity.raid;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;

/**
 * The fixed numbers and assets of one raid boss type (its config class builds one). Durations in ticks.
 *
 * @param id              entity / model id, e.g. {@code tempest_wyvern} (animations are {@code animation.<id>.<clip>})
 * @param actionClips     clips the action controller can trigger (the phase roar is added automatically)
 * @param regenSharePerSecond share of the phase's pool regained per second when left alone
 * @param firstPhaseHealth    phase 1's pool; the pools rise evenly to {@code lastPhaseHealth} in the last phase
 * @param enrageThreshold health fraction of the last phase at which it enrages once
 */
public record RaidBossSpec(String id, float baseScale, float regenSharePerSecond, int regenDelay, double firstPhaseHealth,
		double lastPhaseHealth, int introTicks, int roarTick,
		int transitionTicks, double transitionShockwave, int deathTicks, float deathExplosionPower, float enrageThreshold,
		double enrageDamage, double enrageSpeed, double enrageCooldownReduction, double bossBarRange, BossEvent.BossBarOverlay overlay,
		double normalChance, int globalAfterNormal, int globalAfterSpecial, int maxMinions, float maxTurn, int xp, DustParticleOptions dust,
		SoundEvent spawnSound, SoundEvent roarSound, SoundEvent ambientSound, SoundEvent hurtSound, SoundEvent deathSound, String[] actionClips) {

	public String clip(String name) {
		return "animation." + this.id + "." + name;
	}

	public String portrait() {
		return "textures/gui/portrait_" + this.id + ".png";
	}
}
