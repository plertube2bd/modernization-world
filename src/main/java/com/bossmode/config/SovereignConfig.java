package com.bossmode.config;

/**
 * Every tunable value of the Void Sovereign (final boss), its pylons, orbs and the Void Altar.
 * <p>
 * Durations are in ticks. Attack ranges / radii are given at scale 1.0 and grow with the boss
 * ({@code VoidSovereignEntity#sizeFactor()}: 1.0 in phase 1 -> 1.5 in phase 10). {@code *_TICK} values match the keyframes
 * commented in {@code tools/blender/model_void_sovereign.py}. The per-phase schedule is {@code SovereignPhase}.
 * <p>
 * Compared with the Shard Dragon: as many pools but tougher ones (10 x 1,024 HP behind 20 armour / 12 toughness), harder
 * hits, a wider arena, the Void Shield (it only falls when its Sovereign Shards are destroyed) and a set of counters for
 * aerial mace smashes (Skyward Lash, Gravity Well, Abyssal Carapace, Soul Chains) - the mace still works, but not for free.
 */
public final class SovereignConfig {
	private SovereignConfig() {
	}

	// =================================================================== body
	/** Each phase is its own pool, rising evenly: 100 HP in phase 1 to 600 in phase 10 (3,500 HP in all). */
	public static final double FIRST_PHASE_HEALTH = 100.0;
	public static final double LAST_PHASE_HEALTH = 600.0;
	public static final double ATTACK_DAMAGE = 24.0;
	public static final double MOVEMENT_SPEED = 0.30;
	public static final double ARMOR = 20.0, ARMOR_TOUGHNESS = 12.0;
	public static final double KNOCKBACK_RESISTANCE = 1.0, ATTACK_KNOCKBACK = 2.5, FOLLOW_RANGE = 80.0;
	public static final double STEP_HEIGHT = 2.5;
	/** Model is 7.5 blocks tall at scale 1; the phase table grows it to x1.5 (hitbox 4.4 x 7.2 -> 6.6 x 10.8). */
	public static final float BASE_SCALE = 1.0F, HITBOX_WIDTH = 4.4F, HITBOX_HEIGHT = 7.2F;
	public static final int XP_REWARD = 6000;
	/** Share of the phase's pool regained per second when left alone. */
	public static final float REGEN_SHARE_PER_SECOND = 0.01F;
	public static final int REGEN_DELAY = 120;
	public static final float EXPLOSION_DAMAGE_MULTIPLIER = 0.5F;
	public static final boolean CRUSHES_BLOCKS = true;
	public static final int CRUSH_MAX_BLOCKS_PER_TICK = 24;
	public static final double BOSS_BAR_RANGE = 128.0;
	public static final int BOSS_BAR_MEMBERSHIP_INTERVAL = 10;

	// =================================================================== lifecycle
	public static final int SPAWN_INTRO_TICKS = 60, SPAWN_ROAR_TICK = 44;
	public static final int PHASE_TRANSITION_TICKS = 50;
	public static final double TRANSITION_SHOCKWAVE_RADIUS = 10.0;
	public static final int DEATH_ANIMATION_TICKS = 60;
	public static final float DEATH_EXPLOSION_POWER = 5.0F;
	public static final double MINION_CLEANUP_RADIUS = 128.0;
	/** Final phase, one time. */
	public static final float ENRAGE_HEALTH_THRESHOLD = 0.35F;
	public static final double ENRAGE_DAMAGE_MULTIPLIER = 1.25, ENRAGE_SPEED_MULTIPLIER = 1.15, ENRAGE_COOLDOWN_REDUCTION = 0.30;

	// =================================================================== movement / AI
	public static final float MAX_TURN_PER_TICK = 12.0F;
	public static final int STUCK_TICKS_FOR_UNREACHABLE = 40;
	public static final int GLOBAL_COOLDOWN_AFTER_ATTACK = 10, GLOBAL_COOLDOWN_AFTER_SPECIAL = 28;
	public static final double NORMAL_ATTACK_CHANCE = 0.5;

