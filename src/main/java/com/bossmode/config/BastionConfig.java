package com.bossmode.config;

/**
 * Every tunable value of the Bastion Behemoth (armoured raid boss), its boulders and the Bastion Altar.
 * <p>
 * Durations are in ticks. Attack ranges / radii are given at phase-1 size and grow with the boss
 * ({@code RaidBossEntity#sizeFactor()}: 1.0 -> 1.35). {@code *_TICK} values match the keyframes commented in
 * {@code tools/blender/model_bastion_behemoth.py}. The per-phase schedule is {@code BastionPhase}.
 * <p>
 * A walking fortress: eight pools behind heavy armour, a stone-plated front that halves every blow landing on it
 * (strike its flanks and back), the Fortify stance that almost ignores damage and spikes back at melee attackers,
 * and fast regeneration when left alone. Slow, but everything it does hits hard and covers ground.
 */
public final class BastionConfig {
	private BastionConfig() {
	}

	// =================================================================== body
	/** Each phase is its own pool, rising evenly: 100 HP in phase 1 to 500 in phase 8 (2,400 HP in all). */
	public static final double FIRST_PHASE_HEALTH = 100.0;
	public static final double LAST_PHASE_HEALTH = 500.0;
	public static final double ATTACK_DAMAGE = 26.0;
	public static final double MOVEMENT_SPEED = 0.22;
	public static final double ARMOR = 18.0, ARMOR_TOUGHNESS = 10.0;
	public static final double KNOCKBACK_RESISTANCE = 1.0, ATTACK_KNOCKBACK = 2.5, FOLLOW_RANGE = 80.0;
	public static final double STEP_HEIGHT = 2.0;
	/** Model is 8 blocks long and 5 tall at scale 1; the phase table grows it to x1.35. */
	public static final float BASE_SCALE = 1.0F, HITBOX_WIDTH = 5.0F, HITBOX_HEIGHT = 4.6F;
	public static final int XP_REWARD = 3000;
	/** Share of the phase's pool regained per second when left alone. */
	public static final float REGEN_SHARE_PER_SECOND = 0.02F;
	public static final int REGEN_DELAY = 120;
	public static final double BOSS_BAR_RANGE = 128.0;

	// =================================================================== defences
	/** Blows landing within this angle of its snout (half angle) hit the stone plating and are multiplied. */
	public static final double FRONT_ARMOR_HALF_ANGLE = 55.0;
	public static final float FRONT_ARMOR_MULTIPLIER = 0.5F;
	/** Fortify stance: incoming damage multiplier and the share of melee damage spiked back at the attacker. */
	public static final float FORTIFY_DAMAGE_MULTIPLIER = 0.2F, FORTIFY_THORNS = 0.3F;

	// =================================================================== lifecycle
	public static final int SPAWN_INTRO_TICKS = 60, SPAWN_ROAR_TICK = 44;
	public static final int PHASE_TRANSITION_TICKS = 50;
	public static final double TRANSITION_SHOCKWAVE_RADIUS = 10.0;
	public static final int DEATH_ANIMATION_TICKS = 60;
	public static final float DEATH_EXPLOSION_POWER = 4.5F;
	public static final float ENRAGE_HEALTH_THRESHOLD = 0.30F;
	public static final double ENRAGE_DAMAGE_MULTIPLIER = 1.25, ENRAGE_SPEED_MULTIPLIER = 1.25, ENRAGE_COOLDOWN_REDUCTION = 0.30;
	public static final int MAX_ACTIVE_MINIONS = 8;

	// =================================================================== movement / AI
	public static final float MAX_TURN_PER_TICK = 6.0F;
	public static final int GLOBAL_COOLDOWN_AFTER_ATTACK = 12, GLOBAL_COOLDOWN_AFTER_SPECIAL = 28;
	public static final double NORMAL_ATTACK_CHANCE = 0.5;

