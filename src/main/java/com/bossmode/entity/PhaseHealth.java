package com.bossmode.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Each boss phase is its own health pool, and the pools grow phase by phase: from the first phase's pool to the last
 * phase's, in even steps.
 */
public final class PhaseHealth {
	private PhaseHealth() {
	}

	/** The pool of {@code phase} (1-based) out of {@code phases}, rounded to a whole number. */
	public static double pool(int phase, int phases, double first, double last) {
		if (phases <= 1) {
			return last;
		}
		double step = (Mth.clamp(phase, 1, phases) - 1) / (double) (phases - 1);
		return Math.round(first + (last - first) * step);
	}

	/**
	 * Makes {@code pool} the boss's max health. With {@code keepShare} its health keeps the same share of the bar (a boss
	 * saved with other pools, by an older version); otherwise health is only held to the new max.
	 */
	public static void apply(LivingEntity boss, double pool, boolean keepShare) {
		AttributeInstance max = boss.getAttribute(Attributes.MAX_HEALTH);
		if (max == null || max.getBaseValue() == pool) {
			return;
		}
		float share = boss.getMaxHealth() > 0.0F ? boss.getHealth() / boss.getMaxHealth() : 1.0F;
		max.setBaseValue(pool);
		if (keepShare && share > 0.0F) {
			boss.setHealth(Math.max(1.0F, share * boss.getMaxHealth()));
		} else if (boss.getHealth() > boss.getMaxHealth()) {
			boss.setHealth(boss.getMaxHealth());
		}
	}
}
