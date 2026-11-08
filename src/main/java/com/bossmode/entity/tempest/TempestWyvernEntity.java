package com.bossmode.entity.tempest;

import com.bossmode.config.TempestConfig;
import com.bossmode.entity.action.BossAction;
import com.bossmode.entity.boss.combat.CombatUtil;
import com.bossmode.entity.raid.RaidBossEntity;
import com.bossmode.entity.raid.RaidBossSpec;
import com.bossmode.entity.raid.RaidPhase;
import com.bossmode.entity.tempest.attack.CycloneAttack;
import com.bossmode.entity.tempest.attack.DiveBombAttack;
import com.bossmode.entity.tempest.attack.FeatherBarrageAttack;
import com.bossmode.entity.tempest.attack.LightningBreathAttack;
import com.bossmode.entity.tempest.attack.StaticFieldAttack;
import com.bossmode.entity.tempest.attack.StormBoltsAttack;
import com.bossmode.entity.tempest.attack.TailWhipAttack;
import com.bossmode.entity.tempest.attack.TalonStrikeAttack;
import com.bossmode.entity.tempest.attack.TempestSummonAttack;
import com.bossmode.entity.tempest.attack.ThunderRoarAttack;
import com.bossmode.entity.tempest.attack.WindGustAttack;
import com.bossmode.entity.tempest.attack.WyvernCrashAction;
import com.bossmode.entity.tempest.attack.WyvernLandAction;
import com.bossmode.entity.tempest.attack.WyvernTakeoffAction;
import com.bossmode.registry.ModSounds;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The Tempest Wyvern - the flying raid boss, called at the Tempest Altar with Wyrm Shards; drops the Tempest Eye.
 * <p>
 * Five 1,024 HP phases ({@link TempestPhase}). It fights from the sky: it circles its target out of melee reach,
 * pelting it with feathers, lightning and gusts, and only comes down to dive, to land for a while, or when it takes
 * {@link TempestConfig#SHOOT_DOWN_FRACTION} of its pool during one flight - then it crashes and lies stunned (EXPOSED).
 * No regeneration while airborne. Everything is decided on the logical server.
 */
public class TempestWyvernEntity extends RaidBossEntity<TempestWyvernEntity> {
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(TempestWyvernEntity.class, EntityDataSerializers.BOOLEAN);
	public static final DustParticleOptions STORM_DUST = new DustParticleOptions(0x4FE0FF, 1.8F);
	public static final DustParticleOptions CLOUD_DUST = new DustParticleOptions(0xB8C8D8, 2.4F);

	/** Flight maneuvers: forced by the flight cycle or a shoot-down, never picked at random. */
	public static final Set<String> FLIGHT_ACTIONS = Set.of("takeoff", "land", "crash");
	/** What it can do while airborne. */
	public static final Set<String> AIR_ACTIONS = Set.of("wind_gust", "lightning_breath", "storm_bolts", "dive_bomb", "cyclone", "feather_barrage",
			"thunder_roar", "static_field", "summon");
	/** Actions that steer the body themselves (no hovering meanwhile). */
	private static final Set<String> SELF_MOVING = Set.of("takeoff", "land", "crash", "dive_bomb");
	public static final String[] ACTION_CLIPS = {"talon_strike", "tail_whip", "wind_gust", "lightning_breath", "storm_bolts", "dive_bomb", "cyclone",
			"feather_barrage", "thunder_roar", "static_field", "summon", "takeoff", "land", "crash"};

	public static final RaidBossSpec SPEC = new RaidBossSpec("tempest_wyvern", TempestConfig.BASE_SCALE, TempestConfig.REGEN_SHARE_PER_SECOND,
			TempestConfig.REGEN_DELAY, TempestConfig.FIRST_PHASE_HEALTH, TempestConfig.LAST_PHASE_HEALTH, TempestConfig.SPAWN_INTRO_TICKS, TempestConfig.SPAWN_ROAR_TICK, TempestConfig.PHASE_TRANSITION_TICKS,
			TempestConfig.TRANSITION_SHOCKWAVE_RADIUS, TempestConfig.DEATH_ANIMATION_TICKS, TempestConfig.DEATH_EXPLOSION_POWER,
			TempestConfig.ENRAGE_HEALTH_THRESHOLD, TempestConfig.ENRAGE_DAMAGE_MULTIPLIER, TempestConfig.ENRAGE_SPEED_MULTIPLIER,
			TempestConfig.ENRAGE_COOLDOWN_REDUCTION, TempestConfig.BOSS_BAR_RANGE, BossEvent.BossBarOverlay.NOTCHED_6, TempestConfig.NORMAL_ATTACK_CHANCE,
			TempestConfig.GLOBAL_COOLDOWN_AFTER_ATTACK, TempestConfig.GLOBAL_COOLDOWN_AFTER_SPECIAL, TempestConfig.MAX_ACTIVE_MINIONS,
			TempestConfig.MAX_TURN_PER_TICK, TempestConfig.XP_REWARD, STORM_DUST, ModSounds.TEMPEST_WYVERN_SPAWN, ModSounds.TEMPEST_WYVERN_ROAR,
			ModSounds.TEMPEST_WYVERN_AMBIENT, ModSounds.TEMPEST_WYVERN_HURT, ModSounds.TEMPEST_WYVERN_DEATH, ACTION_CLIPS);
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop(SPEC.clip("fly"));

	private final List<Whirlwind> whirlwinds = new ArrayList<>();
	/** Ticks since the last takeoff / touchdown. Starts so that it takes wing shortly after the intro. */
	private int stanceTicks = TempestConfig.GROUND_TICKS - TempestConfig.FIRST_GROUND_TICKS;
	/** Damage taken during the current flight (shoot-down meter). */
	private float airDamage;
	private double orbitAngle;
	private int orbitDirection = 1;

	public TempestWyvernEntity(EntityType<? extends TempestWyvernEntity> type, Level level) {
		super(type, level, SPEC);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, TempestConfig.FIRST_PHASE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, TempestConfig.ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, TempestConfig.MOVEMENT_SPEED)
				.add(Attributes.ARMOR, TempestConfig.ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, TempestConfig.ARMOR_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, TempestConfig.KNOCKBACK_RESISTANCE)
				.add(Attributes.ATTACK_KNOCKBACK, TempestConfig.ATTACK_KNOCKBACK)
				.add(Attributes.FOLLOW_RANGE, TempestConfig.FOLLOW_RANGE)
				.add(Attributes.STEP_HEIGHT, TempestConfig.STEP_HEIGHT)
				.add(Attributes.SCALE, TempestConfig.BASE_SCALE);
	}

	@Override
	protected List<BossAction<TempestWyvernEntity>> createActions() {
		return List.of(new TalonStrikeAttack(), new TailWhipAttack(), new WindGustAttack(), new LightningBreathAttack(), new StormBoltsAttack(),
				new DiveBombAttack(), new CycloneAttack(), new FeatherBarrageAttack(), new ThunderRoarAttack(), new StaticFieldAttack(),
				new TempestSummonAttack(), new WyvernTakeoffAction(), new WyvernLandAction(), new WyvernCrashAction());
	}

	@Override
	public RaidPhase phase(int number) {
		return TempestPhase.byNumber(number);
	}

	@Override
	public int maxPhase() {
		return TempestPhase.MAX;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLYING, false);
	}

	@Override
	public boolean isActionAllowed(String id) {
		if (FLIGHT_ACTIONS.contains(id) || !super.isActionAllowed(id)) {
			return false;
		}
		return isFlying() ? AIR_ACTIONS.contains(id) : !"dive_bomb".equals(id);
	}

	/** Where breath and feathers leave the Wyvern. */
	public Vec3 mouthPosition() {
		return position().add(CombatUtil.horizontalForward(this).scale(1.2 * getScale())).add(0, 2.7 * getScale(), 0);
	}

	// ================================================================================================ flight
	public boolean isFlying() {
		return this.entityData.get(DATA_FLYING);
	}

	/** Switches stance (takeoff, landing, crash, dive impact, death) and restarts the stance timer. */
	public void setFlying(boolean flying) {
		this.entityData.set(DATA_FLYING, flying);
		setNoGravity(flying);
		this.stanceTicks = 0;
		this.airDamage = 0.0F;
		LivingEntity target = getTarget();
		if (flying && target != null) {
			this.orbitAngle = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
		}
	}

	/** Ticks since the last takeoff (0 on the ground). */
	public int airTicks() {
		return isFlying() ? this.stanceTicks : 0;
	}

	@Override
	public boolean canChase() {
		return !isFlying();
	}

	@Override
	public double chaseStopGap() {
		return TempestConfig.TALON_RANGE * sizeFactor() * 0.6;
	}

	@Override
	protected boolean canRegenerate() {
		return !isFlying();
	}

	/** Top of whatever is below the Wyvern. */
	public double groundBelow(ServerLevel level) {
		Vec3 from = position().add(0, 0.5, 0);
		HitResult hit = level.clip(new ClipContext(from, from.add(0, -128, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		return hit.getType() == HitResult.Type.MISS ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ())
				: hit.getLocation().y;
	}

	/** Cruising height: {@link TempestConfig#FLY_ALTITUDE} above the ground below, or above the target if that stands higher. */
	public double flightAltitude(ServerLevel level, @Nullable LivingEntity target) {
		double ground = groundBelow(level);
		return Math.max(ground, target != null ? target.getY() : ground) + TempestConfig.FLY_ALTITUDE;
	}

	@Override
	protected void tickMechanics(ServerLevel level) {
		tickFlight(level);
		tickWhirlwinds(level);
	}

	private void tickFlight(ServerLevel level) {
		this.stanceTicks++;
		LivingEntity target = getTarget();
		boolean free = getBossState().allowsFreeMovement();
		if (!isFlying()) {
			if (free && target != null && this.stanceTicks >= TempestConfig.GROUND_TICKS && (onGround() || isInWater()
					|| this.stanceTicks >= TempestConfig.GROUND_TICKS + 60)) {
				this.actions.forceAction(level, "takeoff");
			}
			return;
		}
		String current = this.actions.currentActionId();
		if (free) {
			if (target == null || this.stanceTicks >= TempestConfig.AIR_TICKS) {
				endFlight(level, target);
			} else {
				steerFlight(level, target);
			}
		} else if (current == null || !SELF_MOVING.contains(current)) {
			hover(level, target);
		}
		if (this.tickCount % 18 == 0) {
			level.playSound(null, getX(), getY(), getZ(), ModSounds.TEMPEST_WYVERN_FLAP, SoundSource.HOSTILE, 2.5F, 0.8F + getRandom().nextFloat() * 0.2F);
		}
	}

	/** Comes down: onto the target with a Dive Bomb (when the phase has it), otherwise a landing. */
	private void endFlight(ServerLevel level, @Nullable LivingEntity target) {
		boolean dive = target != null && getPhase().attacks().contains("dive_bomb") && getRandom().nextDouble() < TempestConfig.LAND_DIVE_CHANCE
				&& gapTo(target) >= TempestConfig.DIVE_MIN_RANGE && gapTo(target) <= TempestConfig.DIVE_MAX_RANGE;
		this.actions.forceAction(level, dive ? "dive_bomb" : "land");
	}

	/** Circles its target at cruising height, facing it, turning around now and then; climbs over anything in the way. */
	private void steerFlight(ServerLevel level, LivingEntity target) {
		double radius = TempestConfig.FLY_ORBIT_RADIUS + getBbWidth() * 0.5;
		this.orbitAngle += TempestConfig.FLY_ORBIT_SPEED * this.orbitDirection;
		if (getRandom().nextInt(200) == 0) {
			this.orbitDirection = -this.orbitDirection;
		}
		double x = target.getX() + Math.cos(this.orbitAngle) * radius;
		double z = target.getZ() + Math.sin(this.orbitAngle) * radius;
		double ground = Math.max(groundBelow(level), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)));
		double y = Math.max(ground, target.getY()) + TempestConfig.FLY_ALTITUDE;
		Vec3 offset = new Vec3(x - getX(), y - getY(), z - getZ());
		double speed = TempestConfig.FLY_SPEED * getPhase().speedMultiplier() * (isEnraged() ? TempestConfig.ENRAGE_SPEED_MULTIPLIER : 1.0);
		Vec3 wanted = offset.lengthSqr() < 1.0E-4 ? Vec3.ZERO : offset.normalize().scale(Math.min(speed, offset.length() * 0.12));
		if (this.horizontalCollision) {
			wanted = wanted.add(0, 0.35, 0);
			if (this.tickCount % 5 == 0) {
				crushObstacles(level, CombatUtil.horizontalForward(this));
			}
		}
		setDeltaMovement(getDeltaMovement().lerp(wanted, 0.15));
		faceTarget(target, TempestConfig.MAX_TURN_PER_TICK);
	}

	/** Holds position in the air while an attack plays. */
	private void hover(ServerLevel level, @Nullable LivingEntity target) {
		Vec3 v = getDeltaMovement();
		double climb = Mth.clamp((flightAltitude(level, target) - getY()) * 0.05, -0.2, 0.2);
		setDeltaMovement(v.x * 0.6, climb, v.z * 0.6);
	}

	@Override
	protected void afterHurt(ServerLevel level, DamageSource source, float dealt) {
		if (isFlying() && !getBossState().isInvulnerable()) {
			this.airDamage += dealt;
			if (this.airDamage >= getMaxHealth() * TempestConfig.SHOOT_DOWN_FRACTION && !"crash".equals(this.actions.currentActionId())) {
				this.actions.forceAction(level, "crash");
				announce(level, Component.translatable("message.boss_mode.tempest.shot_down"));
			}
		}
	}

	/** Shoot-down meter of the current flight, 0..1 (tests / debugging). */
	public float airDamageFraction() {
		return this.airDamage / (getMaxHealth() * TempestConfig.SHOOT_DOWN_FRACTION);
	}

	// ================================================================================================ whirlwinds (Cyclone)
	/** Whirlwinds roam toward the nearest enemy, drag everything close in and fling what they catch into the air. */
	private static final class Whirlwind {
		private Vec3 position;
		private int age;

		private Whirlwind(Vec3 position) {
			this.position = position;
		}
	}

	public void spawnWhirlwind(ServerLevel level, Vec3 at) {
		this.whirlwinds.add(new Whirlwind(new Vec3(at.x, CombatUtil.groundY(level, at.x, at.z), at.z)));
	}

	public int whirlwindCount() {
		return this.whirlwinds.size();
	}

	public void clearWhirlwinds() {
		this.whirlwinds.clear();
	}

	private void tickWhirlwinds(ServerLevel level) {
		Iterator<Whirlwind> it = this.whirlwinds.iterator();
		while (it.hasNext()) {
			Whirlwind wind = it.next();
			if (++wind.age > TempestConfig.CYCLONE_LIFETIME) {
				level.sendParticles(ParticleTypes.CLOUD, wind.position.x, wind.position.y + 1.5, wind.position.z, 30, 1.0, 1.5, 1.0, 0.1);
				it.remove();
				continue;
			}
			LivingEntity chase = null;
			double best = 32.0 * 32.0;
			for (LivingEntity e : CombatUtil.targetsInRadius(level, this, wind.position, 32.0)) {
				double d = e.distanceToSqr(wind.position);
				if (d < best) {
					best = d;
					chase = e;
				}
			}
			if (chase != null) {
				Vec3 to = chase.position().subtract(wind.position).multiply(1, 0, 1);
				if (to.lengthSqr() > 0.01) {
					Vec3 next = wind.position.add(to.normalize().scale(TempestConfig.CYCLONE_SPEED));
					wind.position = new Vec3(next.x, CombatUtil.groundY(level, next.x, next.z), next.z);
				}
			}
			double radius = TempestConfig.CYCLONE_RADIUS * sizeFactor();
			if (wind.age % 2 == 0) {
				for (int i = 0; i < 6; i++) {
					double h = (wind.age * 0.37 + i) % 6.0;
					double a = wind.age * 0.6 + i * 1.7;
					double r = 0.4 + h * radius / 6.0;
					level.sendParticles(ParticleTypes.CLOUD, wind.position.x + Math.cos(a) * r, wind.position.y + h, wind.position.z + Math.sin(a) * r,
							0, -Math.sin(a), 0.2, Math.cos(a), 0.3);
				}
				level.sendParticles(CLOUD_DUST, wind.position.x, wind.position.y + 1.0, wind.position.z, 3, radius * 0.4, 1.0, radius * 0.4, 0);
			}
			if (wind.age % 20 == 0) {
				level.playSound(null, wind.position.x, wind.position.y, wind.position.z, ModSounds.TEMPEST_WYVERN_CYCLONE, SoundSource.HOSTILE, 1.5F, 1.0F);
			}
			float damage = (float) (TempestConfig.CYCLONE_DAMAGE * getDamageMultiplier());
			for (LivingEntity victim : CombatUtil.targetsInRadius(level, this, wind.position.add(0, 1.0, 0), TempestConfig.CYCLONE_PULL_RADIUS * sizeFactor())) {
				double dx = victim.getX() - wind.position.x, dz = victim.getZ() - wind.position.z;
				if (dx * dx + dz * dz <= radius * radius) {
					if (wind.age % 10 == 0 && victim.hurtServer(level, damageSources().mobAttack(this), damage)) {
						victim.setDeltaMovement(victim.getDeltaMovement().x * 0.3, 0.9, victim.getDeltaMovement().z * 0.3);
						victim.needsSync = true;
					}
				} else {
					CombatUtil.pull(victim, wind.position.add(0, victim.getY() - wind.position.y, 0), TempestConfig.CYCLONE_PULL);
				}
			}
		}
	}

	// ================================================================================================ lifecycle hooks
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			clearWhirlwinds();
			if (isFlying()) {
				setFlying(false); // the body falls out of the sky
			}
		}
	}

	@Override
	protected RawAnimation stanceAnimation(AnimationTest<TempestWyvernEntity> test) {
		return isFlying() ? FLY : super.stanceAnimation(test);
	}

	@Override
	protected void saveMechanics(ValueOutput output) {
		output.putBoolean("Flying", isFlying());
		output.putInt("StanceTicks", this.stanceTicks);
	}

	@Override
	protected void loadMechanics(ValueInput input) {
		boolean flying = input.getBooleanOr("Flying", false);
		this.entityData.set(DATA_FLYING, flying);
		setNoGravity(flying);
		this.stanceTicks = input.getIntOr("StanceTicks", this.stanceTicks);
	}
}
