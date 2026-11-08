package com.bossmode.config;

/**
 * Every tunable value of the Tempest Wyvern (flying raid boss), its whirlwinds and the Tempest Altar.
 * <p>
 * Durations are in ticks. Attack ranges / radii are given at phase-1 size and grow with the boss
 * ({@code RaidBossEntity#sizeFactor()}: 1.0 -> 1.375). {@code *_TICK} values match the keyframes commented in
 * {@code tools/blender/model_tempest_wyvern.py}. The per-phase schedule is {@code TempestPhase}.
 * <p>
 * The Wyvern lives in the air: it circles its target out of melee reach and only comes down to dive, to land for a
 * while - or when it is shot down. Arrows, tridents and other projectiles are the answer; melee gets its turn on the
 * ground. It does not regenerate while airborne.
 */
public final class TempestConfig {
	private TempestConfig() {
	}

	// =================================================================== body
	/** Each phase is its own pool, rising evenly: 100 HP in phase 1 to 400 in phase 5 (1,250 HP in all). */
	public static final double FIRST_PHASE_HEALTH = 100.0;
	public static final double LAST_PHASE_HEALTH = 400.0;
	public static final double ATTACK_DAMAGE = 16.0;
	public static final double MOVEMENT_SPEED = 0.30;
	public static final double ARMOR = 10.0, ARMOR_TOUGHNESS = 4.0;
	public static final double KNOCKBACK_RESISTANCE = 1.0, ATTACK_KNOCKBACK = 1.5, FOLLOW_RANGE = 96.0;
	public static final double STEP_HEIGHT = 1.5;
	/** Model is 3.1 blocks tall at scale 1; the phase table grows it x1.6 -> x2.2. */
	public static final float BASE_SCALE = 1.6F, HITBOX_WIDTH = 3.0F, HITBOX_HEIGHT = 3.2F;
	public static final int XP_REWARD = 2500;
	/** Share of the phase's pool regained per second when left alone (never in the air). */
	public static final float REGEN_SHARE_PER_SECOND = 0.008F;
	public static final int REGEN_DELAY = 140;
	public static final double BOSS_BAR_RANGE = 128.0;

	// =================================================================== lifecycle
	public static final int SPAWN_INTRO_TICKS = 50, SPAWN_ROAR_TICK = 32;
	public static final int PHASE_TRANSITION_TICKS = 50;
	public static final double TRANSITION_SHOCKWAVE_RADIUS = 9.0;
	public static final int DEATH_ANIMATION_TICKS = 50;
	public static final float DEATH_EXPLOSION_POWER = 4.0F;
	public static final float ENRAGE_HEALTH_THRESHOLD = 0.30F;
	public static final double ENRAGE_DAMAGE_MULTIPLIER = 1.25, ENRAGE_SPEED_MULTIPLIER = 1.2, ENRAGE_COOLDOWN_REDUCTION = 0.30;
	public static final int MAX_ACTIVE_MINIONS = 6;

	// =================================================================== movement / AI
	public static final float MAX_TURN_PER_TICK = 14.0F;
	public static final int GLOBAL_COOLDOWN_AFTER_ATTACK = 10, GLOBAL_COOLDOWN_AFTER_SPECIAL = 24;
	public static final double NORMAL_ATTACK_CHANCE = 0.45;

	// =================================================================== flight
	/** Cruising height above the ground (or the target, if it stands higher) and the circle it flies around the target. */
	public static final double FLY_ALTITUDE = 7.0, FLY_ORBIT_RADIUS = 11.0, FLY_SPEED = 0.65, FLY_ORBIT_SPEED = 0.035;
	/** Air time before it comes down (dive or landing), and ground time before it takes off again. */
	public static final int AIR_TICKS = 260, GROUND_TICKS = 100, FIRST_GROUND_TICKS = 30;
	/** Damage taken during one flight, as a fraction of the phase pool, that shoots it down. */
	public static final float SHOOT_DOWN_FRACTION = 0.07F;
	/** Chance that it ends a flight with a Dive Bomb onto the target instead of a plain landing. */
	public static final double LAND_DIVE_CHANCE = 0.6;
	public static final int TAKEOFF_LAUNCH_TICK = 7, TAKEOFF_TOTAL_TICKS = 24;
	public static final double TAKEOFF_DAMAGE = 8.0, TAKEOFF_RADIUS = 5.0, TAKEOFF_RISE_SPEED = 0.75;
	public static final int LAND_IMPACT_TICK = 18, LAND_TOTAL_TICKS = 28;
	public static final double LAND_DAMAGE = 10.0, LAND_RADIUS = 5.0;
	/** Shot down: falls, crashes at the impact tick and lies stunned (EXPOSED) until the end. */
	public static final int CRASH_IMPACT_TICK = 14, CRASH_TOTAL_TICKS = 70;
	public static final double CRASH_DAMAGE = 12.0, CRASH_RADIUS = 5.0;

