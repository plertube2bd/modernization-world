package com.bossmode.config;

/**
 * Every tunable value of the Shard Dragon fight (boss, minions, mid-boss) in one place.
 * <p>
 * Durations are in ticks (20 ticks = 1 second). Boss attack ranges / radii are given for phase 1 and grow with the
 * dragon ({@code ShardDragonEntity#sizeFactor()}: 1.0 in phase 1 -> 2.0 in phase 10). {@code *_HIT_TICK} values
 * must match the keyframes in {@code tools/blender/model_*.py}. The per-phase schedule (size, multipliers, attack
 * pool, minion pool, sealed damage types) is the table in {@link com.bossmode.entity.boss.BossPhase}.
 * <p>
 * Reference - Wither Storm (Cracker's Wither Storm Mod): 400 HP (phases 0-3) / 1024 HP (phases 4-7), 8 armor,
 * regenerates 10 HP/s, 3.5 damage per hit on Normal, 0.6 base speed, chases at 11+ blocks/s, 7 phases.
 */
public final class BossConfig {
	private BossConfig() {
	}

	// =================================================================== Shard Dragon - body
	/** Each phase is its own pool, rising evenly: 100 HP in phase 1 to 300 in phase 10 (1,989 HP in all). */
	public static final double FIRST_PHASE_HEALTH = 100.0;
	public static final double LAST_PHASE_HEALTH = 300.0;
	public static final double ATTACK_DAMAGE = 18.0;
	/** Ground mobs move roughly with speed^2: 0.35 = ~7 blocks/s in phase 1, ~12 blocks/s in phase 10. */
	public static final double MOVEMENT_SPEED = 0.35;
	public static final double ARMOR = 16.0;
	public static final double ARMOR_TOUGHNESS = 8.0;
	public static final double KNOCKBACK_RESISTANCE = 1.0;
	public static final double ATTACK_KNOCKBACK = 2.0;
	public static final double FOLLOW_RANGE = 64.0;
	/** Base step height (multiplied by the current scale, attribute cap 10). */
	public static final double STEP_HEIGHT = 1.5;
	/** Model scale in phase 1 (phase table grows it to 4.0). Hitbox below is at scale 1. */
	public static final float BASE_SCALE = 2.0F;
	public static final float HITBOX_WIDTH = 3.0F;
	public static final float HITBOX_HEIGHT = 2.9F;
	public static final int XP_REWARD = 2000;
	public static final float EXPLOSION_DAMAGE_MULTIPLIER = 0.5F;
	/** Crystal regeneration: this share of the phase's pool per second once the dragon has not been hurt for REGEN_DELAY ticks (never while flying). */
	public static final float REGEN_SHARE_PER_SECOND = 0.012F;
	public static final int REGEN_DELAY = 100;
	/** Damage taken while EXPOSED (recovering) in phases 9-10. */
	public static final float EXPOSED_DAMAGE_MULTIPLIER = 2.0F;
	/** Crush obstructing blocks while chasing (only with the mobGriefing game rule). */
	public static final boolean CRUSHES_BLOCKS = true;
	public static final int CRUSH_MAX_BLOCKS_PER_TICK = 24;

	// =================================================================== boss bar
	public static final double BOSS_BAR_RANGE = 96.0;
	public static final int BOSS_BAR_MEMBERSHIP_INTERVAL = 10;

	// =================================================================== lifecycle
	public static final int SPAWN_INTRO_TICKS = 50;
	public static final int SPAWN_ROAR_TICK = 30;
	public static final int PHASE_TRANSITION_TICKS = 50;
	public static final double TRANSITION_SHOCKWAVE_RADIUS = 8.0;
	public static final int DEATH_ANIMATION_TICKS = 50;
	public static final boolean DEATH_EXPLOSION = true;
	public static final float DEATH_EXPLOSION_POWER = 4.0F;
	public static final double MINION_CLEANUP_RADIUS = 96.0;

	// =================================================================== enrage (phase 10, one time)
	public static final float ENRAGE_HEALTH_THRESHOLD = 0.30F;
	public static final double ENRAGE_DAMAGE_MULTIPLIER = 1.20;
	public static final double ENRAGE_SPEED_MULTIPLIER = 1.10;
	public static final double ENRAGE_COOLDOWN_REDUCTION = 0.30;