	// =================================================================== Void Shield / Sovereign Shards (phases 3, 6 and 9)
	/** Pylons float this high above the ground, this far from the boss, and each fires a void orb every interval. */
	public static final double PYLON_HEALTH = 60.0, PYLON_RADIUS = 20.0, PYLON_HOVER = 3.5;
	public static final int PYLON_SHOT_INTERVAL = 80;
	public static final float PYLON_ORB_DAMAGE = 10.0F;

	// =================================================================== attacks
	// Claw Swipe (normal)
	public static final double CLAW_RANGE = 4.5, CLAW_CONE_DEGREES = 150.0, CLAW_KNOCKBACK = 1.4;
	public static final int CLAW_HIT_TICK = 11, CLAW_TOTAL_TICKS = 24, CLAW_COOLDOWN = 24;
	// Tentacle Lash (normal) - hits all around
	public static final double LASH_RADIUS = 9.0, LASH_DAMAGE = 18.0, LASH_KNOCKBACK = 2.4;
	public static final int LASH_HIT_TICK = 13, LASH_TOTAL_TICKS = 26, LASH_COOLDOWN = 70;
	// Ground Pound - shockwave rings racing outward along the ground; jump over them
	public static final int POUND_HIT_TICK = 18, POUND_TOTAL_TICKS = 56, POUND_RINGS = 3, POUND_RING_GAP = 8, POUND_COOLDOWN = 160, POUND_WEIGHT = 30;
	public static final double POUND_DAMAGE = 20.0, POUND_RING_SPEED = 0.9, POUND_RING_MAX = 28.0, POUND_RING_WIDTH = 1.5, POUND_MAX_RANGE = 26.0;
	// Void Orbs - slow homing orbs
	public static final int ORBS_HIT_TICK = 16, ORBS_TOTAL_TICKS = 32, ORBS_BASE_COUNT = 3, ORBS_COOLDOWN = 140, ORBS_WEIGHT = 25, ORB_LIFETIME = 140;
	public static final float ORB_DAMAGE = 14.0F;
	public static final double ORB_SPEED = 0.55, ORB_TURN = 0.08, ORB_BLAST_RADIUS = 2.5, ORBS_MAX_RANGE = 48.0;
	// Rift Eruption - marked circles under the players burst into void tendrils
	public static final int RIFT_MARK_TICK = 18, RIFT_ERUPT_TICK = 40, RIFT_TOTAL_TICKS = 48, RIFT_EXTRA = 6, RIFT_COOLDOWN = 200, RIFT_WEIGHT = 25;
	public static final double RIFT_RADIUS = 2.5, RIFT_DAMAGE = 20.0, RIFT_LAUNCH = 1.2, RIFT_RANGE = 40.0;
	// Void Grasp - tentacles drag the target in, then a claw slam
	public static final int GRASP_SEIZE_TICK = 12, GRASP_RELEASE_TICK = 52, GRASP_SLAM_TICK = 56, GRASP_TOTAL_TICKS = 66, GRASP_COOLDOWN = 240,
			GRASP_WEIGHT = 20;
	public static final double GRASP_RANGE = 22.0, GRASP_PULL = 0.35, GRASP_DAMAGE_PER_SECOND = 6.0, GRASP_SLAM_DAMAGE = 22.0;
	// Black Hole - a singularity drags everything in for four seconds, then collapses
	public static final int HOLE_CAST_TICK = 20, HOLE_COLLAPSE_TICK = 100, HOLE_TOTAL_TICKS = 110, HOLE_COOLDOWN = 360, HOLE_WEIGHT = 30;
	public static final double HOLE_PULL_RADIUS = 18.0, HOLE_PULL = 0.11, HOLE_CORE_RADIUS = 6.0, HOLE_DAMAGE = 36.0, HOLE_DISTANCE = 10.0;
	// Void Step - sinks into the void, reappears behind the target and slams
	public static final int STEP_VANISH_TICK = 14, STEP_SLAM_TICK = 24, STEP_TOTAL_TICKS = 36, STEP_COOLDOWN = 180, STEP_WEIGHT = 25;
	public static final double STEP_BEHIND = 4.0, STEP_DAMAGE = 22.0, STEP_RADIUS = 5.0, STEP_MIN_RANGE = 8.0, STEP_MAX_RANGE = 48.0;
	// Eclipse (ultimate) - the sky goes dark and meteors rain on the arena
	public static final int ECLIPSE_START_TICK = 30, ECLIPSE_END_TICK = 130, ECLIPSE_TOTAL_TICKS = 150, ECLIPSE_METEORS = 18, ECLIPSE_FALL_TICKS = 24,
			ECLIPSE_COOLDOWN = 500, ECLIPSE_WEIGHT = 40;
	public static final double ECLIPSE_RADIUS = 3.5, ECLIPSE_DAMAGE = 26.0, ECLIPSE_RANGE = 36.0;
	// Soul Drain - life drained from everyone nearby heals the boss (walk out of it)
	public static final int DRAIN_START_TICK = 16, DRAIN_END_TICK = 76, DRAIN_TOTAL_TICKS = 86, DRAIN_COOLDOWN = 300, DRAIN_WEIGHT = 20;
	public static final double DRAIN_RADIUS = 16.0, DRAIN_DAMAGE_PER_SECOND = 5.0, DRAIN_HEAL_FACTOR = 2.0;
	// Summon - the shard legion answers the Sovereign
	public static final int SUMMON_HIT_TICK = 20, SUMMON_TOTAL_TICKS = 40, SUMMON_COOLDOWN = 400, SUMMON_WEIGHT = 20, MAX_ACTIVE_MINIONS = 10;
	// Void Shield (forced at the start of a shard phase)
	public static final int SHIELD_RAISE_TICK = 20, SHIELD_TOTAL_TICKS = 50;