	// =================================================================== attacks
	// Talon Strike (normal, ground)
	public static final double TALON_RANGE = 3.5, TALON_CONE_DEGREES = 130.0, TALON_KNOCKBACK = 1.1;
	public static final int TALON_HIT_TICK = 9, TALON_TOTAL_TICKS = 18, TALON_COOLDOWN = 20;
	// Tail Whip (normal, ground) - hits all around
	public static final double WHIP_RADIUS = 6.0, WHIP_DAMAGE = 14.0, WHIP_KNOCKBACK = 1.8;
	public static final int WHIP_HIT_TICK = 11, WHIP_TOTAL_TICKS = 22, WHIP_COOLDOWN = 60;
	// Wind Gust - a downstroke that blasts everything in front away
	public static final int GUST_HIT_TICK = 14, GUST_TOTAL_TICKS = 32, GUST_COOLDOWN = 140, GUST_WEIGHT = 25;
	public static final double GUST_RANGE = 16.0, GUST_CONE_DEGREES = 90.0, GUST_DAMAGE = 8.0, GUST_KNOCKBACK = 2.6;
	// Lightning Breath - a crackling beam that follows the target
	public static final int BREATH_START_TICK = 18, BREATH_END_TICK = 54, BREATH_TOTAL_TICKS = 64, BREATH_COOLDOWN = 200, BREATH_WEIGHT = 25;
	public static final double BREATH_RANGE = 26.0, BREATH_WIDTH = 1.4, BREATH_DAMAGE = 5.0, BREATH_TRACKING = 0.12;
	public static final int BREATH_HIT_INTERVAL = 5;
	// Storm Bolts - marked spots, then lightning
	public static final int BOLTS_MARK_TICK = 18, BOLTS_TOTAL_TICKS = 40, BOLTS_DELAY = 20, BOLTS_BASE = 4, BOLTS_COOLDOWN = 180, BOLTS_WEIGHT = 25;
	public static final double BOLTS_RADIUS = 2.5, BOLTS_DAMAGE = 16.0, BOLTS_SCATTER = 7.0, BOLTS_RANGE = 40.0;
	// Dive Bomb (air only) - locks on, plunges and lands
	public static final int DIVE_IMPACT_TICK = 32, DIVE_TOTAL_TICKS = 48, DIVE_AIM_TICK = 12, DIVE_COOLDOWN = 240, DIVE_WEIGHT = 30;
	public static final double DIVE_DAMAGE = 22.0, DIVE_RADIUS = 5.0, DIVE_MIN_RANGE = 4.0, DIVE_MAX_RANGE = 40.0;
	/** It only dives on its own after this long in the air, so every flight leaves a window to shoot it down. */
	public static final int DIVE_MIN_AIR_TICKS = 100;
	// Cyclone - whirlwinds that roam the arena, suck players in and fling them up
	public static final int CYCLONE_HIT_TICK = 20, CYCLONE_TOTAL_TICKS = 44, CYCLONE_COOLDOWN = 320, CYCLONE_WEIGHT = 20, CYCLONE_LIFETIME = 140;
	public static final double CYCLONE_RADIUS = 2.8, CYCLONE_PULL_RADIUS = 7.0, CYCLONE_SPEED = 0.22, CYCLONE_DAMAGE = 5.0, CYCLONE_PULL = 0.06;
	// Feather Barrage - a fan of storm feathers
	public static final int FEATHER_HIT_TICK = 16, FEATHER_TOTAL_TICKS = 32, FEATHER_BASE = 5, FEATHER_COOLDOWN = 120, FEATHER_WEIGHT = 25;
	public static final double FEATHER_SPEED = 1.6, FEATHER_SPREAD_DEGREES = 50.0, FEATHER_RANGE = 36.0;
	public static final float FEATHER_DAMAGE = 9.0F;
	// Thunder Roar - a thunderclap around the Wyvern
	public static final int ROAR_HIT_TICK = 14, ROAR_TOTAL_TICKS = 36, ROAR_COOLDOWN = 260, ROAR_WEIGHT = 20;
	public static final double ROAR_RADIUS = 14.0, ROAR_DAMAGE = 14.0, ROAR_KNOCKBACK = 1.8;
	// Static Field - the ground around the target crackles; get out of it
	public static final int FIELD_START_TICK = 16, FIELD_END_TICK = 60, FIELD_TOTAL_TICKS = 70, FIELD_PULSE = 10, FIELD_COOLDOWN = 300, FIELD_WEIGHT = 20;
	public static final double FIELD_RADIUS = 7.0, FIELD_DAMAGE = 5.0, FIELD_RANGE = 36.0;
	// Summon - breezes answer the storm
	public static final int SUMMON_HIT_TICK = 20, SUMMON_TOTAL_TICKS = 40, SUMMON_COOLDOWN = 420, SUMMON_WEIGHT = 15;

	// =================================================================== Tempest Altar
	/** The Wyvern rises this far in front of the altar; its arena radius. */
	public static final int ALTAR_SUMMON_DISTANCE = 10, ARENA_RADIUS = 52;
}