	// =================================================================== movement / AI
	public static final float MAX_TURN_PER_TICK = 20.0F;
	public static final int STUCK_TICKS_FOR_UNREACHABLE = 40;
	public static final int GLOBAL_COOLDOWN_AFTER_ATTACK = 8;
	public static final int GLOBAL_COOLDOWN_AFTER_SPECIAL = 24;
	/** Chance to prefer a normal (melee) attack when one is usable. */
	public static final double NORMAL_ATTACK_CHANCE = 0.55;

	// =================================================================== flight (phases 6-10; air / ground time per phase in BossPhase)
	/** Hover height above the ground (or the target, whichever is higher) - out of melee reach - and orbit distance. */
	public static final double FLY_ALTITUDE = 9.0, FLY_ORBIT_RADIUS = 12.0;
	/** Top air speed in blocks per tick (x the phase speed multiplier: ~14-16 blocks/s) and orbit rate in radians per tick. */
	public static final double FLY_SPEED = 0.6, FLY_ORBIT_SPEED = 0.025;
	/** Damage taken during one flight that knocks the dragon out of the sky, as a fraction of the phase pool (~61 HP). */
	public static final float SHOOT_DOWN_FRACTION = 0.06F;
	/** Chance that a flight ends in a Meteor Dive onto the target instead of a plain landing (phases whose pool has it). */
	public static final double LAND_DIVE_CHANCE = 0.5;
	// Takeoff / landing / shot down - forced by the flight cycle, never picked at random. Radii grow with the dragon.
	public static final int TAKEOFF_LAUNCH_TICK = 8, TAKEOFF_TOTAL_TICKS = 30;
	public static final double TAKEOFF_DAMAGE = 8.0, TAKEOFF_RADIUS = 6.0, TAKEOFF_RISE_SPEED = 0.7;
	public static final int LAND_IMPACT_TICK = 20, LAND_TOTAL_TICKS = 34;
	public static final double LAND_DAMAGE = 12.0, LAND_RADIUS = 6.0;
	/** Shot down: falls, crashes at the impact tick and lies stunned (EXPOSED) for the rest - 3 seconds. */
	public static final int CRASH_IMPACT_TICK = 16, CRASH_TOTAL_TICKS = 76;
	public static final double CRASH_DAMAGE = 14.0, CRASH_RADIUS = 5.0;