	// ------------------------------------------------------------------- counters to aerial (mace) smashes
	/** A player counts as an aerial threat above / next to the Sovereign within this horizontal reach (+ half its width). */
	public static final double AERIAL_THREAT_REACH = 7.0, AERIAL_THREAT_FALL = 2.0, AERIAL_THREAT_RISE = 0.5;
	/** Anyone holding a mace this close makes the counters three times as likely. */
	public static final double MACE_SENSE_RADIUS = 32.0;
	public static final int COUNTER_WEIGHT_FACTOR = 3;
	// Skyward Lash - tentacles whip up at airborne attackers (also triggered by itself when one dives at it)
	public static final int LASH_UP_HIT_TICK = 6, LASH_UP_TOTAL_TICKS = 22, LASH_UP_COOLDOWN = 80, LASH_UP_WEIGHT = 35, LASH_UP_REACT_COOLDOWN = 100;
	public static final double LASH_UP_REACH = 8.0, LASH_UP_HEIGHT = 14.0, LASH_UP_DAMAGE = 16.0, LASH_UP_FLING = 2.2, LASH_UP_GROUND_DAMAGE = 8.0;
	// Gravity Well - six seconds in which nothing stays airborne near the Sovereign (fall distance keeps resetting)
	public static final int WELL_START_TICK = 16, WELL_END_TICK = 136, WELL_TOTAL_TICKS = 146, WELL_COOLDOWN = 420, WELL_WEIGHT = 20;
	public static final double WELL_RADIUS = 16.0, WELL_PULL_DOWN = 1.1, WELL_DAMAGE = 3.0;
	// Abyssal Carapace - every blow is capped, the excess is partly thrown back at the attacker
	public static final int CARAPACE_START_TICK = 12, CARAPACE_END_TICK = 92, CARAPACE_TOTAL_TICKS = 100, CARAPACE_COOLDOWN = 460, CARAPACE_WEIGHT = 20;
	public static final float CARAPACE_HIT_CAP = 15.0F, CARAPACE_REFLECT = 0.25F, CARAPACE_REFLECT_MAX = 24.0F;
	// Soul Chains - everyone nearby is chained to where they stand; stretching the chain yanks them back
	public static final int CHAINS_BIND_TICK = 16, CHAINS_END_TICK = 116, CHAINS_TOTAL_TICKS = 126, CHAINS_COOLDOWN = 400, CHAINS_WEIGHT = 20;
	public static final double CHAINS_RANGE = 24.0, CHAINS_LENGTH = 4.5, CHAINS_YANK = 0.8, CHAINS_DAMAGE = 4.0;