	// =================================================================== attacks
	// Maul (normal) - a forefoot crashes down in front
	public static final double MAUL_RANGE = 4.5, MAUL_CONE_DEGREES = 140.0, MAUL_KNOCKBACK = 1.6;
	public static final int MAUL_HIT_TICK = 12, MAUL_TOTAL_TICKS = 24, MAUL_COOLDOWN = 24;
	// Tail Sweep (normal) - the club tail sweeps its flanks and back
	public static final double SWEEP_RADIUS = 8.0, SWEEP_DAMAGE = 20.0, SWEEP_KNOCKBACK = 2.2;
	public static final int SWEEP_HIT_TICK = 13, SWEEP_TOTAL_TICKS = 26, SWEEP_COOLDOWN = 60;
	// Earthquake - rears and stamps: everything standing on the ground nearby is hit (jump!)
	public static final int QUAKE_HIT_TICK = 20, QUAKE_TOTAL_TICKS = 44, QUAKE_COOLDOWN = 180, QUAKE_WEIGHT = 30;
	public static final double QUAKE_RADIUS = 13.0, QUAKE_DAMAGE = 20.0;
	// Boulder Throw - a molten boulder lobbed at the target
	public static final int BOULDER_HIT_TICK = 20, BOULDER_TOTAL_TICKS = 40, BOULDER_COOLDOWN = 140, BOULDER_WEIGHT = 25, BOULDER_LIFETIME = 200;
	public static final double BOULDER_MIN_RANGE = 6.0, BOULDER_MAX_RANGE = 40.0, BOULDER_RADIUS = 3.5, BOULDER_GRAVITY = 0.06;
	public static final float BOULDER_DAMAGE = 22.0F;
	// Rolling Charge - curls into a ball and rolls through everything
	public static final int ROLL_START_TICK = 16, ROLL_END_TICK = 46, ROLL_TOTAL_TICKS = 60, ROLL_COOLDOWN = 240, ROLL_WEIGHT = 30;
	public static final double ROLL_SPEED = 0.85, ROLL_DAMAGE = 24.0, ROLL_KNOCKBACK = 2.4, ROLL_MIN_RANGE = 8.0, ROLL_MAX_RANGE = 36.0, ROLL_TURN = 2.5;
	// Fortify - plants itself; x0.2 damage, spikes back at melee attackers, a shockwave when it rises
	public static final int FORTIFY_START_TICK = 10, FORTIFY_END_TICK = 100, FORTIFY_TOTAL_TICKS = 110, FORTIFY_COOLDOWN = 520, FORTIFY_WEIGHT = 20;
	public static final double FORTIFY_BURST_RADIUS = 9.0, FORTIFY_BURST_DAMAGE = 18.0;
	// Crystal Spikes - lines of amber spikes burst from the ground toward the targets
	public static final int SPIKES_HIT_TICK = 16, SPIKES_TOTAL_TICKS = 32, SPIKES_COOLDOWN = 160, SPIKES_WEIGHT = 25;
	public static final double SPIKES_DAMAGE = 16.0, SPIKES_LENGTH = 22.0, SPIKES_STEP = 1.5, SPIKES_RADIUS = 1.4, SPIKES_RANGE = 30.0;
	// Magma Breach - fissures open under everyone nearby and erupt
	public static final int BREACH_MARK_TICK = 20, BREACH_ERUPT_TICK = 44, BREACH_TOTAL_TICKS = 48, BREACH_EXTRA = 4, BREACH_COOLDOWN = 260, BREACH_WEIGHT = 25;
	public static final double BREACH_RADIUS = 3.0, BREACH_DAMAGE = 22.0, BREACH_RANGE = 32.0, BREACH_LAUNCH = 1.0;
	// Seismic Roar - a roar that shakes everyone nearby: damage, knockback, slowness and weakness
	public static final int SEISMIC_HIT_TICK = 14, SEISMIC_TOTAL_TICKS = 36, SEISMIC_COOLDOWN = 300, SEISMIC_WEIGHT = 20;
	public static final double SEISMIC_RADIUS = 16.0, SEISMIC_DAMAGE = 14.0, SEISMIC_KNOCKBACK = 2.0;
	// Summon - hounds claw out of the ground
	public static final int SUMMON_HIT_TICK = 20, SUMMON_TOTAL_TICKS = 40, SUMMON_COOLDOWN = 420, SUMMON_WEIGHT = 15;

	// =================================================================== Bastion Altar
	/** The Behemoth rises this far in front of the altar; its arena radius. */
	public static final int ALTAR_SUMMON_DISTANCE = 12, ARENA_RADIUS = 50;
}