	// =================================================================== dragon attacks
	// Bite (normal)
	public static final double BITE_RANGE = 3.2;
	public static final double BITE_CONE_DEGREES = 110.0;
	public static final int BITE_HIT_TICK = 9, BITE_TOTAL_TICKS = 18, BITE_COOLDOWN = 26;
	public static final double BITE_KNOCKBACK = 1.0;
	// Tail Sweep (normal) - rear / side arc
	public static final double TAIL_RANGE = 4.8;
	public static final double TAIL_DAMAGE = 16.0;
	public static final int TAIL_HIT_TICK = 13, TAIL_TOTAL_TICKS = 24, TAIL_COOLDOWN = 60;
	public static final double TAIL_KNOCKBACK = 2.2;
	// Shatter Slam
	public static final int SLAM_HIT_TICK = 24, SLAM_TOTAL_TICKS = 36, SLAM_COOLDOWN = 180;
	public static final double SLAM_DAMAGE = 22.0, SLAM_RADIUS = 9.0, SLAM_MAX_TARGET_DISTANCE = 9.0;
	public static final double SLAM_KNOCKBACK = 1.4, SLAM_KNOCKUP = 0.6;
	public static final int SLAM_SLOWNESS_TICKS = 60, SLAM_WEIGHT = 30;
	// Shard Volley
	public static final int VOLLEY_HIT_TICK = 18, VOLLEY_TOTAL_TICKS = 40, VOLLEY_COOLDOWN = 120;
	public static final double VOLLEY_MIN_RANGE = 6.0, VOLLEY_MAX_RANGE = 48.0, VOLLEY_SPREAD_DEGREES = 50.0;
	public static final int VOLLEY_BASE_COUNT = 3, VOLLEY_WEIGHT = 30;
	public static final double PROJECTILE_SPEED = 1.5;
	public static final float PROJECTILE_DAMAGE = 12.0F;
	public static final int PROJECTILE_LIFETIME = 100;
	public static final boolean PROJECTILE_GRAVITY = false;
	// Wing Tempest
	public static final int TEMPEST_DAMAGE_TICK = 16, TEMPEST_PUSH_TICK = 28, TEMPEST_TOTAL_TICKS = 40, TEMPEST_COOLDOWN = 200;
	public static final double TEMPEST_DAMAGE = 12.0, TEMPEST_RADIUS = 12.0, TEMPEST_PUSH_RADIUS = 15.0;
	public static final double TEMPEST_KNOCKBACK = 2.0, TEMPEST_PUSH_KNOCKBACK = 1.3, TEMPEST_MAX_TARGET_DISTANCE = 12.0;
	public static final int TEMPEST_WEIGHT = 25;
	// Obsidian Charge - 24 blocks/s (Wither Storm chase: 11 blocks/s)
	public static final int CHARGE_PREPARATION_TICKS = 15, CHARGE_DASH_TICKS = 20, CHARGE_TOTAL_TICKS = 45, CHARGE_COOLDOWN = 180;
	public static final double CHARGE_SPEED = 1.2, CHARGE_DISTANCE = 30.0, CHARGE_DAMAGE = 26.0;
	public static final double CHARGE_HITBOX_INFLATE = 0.6, CHARGE_KNOCKBACK = 2.4, CHARGE_MIN_RANGE = 8.0, CHARGE_MAX_RANGE = 34.0;
	public static final int CHARGE_WALL_STUN_TICKS = 30, CHARGE_WEIGHT = 25;
	// Crystal Rain - three waves of telegraphed impacts
	public static final int RAIN_PREPARATION_TICKS = 16, RAIN_WAVE_INTERVAL = 20, RAIN_WAVES = 3, RAIN_FALL_TICKS = 25, RAIN_TOTAL_TICKS = 90;
	public static final int RAIN_COOLDOWN = 220, RAIN_EXTRA_IMPACTS = 5, RAIN_WEIGHT = 25;
	public static final double RAIN_DAMAGE = 16.0, RAIN_IMPACT_RADIUS = 3.0, RAIN_RANGE = 40.0;
	// Spike Eruption - lines of crystal spikes racing outward
	public static final int SPIKES_HIT_TICK = 15, SPIKES_TOTAL_TICKS = 40, SPIKES_LINES = 8, SPIKES_COOLDOWN = 160, SPIKES_WEIGHT = 25;
	public static final double SPIKES_DAMAGE = 14.0, SPIKES_STEP = 1.6, SPIKES_WIDTH = 1.6;
	// Prism Beam - sustained tractor-beam style breath
	public static final int BEAM_START_TICK = 20, BEAM_END_TICK = 60, BEAM_TOTAL_TICKS = 70, BEAM_COOLDOWN = 200, BEAM_WEIGHT = 30;
	public static final double BEAM_DAMAGE = 7.0, BEAM_LENGTH = 48.0, BEAM_WIDTH = 1.4, BEAM_PULL = 0.12, BEAM_MIN_RANGE = 6.0;
	public static final int BEAM_DAMAGE_INTERVAL = 4;
	public static final float BEAM_TURN_PER_TICK = 2.5F;
	// Void Roar
	public static final int ROAR_HIT_TICK = 14, ROAR_TOTAL_TICKS = 36, ROAR_COOLDOWN = 240, ROAR_WEIGHT = 20;
	public static final double ROAR_DAMAGE = 6.0, ROAR_RADIUS = 20.0;
	// Meteor Dive - leap onto the target
	public static final int DIVE_LAUNCH_TICK = 10, DIVE_LAND_TICK = 36, DIVE_TOTAL_TICKS = 52, DIVE_COOLDOWN = 200, DIVE_WEIGHT = 30;
	public static final double DIVE_DAMAGE = 24.0, DIVE_RADIUS = 10.0, DIVE_ARC_HEIGHT = 12.0, DIVE_MIN_RANGE = 10.0, DIVE_MAX_RANGE = 48.0;
	// Cataclysm (ultimate) - everything in the open is hit; hide behind blocks or stay under the dragon
	public static final int CATA_BLAST_TICK = 50, CATA_TOTAL_TICKS = 120, CATA_COOLDOWN = 400, CATA_WEIGHT = 40;
	public static final double CATA_DAMAGE = 40.0, CATA_RADIUS = 64.0, CATA_COVER_MULTIPLIER = 0.15;
	// Summon
	public static final int SUMMON_HIT_TICK = 20, SUMMON_TOTAL_TICKS = 40, SUMMON_COOLDOWN = 360, SUMMON_WEIGHT = 25;
	public static final int MAX_ACTIVE_MINIONS = 14, MAX_ACTIVE_WARLORDS = 2, MINION_LIFETIME = 900;
	public static final double SUMMON_RADIUS = 5.0;