	// ------------------------------------------------------------------- more patterns (phases 4-10)
	// Null Sweep - a knee-high beam sweeps 270 degrees around the Sovereign: jump over it
	public static final int SWEEP_START_TICK = 24, SWEEP_END_TICK = 84, SWEEP_TOTAL_TICKS = 96, SWEEP_COOLDOWN = 300, SWEEP_WEIGHT = 25;
	public static final double SWEEP_LENGTH = 22.0, SWEEP_ARC_DEGREES = 270.0, SWEEP_BEAM_HEIGHT = 0.4, SWEEP_DAMAGE = 18.0;
	// Tentacle Field - tentacles erupt in a checkerboard around the Sovereign, alternating squares
	public static final int FIELD_FIRST_TICK = 36, FIELD_GAP = 20, FIELD_WAVES = 3, FIELD_TOTAL_TICKS = 90, FIELD_COOLDOWN = 320, FIELD_WEIGHT = 25;
	public static final double FIELD_RADIUS = 18.0, FIELD_CELL = 4.0, FIELD_DAMAGE = 16.0, FIELD_LAUNCH = 0.9;
	// Dimension Rend - a slash along a line that leaves a burning rift
	public static final int REND_SLASH_TICK = 24, REND_END_TICK = 84, REND_TOTAL_TICKS = 94, REND_COOLDOWN = 280, REND_WEIGHT = 25;
	public static final double REND_LENGTH = 32.0, REND_WIDTH = 1.6, REND_DAMAGE = 26.0, REND_RIFT_DAMAGE = 6.0;
	// Abyssal Spears - volleys of spears fall where every player stands
	public static final int SPEARS_START_TICK = 16, SPEARS_INTERVAL = 12, SPEARS_VOLLEYS = 5, SPEARS_FALL_TICKS = 14, SPEARS_TOTAL_TICKS = 96,
			SPEARS_COOLDOWN = 300, SPEARS_WEIGHT = 25;
	public static final double SPEARS_RADIUS = 1.8, SPEARS_DAMAGE = 14.0, SPEARS_RANGE = 36.0;
	// Oblivion (ultimate, phases 9-10) - everything outside the three safe circles is struck
	public static final int OBLIVION_MARK_TICK = 20, OBLIVION_BLAST_TICK = 120, OBLIVION_TOTAL_TICKS = 140, OBLIVION_COOLDOWN = 700, OBLIVION_WEIGHT = 40;
	public static final double OBLIVION_RANGE = 48.0, OBLIVION_SAFE_RADIUS = 3.5, OBLIVION_SAFE_DISTANCE = 15.0, OBLIVION_DAMAGE = 40.0;

	// =================================================================== Void Altar
	/** The Void Altar takes one trophy of each raid boss: Wyrm Heart, Tempest Eye, Behemoth Core (see VoidAltarBlock). */
	/** The ritual lasts steps x step ticks (7 s); the Sovereign rises this far in front of the altar. */
	public static final int ALTAR_RITUAL_STEPS = 28, ALTAR_RITUAL_STEP_TICKS = 5, ALTAR_SUMMON_DISTANCE = 12;
	public static final double ALTAR_EXCLUSION_RADIUS = 256.0, ALTAR_ANNOUNCE_RADIUS = 128.0;
	/** Its arena is larger than the dragon's. */
	public static final int ARENA_RADIUS = 56;
}
