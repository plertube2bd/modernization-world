package com.bossmode.entity.boss;

import com.bossmode.entity.PhaseHealth;
import com.bossmode.config.BossConfig;
import com.bossmode.entity.DamageKind;
import com.bossmode.entity.DecoratedBoss;
import com.bossmode.entity.ShardFaction;
import com.bossmode.entity.action.ActionController;
import com.bossmode.entity.action.ActionHost;
import com.bossmode.entity.action.BossAction;
import com.bossmode.entity.boss.ai.DragonChaseGoal;
import com.bossmode.entity.boss.combat.BiteAttack;
import com.bossmode.entity.boss.combat.CataclysmAttack;
import com.bossmode.entity.boss.combat.CombatUtil;
import com.bossmode.entity.boss.combat.CrashLandingAction;
import com.bossmode.entity.boss.combat.CrystalRainAttack;
import com.bossmode.entity.boss.combat.LandingAction;
import com.bossmode.entity.boss.combat.MeteorDiveAttack;
import com.bossmode.entity.boss.combat.ObsidianChargeAttack;
import com.bossmode.entity.boss.combat.PrismBeamAttack;
import com.bossmode.entity.boss.combat.ShardVolleyAttack;
import com.bossmode.entity.boss.combat.ShatterSlamAttack;
import com.bossmode.entity.boss.combat.SpikeEruptionAttack;
import com.bossmode.entity.boss.combat.SummonAttack;
import com.bossmode.entity.boss.combat.TailSweepAttack;
import com.bossmode.entity.boss.combat.TakeoffAction;
import com.bossmode.entity.boss.combat.VoidRoarAttack;
import com.bossmode.entity.boss.combat.WingTempestAttack;
import com.bossmode.entity.minion.MinionType;
import com.bossmode.entity.minion.ShardHoundEntity;
import com.bossmode.entity.warlord.ShardWarlordEntity;
import com.bossmode.registry.ModEntities;
import com.bossmode.registry.ModSounds;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Shard Dragon - a ten-phase boss.
 * <p>
 * From phase 6 it alternates between the ground and the air ({@link BossPhase#airTicks()} / {@link BossPhase#groundTicks()}):
 * it takes off, circles high above its target using only ranged patterns, then lands or meteor-dives back down. While
 * it flies, projectiles always hurt it (even in the melee-only phases), it does not regenerate, and taking
 * {@link BossConfig#SHOOT_DOWN_FRACTION} of its pool during one flight shoots it down: it crashes and lies EXPOSED.
 * <p>
 * Server authority: health pools, AI, targeting, phases, growth, attacks, cooldowns, projectiles, minions, mid-bosses,
 * damage sealing and the boss bar are all decided here on the logical server. Clients only receive synced state
 * (boss state, phase, enrage flag, scale attribute, health) plus GeckoLib animation triggers, used for rendering
 * and the decorated boss bar.
 */
public class ShardDragonEntity extends Monster implements ActionHost, ShardFaction, DecoratedBoss {
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(ShardDragonEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(ShardDragonEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(ShardDragonEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(ShardDragonEntity.class, EntityDataSerializers.BOOLEAN);

	/** Flight maneuvers: forced by the flight cycle or a shoot-down, never picked at random. */
	public static final Set<String> FLIGHT_ACTIONS = Set.of("takeoff", "land", "crash");
	/** Patterns that work from the air; the others need the ground under the dragon's feet. */
	public static final Set<String> AIR_ACTIONS = Set.of("shard_volley", "wing_tempest", "crystal_rain", "prism_beam", "void_roar", "meteor_dive",
			"cataclysm", "summon");
	/** Actions that move the dragon themselves - the hover control keeps out of their way. */
	private static final Set<String> SELF_MOVING = Set.of("takeoff", "land", "crash", "meteor_dive");

	private static final Identifier COMBAT_SPEED_MODIFIER = com.bossmode.BossMode.id("combat_speed");
	private static final DustParticleOptions SHARD_DUST = new DustParticleOptions(0xB57CFF, 2.0F);
	private static final DustParticleOptions EMBER_DUST = new DustParticleOptions(0xFF4060, 2.2F);
	/** Death explosion: hurts living things nearby but never destroys or scatters the dropped loot / XP. */
	private static final ExplosionDamageCalculator LOOT_SAFE_EXPLOSION = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return !(entity instanceof ItemEntity || entity instanceof ExperienceOrb) && super.shouldDamageEntity(explosion, entity);
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return entity instanceof ItemEntity || entity instanceof ExperienceOrb ? 0.0F : super.getKnockbackMultiplier(entity);
		}
	};

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private final BossBarController bossBar;
	private final ActionController<ShardDragonEntity> actions;
	private final Map<UUID, Integer> deflectMessageTicks = new HashMap<>();
	private boolean introFinished;
	private int stateTicks;
	private boolean targetUnreachable;
	private int hurtAnimationCooldown;
	private int lastHurtTick;
	private boolean phaseBreakPending;
	private boolean allowDeath;
	private int lastEntryWavePhase = 1;
	private float transitionFromScale = BossConfig.BASE_SCALE;
	private float transitionFromHealth;
	private boolean transitionRefills;
	private int lastDeflectSoundTick;
	/** Ticks since the last takeoff / landing. */
	private int stanceTicks;
	/** Damage taken during the current flight (shoot-down meter). */
	private float airDamage;
	private double orbitAngle;
	private int orbitDirection = 1;

	public ShardDragonEntity(EntityType<? extends ShardDragonEntity> type, Level level) {
		super(type, level);
		this.xpReward = BossConfig.XP_REWARD;
		this.bossBar = new BossBarController(this, BossConfig.BOSS_BAR_RANGE, BossConfig.BOSS_BAR_MEMBERSHIP_INTERVAL,
				BossPhase.ONE.barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
		this.actions = new ActionController<>(this, List.<BossAction<ShardDragonEntity>>of(
				new BiteAttack(), new TailSweepAttack(), new ShatterSlamAttack(), new ShardVolleyAttack(), new WingTempestAttack(),
				new ObsidianChargeAttack(), new CrystalRainAttack(), new SpikeEruptionAttack(), new PrismBeamAttack(), new VoidRoarAttack(),
				new MeteorDiveAttack(), new CataclysmAttack(), new SummonAttack(), new TakeoffAction(), new LandingAction(),
				new CrashLandingAction()),
				CombatUtil::isValidTarget, BossConfig.NORMAL_ATTACK_CHANCE, 60,
				BossConfig.GLOBAL_COOLDOWN_AFTER_ATTACK, BossConfig.GLOBAL_COOLDOWN_AFTER_SPECIAL);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BossConfig.FIRST_PHASE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, BossConfig.ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, BossConfig.MOVEMENT_SPEED)
				.add(Attributes.ARMOR, BossConfig.ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, BossConfig.ARMOR_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, BossConfig.KNOCKBACK_RESISTANCE)
				.add(Attributes.ATTACK_KNOCKBACK, BossConfig.ATTACK_KNOCKBACK)
				.add(Attributes.FOLLOW_RANGE, BossConfig.FOLLOW_RANGE)
				.add(Attributes.STEP_HEIGHT, BossConfig.STEP_HEIGHT * BossConfig.BASE_SCALE)
				.add(Attributes.SCALE, BossConfig.BASE_SCALE);
	}

	/** Programmatic spawn API (commands, events, other mods). Plays the spawn intro. */
	public static @Nullable ShardDragonEntity spawnBoss(ServerLevel level, BlockPos pos) {
		return ModEntities.SHARD_DRAGON.spawn(level, pos, EntitySpawnReason.EVENT);
	}

	// ================================================================================================ synced state
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) BossState.SPAWNING.ordinal());
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_ENRAGED, false);
		builder.define(DATA_FLYING, false);
	}

	@Override
	public BossState getBossState() {
		return BossState.byId(this.entityData.get(DATA_STATE));
	}

	@Override
	public void setBossState(BossState state) {
		if (getBossState() != state) {
			this.entityData.set(DATA_STATE, (byte) state.ordinal());
			this.stateTicks = 0;
		}
	}

	public BossPhase getPhase() {
		return BossPhase.byNumber(this.entityData.get(DATA_PHASE));
	}

	/** The health pool of {@code phase}: 100 in phase 1, rising to 300 in phase 10. */
	public static double phasePool(BossPhase phase) {
		return PhaseHealth.pool(phase.number(), BossPhase.values().length, BossConfig.FIRST_PHASE_HEALTH, BossConfig.LAST_PHASE_HEALTH);
	}

	private void setPhase(BossPhase phase) {
		this.entityData.set(DATA_PHASE, (byte) phase.number());
	}

	public boolean isEnraged() {
		return this.entityData.get(DATA_ENRAGED);
	}

	/** Recovering from an attack or lying shot down - damage bonus in phases 9-10. */
	public boolean isExposed() {
		BossState state = getBossState();
		return state == BossState.ATTACK_RECOVERY || state == BossState.SPECIAL_RECOVERY;
	}

	public ActionController<ShardDragonEntity> getActionController() {
		return this.actions;
	}

	public BossBarController getBossBar() {
		return this.bossBar;
	}

	public boolean isIntroFinished() {
		return this.introFinished;
	}

	public boolean isTargetUnreachable() {
		return this.targetUnreachable;
	}

	public void setTargetUnreachable(boolean unreachable) {
		this.targetUnreachable = unreachable;
	}

	/** Size relative to phase 1 (1.0 -> 2.0); attack ranges and radii grow with it. */
	public double sizeFactor() {
		return getScale() / BossConfig.BASE_SCALE;
	}

	public double getDamageMultiplier() {
		return getPhase().damageMultiplier() * (isEnraged() ? BossConfig.ENRAGE_DAMAGE_MULTIPLIER : 1.0);
	}

	@Override
	public double getCooldownMultiplier() {
		return getPhase().cooldownMultiplier() * (isEnraged() ? 1.0 - BossConfig.ENRAGE_COOLDOWN_REDUCTION : 1.0);
	}

	@Override
	public boolean isActionAllowed(String id) {
		if (FLIGHT_ACTIONS.contains(id) || !getPhase().attacks().contains(id)) {
			return false;
		}
		return !isFlying() || AIR_ACTIONS.contains(id);
	}

	private void applyCombatModifiers() {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			double multiplier = getPhase().speedMultiplier() * (isEnraged() ? BossConfig.ENRAGE_SPEED_MULTIPLIER : 1.0);
			speed.addOrUpdateTransientModifier(new AttributeModifier(COMBAT_SPEED_MODIFIER, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private void setBodyScale(float scale) {
		AttributeInstance scaleAttribute = getAttribute(Attributes.SCALE);
		AttributeInstance step = getAttribute(Attributes.STEP_HEIGHT);
		if (scaleAttribute != null && Math.abs(scaleAttribute.getBaseValue() - scale) > 1.0E-3) {
			scaleAttribute.setBaseValue(scale);
		}
		if (step != null) {
			step.setBaseValue(Math.min(10.0, BossConfig.STEP_HEIGHT * scale));
		}
	}

	// ================================================================================================ AI
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new DragonChaseGoal(this));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ShardDragonEntity.class, ShardHoundEntity.class, ShardWarlordEntity.class, Vex.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(target, level) -> CombatUtil.isValidTarget(this, target)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false,
				(target, level) -> CombatUtil.isValidTarget(this, target)));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.hurtAnimationCooldown > 0) {
			this.hurtAnimationCooldown--;
		}
		if (level() instanceof ServerLevel level && !isDeadOrDying()) {
			this.bossBar.tick(level);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.stateTicks++;
		BossState state = getBossState();
		boolean free = state.allowsFreeMovement();
		this.goalSelector.setControlFlag(Goal.Flag.MOVE, free);
		this.goalSelector.setControlFlag(Goal.Flag.LOOK, free);
		switch (state) {
			case SPAWNING -> tickIntro(level);
			case PHASE_TRANSITION -> tickTransition(level);
			case DYING -> {
			}
			default -> {
				if (this.phaseBreakPending) {
					this.phaseBreakPending = false;
					startTransition(level, getPhase().next(), false);
				} else if (!checkEnrage(level)) {
					tickFlight(level);
					regenerate(level);
					this.actions.tick(level);
				}
			}
		}
	}

	private void tickIntro(ServerLevel level) {
		getNavigation().stop();
		setDeltaMovement(0, getDeltaMovement().y, 0);
		int t = this.stateTicks;
		if (t == 1) {
			setBodyScale(getPhase().scale());
			playSound(ModSounds.SHARD_DRAGON_SPAWN, 5.0F, 1.0F);
		}
		if (t <= 24 && t % 2 == 0) {
			BlockState ground = level.getBlockState(blockPosition().below());
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.OBSIDIAN.defaultBlockState() : ground);
			level.sendParticles(debris, getX(), getY() + 0.2, getZ(), 24, getBbWidth() * 0.6, 0.1, getBbWidth() * 0.6, 0.2);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.5, getZ(), 16, getBbWidth() * 0.5, 0.3, getBbWidth() * 0.5, 0.1);
		}
		if (t == BossConfig.SPAWN_ROAR_TICK) {
			playSound(ModSounds.SHARD_DRAGON_ROAR, 6.0F, 0.9F);
			shockwaveParticles(level, false);
		}
		if (t >= BossConfig.SPAWN_INTRO_TICKS) {
			this.introFinished = true;
			setBossState(BossState.IDLE);
		}
	}

	private boolean checkEnrage(ServerLevel level) {
		if (!getPhase().isLast() || isEnraged() || getHealth() / getMaxHealth() > BossConfig.ENRAGE_HEALTH_THRESHOLD) {
			return false;
		}
		startTransition(level, getPhase(), true);
		return true;
	}

	private void regenerate(ServerLevel level) {
		if (isFlying()) {
			return; // flying costs everything it has - no crystal regrowth in the air
		}
		if (this.tickCount - this.lastHurtTick > BossConfig.REGEN_DELAY && getHealth() < getMaxHealth()) {
			heal(getMaxHealth() * BossConfig.REGEN_SHARE_PER_SECOND / 20.0F);
			if (this.tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.GLOW, getX(), getY(0.6), getZ(), 6, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
			}
		}
	}

	/** Advances to {@code phase} (or roars into enrage): invulnerable roar, growth, health refill, entry wave. */
	private void startTransition(ServerLevel level, BossPhase phase, boolean enrage) {
		this.actions.cancel(level);
		BossPhase old = getPhase();
		this.transitionFromScale = getScale();
		this.transitionFromHealth = getHealth();
		this.transitionRefills = phase != old;
		if (phase != old) {
			setPhase(phase);
			PhaseHealth.apply(this, phasePool(phase), false);
			this.bossBar.setStyle(phase.barColor(), phase.darkensSky());
			announcePhase(level, old, phase);
		}
		if (enrage) {
			this.entityData.set(DATA_ENRAGED, true);
			announce(level, Component.translatable("message.boss_mode.enraged"));
		}
		applyCombatModifiers();
		setBossState(BossState.PHASE_TRANSITION);
		getNavigation().stop();
		triggerAnim(ShardDragonAnimations.ACTION_CONTROLLER, ShardDragonAnimations.TRIGGER_ROAR);
		playSound(ModSounds.SHARD_DRAGON_ROAR, 7.0F, enrage ? 0.7F : 0.9F);
		for (LivingEntity victim : CombatUtil.targetsInRadius(level, this, position(), BossConfig.TRANSITION_SHOCKWAVE_RADIUS * sizeFactor())) {
			CombatUtil.knockbackFrom(victim, position(), 1.6, 0.5);
		}
		shockwaveParticles(level, enrage);
	}

	private void tickTransition(ServerLevel level) {
		setDeltaMovement(0, isFlying() ? 0 : getDeltaMovement().y, 0);
		float progress = Math.min(1.0F, this.stateTicks / (float) BossConfig.PHASE_TRANSITION_TICKS);
		setBodyScale(Mth.lerp(progress, this.transitionFromScale, getPhase().scale()));
		if (this.transitionRefills) {
			setHealth(Mth.lerp(progress, this.transitionFromHealth, getMaxHealth()));
		}
		if (this.stateTicks % 5 == 0) {
			level.sendParticles(isEnraged() ? EMBER_DUST : SHARD_DUST, getX(), getY(0.5), getZ(), 16, getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0);
			if (isEnraged()) {
				level.sendParticles(ParticleTypes.FLAME, getX(), getY(0.4), getZ(), 12, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.02);
			}
		}
		if (this.stateTicks == BossConfig.PHASE_TRANSITION_TICKS / 2 && this.lastEntryWavePhase < getPhase().number()) {
			this.lastEntryWavePhase = getPhase().number();
			spawnEntryWave(level);
		}
		if (this.stateTicks >= BossConfig.PHASE_TRANSITION_TICKS) {
			setBodyScale(getPhase().scale());
			if (this.transitionRefills) {
				setHealth(getMaxHealth());
			}
			setBossState(getTarget() != null ? BossState.CHASE : BossState.IDLE);
		}
	}

	/** Minions from the new phase's roster plus the phase's Shard Warlords, spawned mid-roar. */
	private void spawnEntryWave(ServerLevel level) {
		BossPhase phase = getPhase();
		LivingEntity target = getTarget();
		if (!phase.minions().isEmpty()) {
			int count = Math.min(phase.entryWave(), BossConfig.MAX_ACTIVE_MINIONS - countMinions(level));
			for (Vec3 p : SummonAttack.ringAround(level, this, count, getBbWidth() * 0.5 + 4.0)) {
				MinionType type = phase.minions().get(getRandom().nextInt(phase.minions().size()));
				type.spawn(level, this, type == MinionType.WRAITH ? p.add(0, 1.5, 0) : p, target);
			}
		}
		int warlords = Math.min(phase.midBosses(), BossConfig.MAX_ACTIVE_WARLORDS - countWarlords(level));
		for (Vec3 p : SummonAttack.ringAround(level, this, warlords, getBbWidth() * 0.5 + 8.0)) {
			ShardWarlordEntity warlord = ModEntities.SHARD_WARLORD.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (warlord != null) {
				warlord.snapTo(p.x, p.y, p.z, getYRot(), 0.0F);
				warlord.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), EntitySpawnReason.MOB_SUMMONED, null);
				warlord.setSummoner(this);
				warlord.setPool(ShardWarlordEntity.poolFor(phasePool(phase)));
				if (target != null) {
					warlord.setTarget(target);
				}
				level.addFreshEntity(warlord);
			}
		}
		if (warlords > 0) {
			announce(level, Component.translatable("message.boss_mode.warlord_arrives"));
		}
	}

	private void announcePhase(ServerLevel level, BossPhase old, BossPhase phase) {
		StringBuilder sealed = new StringBuilder();
		for (DamageKind kind : DamageKind.values()) {
			if (old.allowedDamage().contains(kind) && !phase.allowedDamage().contains(kind)) {
				sealed.append(sealed.isEmpty() ? "" : ", ").append(Component.translatable("damage_kind.boss_mode." + kind.name().toLowerCase(Locale.ROOT)).getString());
			}
		}
		boolean takesWing = phase.canFly() && !old.canFly();
		String key = takesWing ? "message.boss_mode.phase_flight" : "message.boss_mode.phase";
		Component message = sealed.isEmpty()
				? Component.translatable(key, phase.number())
				: Component.translatable(key + "_sealed", phase.number(), sealed.toString());
		announce(level, message);
	}

	private void announce(ServerLevel level, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceTo(this) <= BossConfig.BOSS_BAR_RANGE) {
				player.sendOverlayMessage(message);
			}
		}
	}

	private void shockwaveParticles(ServerLevel level, boolean enrage) {
		double r = getBbWidth() * 0.6;
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2 * i / 48;
			level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, Math.cos(a), 0.02, Math.sin(a), 0.7);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.5), getZ(), 60, r, getBbHeight() * 0.4, r, 0.2);
		if (enrage) {
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY(1.0), getZ(), 8, r * 0.5, 0.5, r * 0.5, 0);
			level.sendParticles(ParticleTypes.FLAME, getX(), getY(0.4), getZ(), 60, r, getBbHeight() * 0.3, r, 0.1);
		}
	}

	/** Crushes breakable blocks in front of the dragon (only when the mobGriefing rule allows it). */
	public void crushObstacles(ServerLevel level, Vec3 direction) {
		if (!BossConfig.CRUSHES_BLOCKS || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		AABB box = getBoundingBox().expandTowards(direction.scale(1.5));
		int minY = Mth.floor(getY() + 1.0);
		int crushed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), minY, Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && WitherBoss.canDestroy(state) && state.getDestroySpeed(level, pos) >= 0 && state.getDestroySpeed(level, pos) < 30.0F) {
				level.destroyBlock(pos, false, this);
				if (++crushed >= BossConfig.CRUSH_MAX_BLOCKS_PER_TICK) {
					break;
				}
			}
		}
	}

	// ================================================================================================ flight (phases 6-10)
	public boolean isFlying() {
		return this.entityData.get(DATA_FLYING);
	}

	/** Switches stance (takeoff, landing, crash, dive impact, Cataclysm collapse, death) and restarts the stance timer. */
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

	/** Top of whatever is below the dragon. */
	public double groundBelow(ServerLevel level) {
		Vec3 from = position().add(0, 0.5, 0);
		HitResult hit = level.clip(new ClipContext(from, from.add(0, -128, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ())
				: hit.getLocation().y;
	}

	/** Cruising height: {@link BossConfig#FLY_ALTITUDE} above the ground below, or above the target if that stands higher. */
	public double flightAltitude(ServerLevel level, @Nullable LivingEntity target) {
		double ground = groundBelow(level);
		return Math.max(ground, target != null ? target.getY() : ground) + BossConfig.FLY_ALTITUDE;
	}

	/** The air / ground cycle. Runs with the action state machine (never during the intro, a phase roar or death). */
	private void tickFlight(ServerLevel level) {
		this.stanceTicks++;
		BossPhase phase = getPhase();
		LivingEntity target = getTarget();
		boolean free = getBossState().allowsFreeMovement();
		if (!isFlying()) {
			if (free && phase.canFly() && target != null && onGround() && this.stanceTicks >= phase.groundTicks()) {
				this.actions.forceAction(level, "takeoff");
			}
			return;
		}
		String current = this.actions.currentActionId();
		if (free) {
			if (target == null || !phase.canFly() || this.stanceTicks >= phase.airTicks()) {
				endFlight(level, target);
			} else {
				steerFlight(level, target);
			}
		} else if (current == null || !SELF_MOVING.contains(current)) {
			hover(level, target);
		}
	}

	/** Comes down: sometimes straight onto the target with a Meteor Dive (phases that have it), otherwise a landing. */
	private void endFlight(ServerLevel level, @Nullable LivingEntity target) {
		boolean dive = target != null && getPhase().attacks().contains("meteor_dive") && getRandom().nextDouble() < BossConfig.LAND_DIVE_CHANCE
				&& distanceTo(target) >= BossConfig.DIVE_MIN_RANGE && distanceTo(target) <= BossConfig.DIVE_MAX_RANGE;
		this.actions.forceAction(level, dive ? "meteor_dive" : "land");
	}

	/** Circles its target at cruising height, facing it, turning around now and then; climbs over anything in the way. */
	private void steerFlight(ServerLevel level, LivingEntity target) {
		double radius = BossConfig.FLY_ORBIT_RADIUS + getBbWidth() * 0.5;
		this.orbitAngle += BossConfig.FLY_ORBIT_SPEED * this.orbitDirection;
		if (getRandom().nextInt(240) == 0) {
			this.orbitDirection = -this.orbitDirection;
		}
		double x = target.getX() + Math.cos(this.orbitAngle) * radius;
		double z = target.getZ() + Math.sin(this.orbitAngle) * radius;
		double ground = Math.max(groundBelow(level), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)));
		double y = Math.max(ground, target.getY()) + BossConfig.FLY_ALTITUDE;
		Vec3 offset = new Vec3(x - getX(), y - getY(), z - getZ());
		double speed = BossConfig.FLY_SPEED * getPhase().speedMultiplier() * (isEnraged() ? BossConfig.ENRAGE_SPEED_MULTIPLIER : 1.0);
		Vec3 wanted = offset.lengthSqr() < 1.0E-4 ? Vec3.ZERO : offset.normalize().scale(Math.min(speed, offset.length() * 0.12));
		if (this.horizontalCollision) {
			wanted = wanted.add(0, 0.35, 0);
			if (this.tickCount % 5 == 0) {
				crushObstacles(level, CombatUtil.horizontalForward(this));
			}
		}
		setDeltaMovement(getDeltaMovement().lerp(wanted, 0.15));
		faceTarget(target, BossConfig.MAX_TURN_PER_TICK);
	}

	/** Holds position in the air while an attack plays. */
	private void hover(ServerLevel level, @Nullable LivingEntity target) {
		Vec3 v = getDeltaMovement();
		double climb = Mth.clamp((flightAltitude(level, target) - getY()) * 0.05, -0.2, 0.2);
		setDeltaMovement(v.x * 0.6, climb, v.z * 0.6);
	}

	// ================================================================================================ minions
	public int countMinions(ServerLevel level) {
		AABB area = getBoundingBox().inflate(BossConfig.MINION_CLEANUP_RADIUS);
		return level.getEntitiesOfClass(ShardHoundEntity.class, area, h -> h.isAlive() && h.isSummonedBy(this)).size()
				+ level.getEntitiesOfClass(Vex.class, area, v -> v.isAlive() && v.getOwner() == this).size();
	}

	public int countWarlords(ServerLevel level) {
		return level.getEntitiesOfClass(ShardWarlordEntity.class, getBoundingBox().inflate(BossConfig.MINION_CLEANUP_RADIUS),
				w -> w.isAlive() && w.isSummonedBy(this)).size();
	}

	private void dismissMinions(ServerLevel level) {
		AABB area = getBoundingBox().inflate(BossConfig.MINION_CLEANUP_RADIUS);
		List<Mob> minions = new java.util.ArrayList<>();
		minions.addAll(level.getEntitiesOfClass(ShardHoundEntity.class, area, h -> h.isSummonedBy(this)));
		minions.addAll(level.getEntitiesOfClass(ShardWarlordEntity.class, area, w -> w.isSummonedBy(this)));
		minions.addAll(level.getEntitiesOfClass(Vex.class, area, v -> v.getOwner() == this));
		for (Mob minion : minions) {
			level.sendParticles(ParticleTypes.POOF, minion.getX(), minion.getY(0.5), minion.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			minion.discard();
		}
	}

	// ================================================================================================ geometry helpers
	public double gapTo(Entity target) {
		return ActionController.gap(this, target);
	}

	public Vec3 frontPoint(double distance) {
		return position().add(CombatUtil.horizontalForward(this).scale(getBbWidth() / 2.0 + distance));
	}

	/** Approximate mouth position of the model (the OBJ head is ~2.1 blocks forward, ~1.8 blocks up at scale 1). */
	public Vec3 mouthPosition() {
		return position().add(CombatUtil.horizontalForward(this).scale(getBbWidth() / 2.0 + 0.7 * getScale())).add(0, 1.8 * getScale(), 0);
	}

	@Override
	public void faceTarget(Entity target, float maxTurn) {
		faceDirection(target.position().subtract(position()), maxTurn);
	}

	public void faceDirection(Vec3 direction) {
		faceDirection(direction, 360.0F);
	}

	private void faceDirection(Vec3 direction, float maxTurn) {
		if (direction.x * direction.x + direction.z * direction.z < 1.0E-6) {
			return;
		}
		float desired = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
		float yaw = Mth.approachDegrees(getYRot(), desired, maxTurn);
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	// ================================================================================================ damage / phases / immunities
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() != null && ShardFaction.isMember(source.getEntity())) {
			return false;
		}
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass && getBossState() != BossState.SPAWNING && getBossState() != BossState.PHASE_TRANSITION) {
			DamageKind kind = DamageKind.classify(source);
			BossPhase phase = getPhase();
			boolean shotInFlight = kind == DamageKind.PROJECTILE && isFlying();
			if (!phase.allowedDamage().contains(kind) && !shotInFlight) {
				deflect(level, source, kind, phase);
				return false;
			}
			if (kind == DamageKind.EXPLOSION) {
				amount *= BossConfig.EXPLOSION_DAMAGE_MULTIPLIER;
			}
			if (isExposed() && phase.number() >= 9) {
				amount *= BossConfig.EXPOSED_DAMAGE_MULTIPLIER;
			}
		}
		this.allowDeath = bypass;
		float healthBefore = getHealth();
		boolean hurt;
		try {
			hurt = super.hurtServer(level, source, amount);
		} finally {
			this.allowDeath = false;
		}
		if (hurt && isAlive()) {
			this.lastHurtTick = this.tickCount;
			level.sendParticles(SHARD_DUST, getX(), getY(0.6), getZ(), 10, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
			if (this.hurtAnimationCooldown <= 0) {
				this.hurtAnimationCooldown = 10;
				triggerAnim(ShardDragonAnimations.HURT_CONTROLLER, ShardDragonAnimations.TRIGGER_HURT);
			}
			if (isFlying() && !this.phaseBreakPending && !getBossState().isInvulnerable()) {
				this.airDamage += Math.max(0.0F, healthBefore - getHealth());
				if (this.airDamage >= getMaxHealth() * BossConfig.SHOOT_DOWN_FRACTION && !"crash".equals(this.actions.currentActionId())) {
					this.actions.forceAction(level, "crash");
					announce(level, Component.translatable("message.boss_mode.shot_down"));
				}
			}
		}
		return hurt;
	}

	/** A sealed damage type bounced off: sparks, a clang and (throttled) a hint for the attacking player. */
	private void deflect(ServerLevel level, DamageSource source, DamageKind kind, BossPhase phase) {
		Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position().add(0, getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.3);
		if (this.tickCount - this.lastDeflectSoundTick > 4) {
			this.lastDeflectSoundTick = this.tickCount;
			playSound(ModSounds.SHARD_DRAGON_DEFLECT, 2.0F, 0.8F + getRandom().nextFloat() * 0.3F);
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			Integer last = this.deflectMessageTicks.get(player.getUUID());
			if (last == null || this.tickCount - last > 40) {
				this.deflectMessageTicks.put(player.getUUID(), this.tickCount);
				String key = kind == DamageKind.PROJECTILE && phase.canFly() ? "projectile_grounded" : kind.name().toLowerCase(Locale.ROOT);
				player.sendOverlayMessage(Component.translatable("message.boss_mode.sealed." + key, phase.number()));
			}
			if (this.deflectMessageTicks.size() > 32) {
				this.deflectMessageTicks.clear();
			}
		}
	}

	/** Each phase is its own pool: a lethal hit before phase 10 leaves 1 HP and breaks into the next phase instead. */
	@Override
	public void setHealth(float health) {
		if (health <= 0.0F && !this.allowDeath && !level().isClientSide() && isAlive() && !getPhase().isLast()) {
			super.setHealth(1.0F);
			this.phaseBreakPending = true;
			return;
		}
		super.setHealth(health);
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.isInvulnerableTo(level, source);
		}
		if (getBossState().isInvulnerable() || this.phaseBreakPending) {
			return true;
		}
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.SLOWNESS) || effect.is(MobEffects.WEAKNESS)) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	// ================================================================================================ death
	@Override
	public void die(DamageSource source) {
		super.die(source); // loot table + XP are dropped here, once, by vanilla
		if (level() instanceof ServerLevel level) {
			this.actions.cancel(level);
			setBossState(BossState.DYING);
			this.bossBar.removeAll();
			stopTriggeredAnim(ShardDragonAnimations.ACTION_CONTROLLER, null);
			getNavigation().stop();
			this.noPhysics = false;
			if (isFlying()) {
				setFlying(false); // the body falls out of the sky
			}
			dismissMinions(level);
			announce(level, Component.translatable("message.boss_mode.defeated"));
		}
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (this.deathTime % 3 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.4), getZ(), 12, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.05);
			level.sendParticles(SHARD_DUST, getX(), getY(0.4), getZ(), 6, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0);
		}
		if (this.deathTime == BossConfig.DEATH_ANIMATION_TICKS - 10) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
					getX(), getY(0.4), getZ(), 160, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.3);
			playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 6.0F, 0.5F);
		}
		if (this.deathTime >= BossConfig.DEATH_ANIMATION_TICKS) {
			if (BossConfig.DEATH_EXPLOSION) {
				level.explode(this, null, LOOT_SAFE_EXPLOSION, getX(), getY() + 1.0, getZ(), BossConfig.DEATH_EXPLOSION_POWER, false,
						Level.ExplosionInteraction.NONE);
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 2, 1, 1, 1, 0);
			level.broadcastEntityEvent(this, (byte) 60);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		this.bossBar.removeAll();
	}

	// ================================================================================================ sounds
	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SHARD_DRAGON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SHARD_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.SHARD_DRAGON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	// ================================================================================================ decorated boss bar
	@Override
	public BarStyle barStyle() {
		return BarStyle.MAIN;
	}

	@Override
	public int barPhase() {
		return getPhase().number();
	}

	@Override
	public int barMaxPhase() {
		return BossPhase.MAX;
	}

	@Override
	public boolean barEnraged() {
		return isEnraged();
	}

	@Override
	public boolean barExposed() {
		return isExposed();
	}

	@Override
	public boolean barRequiresExposure() {
		return false;
	}

	/** Projectiles light up on the bar while the dragon flies - they always hurt it then. */
	@Override
	public int barAllowedDamage() {
		return getPhase().allowedDamageMask() | (isFlying() ? DamageKind.PROJECTILE.bit() : 0);
	}

	@Override
	public int barAccentColor() {
		return isEnraged() ? 0xFFFF2020 : getPhase().accentColor();
	}

	@Override
	public String barPortrait() {
		return "textures/gui/portrait_shard_dragon.png";
	}

	// ================================================================================================ naming / save
	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bossBar.updateName();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("IntroFinished", this.introFinished);
		output.putInt("Phase", getPhase().number());
		output.putBoolean("Enraged", isEnraged());
		output.putInt("EntryWavePhase", this.lastEntryWavePhase);
		output.putBoolean("Flying", isFlying());
		output.putInt("StanceTicks", this.stanceTicks);
		this.actions.save(output.child("Cooldowns"));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.introFinished = input.getBooleanOr("IntroFinished", false);
		BossPhase phase = BossPhase.byNumber(input.getIntOr("Phase", 1));
		setPhase(phase);
		PhaseHealth.apply(this, phasePool(phase), true);
		this.entityData.set(DATA_ENRAGED, input.getBooleanOr("Enraged", false));
		this.lastEntryWavePhase = input.getIntOr("EntryWavePhase", phase.number());
		setFlying(phase.canFly() && input.getBooleanOr("Flying", false));
		this.stanceTicks = input.getIntOr("StanceTicks", 0);
		this.actions.load(input.childOrEmpty("Cooldowns"));
		// an in-flight attack or transition is never restored: the dragon resumes from a neutral state
		setBossState(this.introFinished ? BossState.IDLE : BossState.SPAWNING);
		setBodyScale(phase.scale());
		applyCombatModifiers();
		this.bossBar.setStyle(phase.barColor(), phase.darkensSky());
		this.bossBar.updateName();
	}

	// ================================================================================================ GeckoLib
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(ShardDragonAnimations.BASE_CONTROLLER, 5, this::baseAnimation));
		AnimationController<ShardDragonEntity> action = new AnimationController<>(ShardDragonAnimations.ACTION_CONTROLLER, 3, test -> PlayState.STOP);
		for (String clip : ShardDragonAnimations.ACTION_CLIPS) {
			action.triggerableAnim(clip, ShardDragonAnimations.once(clip));
		}
		controllers.add(action);
		controllers.add(new AnimationController<ShardDragonEntity>(ShardDragonAnimations.HURT_CONTROLLER, 0, test -> PlayState.STOP)
				.additiveAnimations()
				.triggerableAnim(ShardDragonAnimations.TRIGGER_HURT, ShardDragonAnimations.HURT));
	}

	/** Client, every frame: reads synced state only; GeckoLib ignores repeated setAnimation calls for the same clip. */
	private PlayState baseAnimation(AnimationTest<ShardDragonEntity> test) {
		BossState state = getBossState();
		if (isDeadOrDying() || state == BossState.DYING) {
			return test.setAndContinue(ShardDragonAnimations.DEATH);
		}
		if (state == BossState.SPAWNING) {
			return test.setAndContinue(ShardDragonAnimations.SPAWN);
		}
		if (isFlying()) {
			return test.setAndContinue(ShardDragonAnimations.FLY);
		}
		if (test.isMoving()) {
			return test.setAndContinue(isAggressive() ? ShardDragonAnimations.RUN : ShardDragonAnimations.WALK);
		}
		return test.setAndContinue(ShardDragonAnimations.IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}
}