	// =================================================================== Shard Hound family (minions)
	public static final double HOUND_HEALTH = 40.0, HOUND_DAMAGE = 8.0, HOUND_SPEED = 0.34;
	public static final double SPITTER_HEALTH = 30.0, SPITTER_DAMAGE = 6.0, SPITTER_SPEED = 0.30;
	public static final double VOLATILE_HEALTH = 24.0, VOLATILE_SPEED = 0.38;
	public static final float VOLATILE_EXPLOSION_POWER = 3.0F;
	public static final int VOLATILE_FUSE_TICKS = 30, HOUND_ATTACK_HIT_TICK = 6, SPITTER_SHOT_TICK = 10;
	public static final int HOUND_EMERGE_TICKS = 20;

	// =================================================================== Shard Warlord (mid-boss)
	public static final double WARLORD_HEALTH = 600.0, WARLORD_DAMAGE = 16.0, WARLORD_SPEED = 0.30;
	public static final double WARLORD_ARMOR = 14.0, WARLORD_TOUGHNESS = 8.0, WARLORD_SCALE = 1.5;
	public static final int WARLORD_INTRO_TICKS = 30, WARLORD_DEATH_TICKS = 40;
	public static final double WARLORD_SWING_RANGE = 2.4, WARLORD_SWING_CONE = 140.0;
	public static final int WARLORD_SWING_HIT_TICK = 11, WARLORD_SWING_TOTAL = 20, WARLORD_SWING_COOLDOWN = 22;
	public static final int WARLORD_BOLT_HIT_TICK = 10, WARLORD_BOLT_TOTAL = 20, WARLORD_BOLT_COOLDOWN = 60;
	public static final float WARLORD_BOLT_DAMAGE = 10.0F;
	public static final int WARLORD_SPIKES_HIT_TICK = 15, WARLORD_SPIKES_TOTAL = 40, WARLORD_SPIKES_COOLDOWN = 110;
	public static final double WARLORD_SPIKES_DAMAGE = 14.0;
	public static final int WARLORD_WARCRY_HIT_TICK = 12, WARLORD_WARCRY_TOTAL = 32, WARLORD_WARCRY_COOLDOWN = 400;
	public static final int WARLORD_BLINK_TICK = 9, WARLORD_BLINK_TOTAL = 20, WARLORD_BLINK_COOLDOWN = 160;

	// =================================================================== Shard Altar (summoning) and the shard economy
	/** Wyrm Shards the altar must be fed before the ritual starts; also the range of the altar's {@code charge} block state. */
	public static final int ALTAR_SHARD_COST = 12;
	/** The ritual advances one step every {@link #ALTAR_RITUAL_STEP_TICKS}; the dragon rises after the last step (5 s). */
	public static final int ALTAR_RITUAL_STEPS = 20, ALTAR_RITUAL_STEP_TICKS = 5;
	/** The dragon rises this many blocks in front of the altar (the arena circle of the generated shrine). */
	public static final int ALTAR_SUMMON_DISTANCE = 9;
	/** A ritual cannot be completed while a Shard Dragon is alive this close to the altar; players this close hear the ritual. */
	public static final double ALTAR_DRAGON_EXCLUSION_RADIUS = 256.0, ALTAR_ANNOUNCE_RADIUS = 96.0;
	/** Completing the ritual levels and corrupts the land around the altar into the dragon's arena (world.ArenaTerraformer). */
	public static final boolean ALTAR_TERRAFORM = true;
	/**
	 * Flat arena radius around the altar, the ring that slopes back to the original terrain, and the untouched core around
	 * the altar itself (the shrine's dais, obelisks and chest).
	 */
	public static final int ARENA_RADIUS = 48, ARENA_BLEND = 12, ARENA_PROTECT_RADIUS = 7;
	/** Obsidian spires left standing as cover from Cataclysm - obsidian is too hard for the dragon to crush. */
	public static final int ARENA_SPIRES = 14;
	/** Holes under the new floor are filled at most this deep; block changes per tick (the shockwave takes a few seconds). */
	public static final int ARENA_FILL_DEPTH = 24, ARENA_BLOCKS_PER_TICK = 8000;
	/**
	 * One drop rule for every hostile mob - natural spawns and raid minions / the mid-boss alike: a single shard with this
	 * chance when a player lands the kill, plus {@link #SHARD_DROP_LOOTING_BONUS} per Looting level.
	 */
	public static final float SHARD_DROP_CHANCE = 0.10F, SHARD_DROP_LOOTING_BONUS = 0.03F;
}
