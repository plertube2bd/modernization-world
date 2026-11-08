package com.bossmode.entity.sovereign;

import com.bossmode.entity.PhaseHealth;
import com.bossmode.BossMode;
import com.bossmode.config.SovereignConfig;
import com.bossmode.entity.DamageKind;
import com.bossmode.entity.DecoratedBoss;
import com.bossmode.entity.ShardFaction;
import com.bossmode.entity.action.ActionController;
import com.bossmode.entity.action.ActionHost;
import com.bossmode.entity.action.BossAction;
import com.bossmode.entity.boss.BossBarController;
import com.bossmode.entity.boss.BossState;
import com.bossmode.entity.boss.combat.CombatUtil;
import com.bossmode.entity.minion.MinionType;
import com.bossmode.entity.minion.ShardHoundEntity;
import com.bossmode.entity.sovereign.attack.AbyssalCarapaceAction;
import com.bossmode.entity.sovereign.attack.AbyssalSpearsAttack;
import com.bossmode.entity.sovereign.attack.BlackHoleAttack;
import com.bossmode.entity.sovereign.attack.DimensionRendAttack;
import com.bossmode.entity.sovereign.attack.GravityWellAttack;
import com.bossmode.entity.sovereign.attack.NullSweepAttack;
import com.bossmode.entity.sovereign.attack.OblivionAttack;
import com.bossmode.entity.sovereign.attack.SkywardLashAttack;
import com.bossmode.entity.sovereign.attack.SoulChainsAttack;
import com.bossmode.entity.sovereign.attack.TentacleFieldAttack;
import com.bossmode.entity.sovereign.attack.ClawSwipeAttack;
import com.bossmode.entity.sovereign.attack.EclipseAttack;
import com.bossmode.entity.sovereign.attack.GroundPoundAttack;
import com.bossmode.entity.sovereign.attack.RiftEruptionAttack;
import com.bossmode.entity.sovereign.attack.SoulDrainAttack;
import com.bossmode.entity.sovereign.attack.SovereignSummonAttack;
import com.bossmode.entity.sovereign.attack.TentacleLashAttack;
import com.bossmode.entity.sovereign.attack.VoidGraspAttack;
import com.bossmode.entity.sovereign.attack.VoidOrbsAttack;
import com.bossmode.entity.sovereign.attack.VoidShieldAction;
import com.bossmode.entity.sovereign.attack.VoidStepAttack;
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
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Void Sovereign - the final boss, called at the Void Altar with one trophy of each raid boss (Wyrm Heart, Tempest Eye, Behemoth Core).
 * <p>
 * Ten 1,024 HP phases ({@link SovereignPhase}), growing x1.0 -> x1.5, with twenty patterns. Phases 3, 6 and 9 open with the
 * <b>Void Shield</b>: Sovereign Shards rise around the arena and, until every one of them is broken, nothing hurts the
 * Sovereign. Aerial smashes (a falling mace) are answered: it senses a mace nearby and lashes out at anyone diving at it
 * (Skyward Lash), and Gravity Well, the Abyssal Carapace (blows capped, the excess partly reflected) and Soul Chains take
 * the height out of the fight for a while - the mace still works, but not for free. Like the Shard Dragon everything is decided on the logical server - clients only receive synced state
 * (boss state, phase, enrage, shield, scale, health) and GeckoLib animation triggers.
 */
public class VoidSovereignEntity extends Monster implements ActionHost, ShardFaction, DecoratedBoss {
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(VoidSovereignEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(VoidSovereignEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(VoidSovereignEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(VoidSovereignEntity.class, EntityDataSerializers.BOOLEAN);

	/** Forced by the boss itself (a pylon phase begins), never picked at random. */
	public static final Set<String> FORCED_ACTIONS = Set.of("shield");
	private static final Identifier COMBAT_SPEED_MODIFIER = BossMode.id("sovereign_combat_speed");
	public static final DustParticleOptions VOID_DUST = new DustParticleOptions(0xD01A5A, 2.2F);
	private static final DustParticleOptions DARK_DUST = new DustParticleOptions(0x2A0A40, 2.4F);
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
	private final ActionController<VoidSovereignEntity> actions;
	private final Map<UUID, Integer> deflectMessageTicks = new HashMap<>();
	private final List<UUID> pylons = new ArrayList<>();
	private boolean introFinished;
	private int stateTicks;
	private boolean targetUnreachable;
	private int hurtAnimationCooldown;
	private int lastHurtTick;
	private boolean phaseBreakPending;
	private boolean allowDeath;
	private boolean shieldPending;
	private int lastEntryWavePhase = 1;
	private float transitionFromScale = SovereignConfig.BASE_SCALE;
	private float transitionFromHealth;
	private boolean transitionRefills;
	private int lastDeflectSoundTick;
	/** Abyssal Carapace up: blows are capped and partly reflected. */
	private boolean carapace;
	/** Someone nearby holds a mace (refreshed every half second). */
	private boolean maceThreat;
	private int counterCooldown;
	private int lastShardsLeft = -1;

	public VoidSovereignEntity(EntityType<? extends VoidSovereignEntity> type, Level level) {
		super(type, level);
		this.xpReward = SovereignConfig.XP_REWARD;
		this.bossBar = new BossBarController(this, SovereignConfig.BOSS_BAR_RANGE, SovereignConfig.BOSS_BAR_MEMBERSHIP_INTERVAL,
				SovereignPhase.ONE.barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
		this.actions = new ActionController<>(this, List.<BossAction<VoidSovereignEntity>>of(
				new ClawSwipeAttack(), new TentacleLashAttack(), new GroundPoundAttack(), new VoidOrbsAttack(), new RiftEruptionAttack(),
				new VoidGraspAttack(), new BlackHoleAttack(), new VoidStepAttack(), new EclipseAttack(), new SoulDrainAttack(),
				new SovereignSummonAttack(), new VoidShieldAction(), new SkywardLashAttack(), new GravityWellAttack(), new AbyssalCarapaceAction(),
				new SoulChainsAttack(), new NullSweepAttack(), new TentacleFieldAttack(), new DimensionRendAttack(), new AbyssalSpearsAttack(),
				new OblivionAttack()),
				CombatUtil::isValidTarget, SovereignConfig.NORMAL_ATTACK_CHANCE, 60,
				SovereignConfig.GLOBAL_COOLDOWN_AFTER_ATTACK, SovereignConfig.GLOBAL_COOLDOWN_AFTER_SPECIAL);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, SovereignConfig.FIRST_PHASE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, SovereignConfig.ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, SovereignConfig.MOVEMENT_SPEED)
				.add(Attributes.ARMOR, SovereignConfig.ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, SovereignConfig.ARMOR_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, SovereignConfig.KNOCKBACK_RESISTANCE)
				.add(Attributes.ATTACK_KNOCKBACK, SovereignConfig.ATTACK_KNOCKBACK)
				.add(Attributes.FOLLOW_RANGE, SovereignConfig.FOLLOW_RANGE)
				.add(Attributes.STEP_HEIGHT, SovereignConfig.STEP_HEIGHT)
				.add(Attributes.SCALE, SovereignConfig.BASE_SCALE);
	}

	// ================================================================================================ synced state
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) BossState.SPAWNING.ordinal());
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_ENRAGED, false);
		builder.define(DATA_SHIELDED, false);
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

	public SovereignPhase getPhase() {
		return SovereignPhase.byNumber(this.entityData.get(DATA_PHASE));
	}

	private void setPhase(SovereignPhase phase) {
		this.entityData.set(DATA_PHASE, (byte) phase.number());
	}

	public boolean isEnraged() {
		return this.entityData.get(DATA_ENRAGED);
	}

	/** While the Void Shield stands (pylons alive) nothing hurts the Sovereign. */
	public boolean isShielded() {
		return this.entityData.get(DATA_SHIELDED);
	}

	/** Recovering from an attack. */
	public boolean isExposed() {
		BossState state = getBossState();
		return state == BossState.ATTACK_RECOVERY || state == BossState.SPECIAL_RECOVERY;
	}

	public ActionController<VoidSovereignEntity> getActionController() {
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

	/** Size relative to phase 1 (1.0 -> 1.5); attack ranges and radii grow with it. */
	public double sizeFactor() {
		return getScale() / SovereignConfig.BASE_SCALE;
	}

	public double getDamageMultiplier() {
		return getPhase().damageMultiplier() * (isEnraged() ? SovereignConfig.ENRAGE_DAMAGE_MULTIPLIER : 1.0);
	}

	@Override
	public double getCooldownMultiplier() {
		return getPhase().cooldownMultiplier() * (isEnraged() ? 1.0 - SovereignConfig.ENRAGE_COOLDOWN_REDUCTION : 1.0);
	}

	@Override
	public boolean isActionAllowed(String id) {
		return !FORCED_ACTIONS.contains(id) && getPhase().attacks().contains(id);
	}

	private void applyCombatModifiers() {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			double multiplier = getPhase().speedMultiplier() * (isEnraged() ? SovereignConfig.ENRAGE_SPEED_MULTIPLIER : 1.0);
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
			step.setBaseValue(Math.min(10.0, SovereignConfig.STEP_HEIGHT * scale));
		}
	}

	// ================================================================================================ AI
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SovereignChaseGoal(this));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 48.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VoidSovereignEntity.class, ShardHoundEntity.class, ShardWarlordEntity.class, Vex.class,
				VoidPylonEntity.class));
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
					if (this.shieldPending && !this.actions.isActing()) {
						this.shieldPending = false;
						this.actions.forceAction(level, "shield");
					}
					tickShield(level);
					tickCounters(level);
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
			playSound(ModSounds.VOID_SOVEREIGN_SPAWN, 6.0F, 1.0F);
		}
		if (t <= 40 && t % 2 == 0) {
			double r = getBbWidth() * 0.7;
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.3, getZ(), 30, r, 0.2, r, 0.15);
			level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.3, getZ(), 12, r, 0.1, r, 0.05);
			level.sendParticles(VOID_DUST, getX(), getY() + 1.0, getZ(), 10, r, 1.0, r, 0);
		}
		if (t == SovereignConfig.SPAWN_ROAR_TICK) {
			playSound(ModSounds.VOID_SOVEREIGN_ROAR, 7.0F, 0.8F);
			shockwaveParticles(level, false);
		}
		if (t >= SovereignConfig.SPAWN_INTRO_TICKS) {
			this.introFinished = true;
			setBossState(BossState.IDLE);
		}
	}

	private boolean checkEnrage(ServerLevel level) {
		if (!getPhase().isLast() || isEnraged() || getHealth() / getMaxHealth() > SovereignConfig.ENRAGE_HEALTH_THRESHOLD) {
			return false;
		}
		startTransition(level, getPhase(), true);
		return true;
	}

	private void regenerate(ServerLevel level) {
		if (this.tickCount - this.lastHurtTick > SovereignConfig.REGEN_DELAY && getHealth() < getMaxHealth()) {
			heal(getMaxHealth() * SovereignConfig.REGEN_SHARE_PER_SECOND / 20.0F);
			if (this.tickCount % 10 == 0) {
				level.sendParticles(VOID_DUST, getX(), getY(0.6), getZ(), 6, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
			}
		}
	}

	/** Advances to {@code phase} (or roars into enrage): invulnerable roar, growth, health refill, entry wave, shield. */
	/** The health pool of {@code phase}: 100 in phase 1, rising to 600 in phase 10. */
	public static double phasePool(SovereignPhase phase) {
		return PhaseHealth.pool(phase.number(), SovereignPhase.values().length, SovereignConfig.FIRST_PHASE_HEALTH, SovereignConfig.LAST_PHASE_HEALTH);
	}

	private void startTransition(ServerLevel level, SovereignPhase phase, boolean enrage) {
		this.actions.cancel(level);
		SovereignPhase old = getPhase();
		this.transitionFromScale = getScale();
		this.transitionFromHealth = getHealth();
		this.transitionRefills = phase != old;
		if (phase != old) {
			setPhase(phase);
			PhaseHealth.apply(this, phasePool(phase), false);
			this.bossBar.setStyle(phase.barColor(), phase.darkensSky());
			this.shieldPending = phase.pylons() > 0;
			announce(level, Component.translatable(phase.pylons() > 0 ? "message.boss_mode.sovereign.phase_shield" : "message.boss_mode.sovereign.phase",
					phase.number()));
		}
		if (enrage) {
			this.entityData.set(DATA_ENRAGED, true);
			announce(level, Component.translatable("message.boss_mode.sovereign.enraged"));
		}
		applyCombatModifiers();
		setBossState(BossState.PHASE_TRANSITION);
		getNavigation().stop();
		triggerAnim(SovereignAnimations.ACTION_CONTROLLER, SovereignAnimations.TRIGGER_ROAR);
		playSound(ModSounds.VOID_SOVEREIGN_ROAR, 7.0F, enrage ? 0.6F : 0.8F);
		for (LivingEntity victim : CombatUtil.targetsInRadius(level, this, position(), SovereignConfig.TRANSITION_SHOCKWAVE_RADIUS * sizeFactor())) {
			CombatUtil.knockbackFrom(victim, position(), 1.8, 0.5);
		}
		shockwaveParticles(level, enrage);
	}

	private void tickTransition(ServerLevel level) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		float progress = Math.min(1.0F, this.stateTicks / (float) SovereignConfig.PHASE_TRANSITION_TICKS);
		setBodyScale(Mth.lerp(progress, this.transitionFromScale, getPhase().scale()));
		if (this.transitionRefills) {
			setHealth(Mth.lerp(progress, this.transitionFromHealth, getMaxHealth()));
		}
		if (this.stateTicks % 5 == 0) {
			level.sendParticles(isEnraged() ? DARK_DUST : VOID_DUST, getX(), getY(0.5), getZ(), 18, getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0);
			level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY(0.5), getZ(), 8, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.02);
		}
		if (this.stateTicks == SovereignConfig.PHASE_TRANSITION_TICKS / 2 && this.lastEntryWavePhase < getPhase().number()) {
			this.lastEntryWavePhase = getPhase().number();
			spawnEntryWave(level);
		}
		if (this.stateTicks >= SovereignConfig.PHASE_TRANSITION_TICKS) {
			setBodyScale(getPhase().scale());
			if (this.transitionRefills) {
				setHealth(getMaxHealth());
			}
			setBossState(getTarget() != null ? BossState.CHASE : BossState.IDLE);
		}
	}

	/** The new phase's minions and Shard Warlords, spawned mid-roar. */
	private void spawnEntryWave(ServerLevel level) {
		SovereignPhase phase = getPhase();
		LivingEntity target = getTarget();
		if (!phase.minions().isEmpty()) {
			int count = Math.min(phase.entryWave(), SovereignConfig.MAX_ACTIVE_MINIONS - countMinions(level));
			for (Vec3 p : ringAround(level, count, getBbWidth() * 0.5 + 5.0)) {
				MinionType type = phase.minions().get(getRandom().nextInt(phase.minions().size()));
				type.spawn(level, this, type == MinionType.WRAITH ? p.add(0, 1.5, 0) : p, target);
			}
		}
		int warlords = Math.min(phase.midBosses(), 2 - countWarlords(level));
		for (Vec3 p : ringAround(level, warlords, getBbWidth() * 0.5 + 9.0)) {
			ShardWarlordEntity warlord = ModEntities.SHARD_WARLORD.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (warlord != null) {
				warlord.snapTo(p.x, p.y, p.z, getYRot(), 0.0F);
				warlord.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), EntitySpawnReason.MOB_SUMMONED, null);
				warlord.setSummoner(this);
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

	/** Ground-snapped points evenly spread around the Sovereign. */
	public List<Vec3> ringAround(ServerLevel level, int count, double radius) {
		double startAngle = getRandom().nextDouble() * Math.PI * 2;
		List<Vec3> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double a = startAngle + Math.PI * 2 * i / Math.max(1, count);
			double x = getX() + Math.cos(a) * radius;
			double z = getZ() + Math.sin(a) * radius;
			points.add(new Vec3(x, CombatUtil.groundY(level, x, z), z));
		}
		return points;
	}

	public void announce(ServerLevel level, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceTo(this) <= SovereignConfig.BOSS_BAR_RANGE) {
				player.sendOverlayMessage(message);
			}
		}
	}

	private void shockwaveParticles(ServerLevel level, boolean enrage) {
		double r = getBbWidth() * 0.6;
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2 * i / 48;
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, Math.cos(a), 0.02, Math.sin(a), 0.7);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.5), getZ(), 80, r, getBbHeight() * 0.4, r, 0.25);
		level.sendParticles(VOID_DUST, getX(), getY(0.5), getZ(), 40, r, getBbHeight() * 0.4, r, 0);
		if (enrage) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(0.4), getZ(), 80, r, getBbHeight() * 0.3, r, 0.1);
		}
	}

	/** Crushes breakable blocks in front of the Sovereign (only when the mobGriefing rule allows it). */
	public void crushObstacles(ServerLevel level, Vec3 direction) {
		if (!SovereignConfig.CRUSHES_BLOCKS || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		AABB box = getBoundingBox().expandTowards(direction.scale(1.5));
		int minY = Mth.floor(getY() + 1.0);
		int crushed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), minY, Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && WitherBoss.canDestroy(state) && state.getDestroySpeed(level, pos) >= 0 && state.getDestroySpeed(level, pos) < 30.0F) {
				level.destroyBlock(pos, false, this);
				if (++crushed >= SovereignConfig.CRUSH_MAX_BLOCKS_PER_TICK) {
					break;
				}
			}
		}
	}

	// ================================================================================================ Void Shield
	/** Raises the shield and the phase's pylons around the Sovereign (called by the shield action). */
	public void raiseShield(ServerLevel level) {
		this.pylons.clear();
		int count = getPhase().pylons();
		double start = getRandom().nextDouble() * Math.PI * 2;
		for (int i = 0; i < count; i++) {
			double a = start + Math.PI * 2 * i / count;
			double x = getX() + Math.cos(a) * SovereignConfig.PYLON_RADIUS;
			double z = getZ() + Math.sin(a) * SovereignConfig.PYLON_RADIUS;
			double y = CombatUtil.groundY(level, x, z) + SovereignConfig.PYLON_HOVER;
			VoidPylonEntity pylon = ModEntities.VOID_PYLON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (pylon == null) {
				continue;
			}
			pylon.snapTo(x, y, z, 0.0F, 0.0F);
			pylon.setSovereign(this);
			level.addFreshEntity(pylon);
			this.pylons.add(pylon.getUUID());
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 0.6, 1.2, 0.6, 0.1);
		}
		this.entityData.set(DATA_SHIELDED, !this.pylons.isEmpty());
		playSound(ModSounds.VOID_SOVEREIGN_SHIELD, 6.0F, 0.8F);
		announce(level, Component.translatable("message.boss_mode.sovereign.shield_up", this.pylons.size()));
	}

	public int livingPylons(ServerLevel level) {
		int alive = 0;
		for (UUID id : this.pylons) {
			if (level.getEntity(id) instanceof VoidPylonEntity pylon && pylon.isAlive()) {
				alive++;
			}
		}
		return alive;
	}

	private void tickShield(ServerLevel level) {
		if (!isShielded()) {
			return;
		}
		if (this.tickCount % 6 == 0) {
			double r = getBbWidth() * 0.75;
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16 + this.tickCount * 0.05;
				level.sendParticles(ParticleTypes.PORTAL, getX() + Math.cos(a) * r, getY(0.2 + 0.6 * ((i % 4) / 3.0)), getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		if (this.tickCount % 10 == 0 && livingPylons(level) == 0) {
			dropShield(level);
		}
	}

	private void dropShield(ServerLevel level) {
		this.entityData.set(DATA_SHIELDED, false);
		this.pylons.clear();
		playSound(SoundEvents.GLASS_BREAK, 6.0F, 0.5F);
		playSound(ModSounds.VOID_SOVEREIGN_HURT, 6.0F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(0.5), getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(VOID_DUST, getX(), getY(0.5), getZ(), 80, getBbWidth() * 0.7, getBbHeight() * 0.4, getBbWidth() * 0.7, 0);
		announce(level, Component.translatable("message.boss_mode.sovereign.shield_down"));
	}

	private void discardPylons(ServerLevel level) {
		for (UUID id : this.pylons) {
			Entity pylon = level.getEntity(id);
			if (pylon != null) {
				pylon.discard();
			}
		}
		this.pylons.clear();
		this.entityData.set(DATA_SHIELDED, false);
	}

	/** One Sovereign Shard broke: announce how many are left (the last one drops the shield at once). */
	public void onShardBroken(ServerLevel level) {
		this.lastShardsLeft = livingPylons(level); // the broken shard is already dead, so it is not counted
		if (this.lastShardsLeft > 0) {
			announce(level, Component.translatable("message.boss_mode.sovereign.pylon_down", this.lastShardsLeft));
		} else if (isShielded()) {
			dropShield(level);
		}
	}

	/** Shards left after the last one broke (-1 before any broke) - what the announcement said. */
	public int lastShardsLeft() {
		return this.lastShardsLeft;
	}

	// ================================================================================================ counters to aerial smashes
	public boolean isMaceThreat() {
		return this.maceThreat;
	}

	public boolean isCarapaced() {
		return this.carapace;
	}

	public void setCarapace(boolean carapace) {
		this.carapace = carapace;
	}

	/**
	 * Players diving at the Sovereign: airborne above its feet within reach, and either falling (building a smash) or
	 * shooting upward (a wind-charge launch).
	 */
	public List<Player> aerialThreats(ServerLevel level) {
		double reach = getBbWidth() * 0.5 + SovereignConfig.AERIAL_THREAT_REACH * sizeFactor();
		return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(reach, SovereignConfig.LASH_UP_HEIGHT + 4.0, reach), p -> {
			double dx = p.getX() - getX(), dz = p.getZ() - getZ();
			return CombatUtil.isValidTarget(this, p) && !p.onGround() && dx * dx + dz * dz <= reach * reach && p.getY() > getY() + 1.0
					&& (p.fallDistance > SovereignConfig.AERIAL_THREAT_FALL || p.getDeltaMovement().y > SovereignConfig.AERIAL_THREAT_RISE);
		});
	}

	/** Senses maces nearby and lashes out at anyone diving at it - unless it is recovering from an attack (the opening). */
	private void tickCounters(ServerLevel level) {
		if (this.counterCooldown > 0) {
			this.counterCooldown--;
		}
		if (this.tickCount % 10 == 0) {
			this.maceThreat = !level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SovereignConfig.MACE_SENSE_RADIUS),
					p -> CombatUtil.isValidTarget(this, p) && (p.getMainHandItem().is(Items.MACE) || p.getOffhandItem().is(Items.MACE))).isEmpty();
		}
		BossState state = getBossState();
		boolean canReact = state == BossState.IDLE || state == BossState.CHASE || state == BossState.ATTACK_PREPARATION
				|| state == BossState.SPECIAL_PREPARATION;
		if (this.counterCooldown > 0 || !canReact || isShielded() || !getPhase().attacks().contains("skyward_lash")
				|| "skyward_lash".equals(this.actions.currentActionId())) {
			return;
		}
		if (!aerialThreats(level).isEmpty()) {
			this.counterCooldown = SovereignConfig.LASH_UP_REACT_COOLDOWN;
			this.actions.forceAction(level, "skyward_lash");
		}
	}

	// ================================================================================================ minions
	public int countMinions(ServerLevel level) {
		AABB area = getBoundingBox().inflate(SovereignConfig.MINION_CLEANUP_RADIUS);
		return level.getEntitiesOfClass(ShardHoundEntity.class, area, h -> h.isAlive() && h.isSummonedBy(this)).size()
				+ level.getEntitiesOfClass(Vex.class, area, v -> v.isAlive() && v.getOwner() == this).size();
	}

	public int countWarlords(ServerLevel level) {
		return level.getEntitiesOfClass(ShardWarlordEntity.class, getBoundingBox().inflate(SovereignConfig.MINION_CLEANUP_RADIUS),
				w -> w.isAlive() && w.isSummonedBy(this)).size();
	}

	private void dismissMinions(ServerLevel level) {
		AABB area = getBoundingBox().inflate(SovereignConfig.MINION_CLEANUP_RADIUS);
		List<Mob> minions = new ArrayList<>();
		minions.addAll(level.getEntitiesOfClass(ShardHoundEntity.class, area, h -> h.isSummonedBy(this)));
		minions.addAll(level.getEntitiesOfClass(ShardWarlordEntity.class, area, w -> w.isSummonedBy(this)));
		minions.addAll(level.getEntitiesOfClass(Vex.class, area, v -> v.getOwner() == this));
		for (Mob minion : minions) {
			level.sendParticles(ParticleTypes.POOF, minion.getX(), minion.getY(0.5), minion.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			minion.discard();
		}
		discardPylons(level);
	}

	// ================================================================================================ geometry helpers
	public double gapTo(Entity target) {
		return ActionController.gap(this, target);
	}

	public Vec3 frontPoint(double distance) {
		return position().add(CombatUtil.horizontalForward(this).scale(getBbWidth() / 2.0 + distance));
	}

	/** Chest height in front of the body - where orbs and beams leave the Sovereign. */
	public Vec3 chestPosition() {
		return position().add(CombatUtil.horizontalForward(this).scale(getBbWidth() * 0.35)).add(0, getBbHeight() * 0.72, 0);
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
			if (isShielded()) {
				deflect(level, source);
				return false;
			}
			if (DamageKind.classify(source) == DamageKind.EXPLOSION) {
				amount *= SovereignConfig.EXPLOSION_DAMAGE_MULTIPLIER;
			}
			if (this.carapace) {
				amount = absorbWithCarapace(level, source, amount);
			}
		}
		this.allowDeath = bypass;
		boolean hurt;
		try {
			hurt = super.hurtServer(level, source, amount);
		} finally {
			this.allowDeath = false;
		}
		if (hurt && isAlive()) {
			this.lastHurtTick = this.tickCount;
			level.sendParticles(VOID_DUST, getX(), getY(0.6), getZ(), 10, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
			if (this.hurtAnimationCooldown <= 0) {
				this.hurtAnimationCooldown = 10;
				triggerAnim(SovereignAnimations.HURT_CONTROLLER, SovereignAnimations.TRIGGER_HURT);
			}
		}
		return hurt;
	}

	/** Abyssal Carapace: caps the blow and throws part of the excess back at a nearby attacker. */
	private float absorbWithCarapace(ServerLevel level, DamageSource source, float amount) {
		Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position().add(0, getBbHeight() * 0.5, 0);
		level.sendParticles(DARK_DUST, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0);
		if (amount <= SovereignConfig.CARAPACE_HIT_CAP) {
			return amount;
		}
		float excess = amount - SovereignConfig.CARAPACE_HIT_CAP;
		Entity attacker = source.getEntity();
		if (attacker instanceof LivingEntity living && living.distanceTo(this) < getBbWidth() + 10.0 && CombatUtil.canHit(this, living)) {
			living.hurtServer(level, damageSources().thorns(this), Math.min(SovereignConfig.CARAPACE_REFLECT_MAX, excess * SovereignConfig.CARAPACE_REFLECT));
			living.resetFallDistance();
			CombatUtil.knockbackFrom(living, position(), 1.4, 0.5);
			level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY(0.6), living.getZ(), 12, 0.3, 0.4, 0.3, 0.3);
		}
		if (this.tickCount - this.lastDeflectSoundTick > 4) {
			this.lastDeflectSoundTick = this.tickCount;
			playSound(ModSounds.VOID_SOVEREIGN_CARAPACE, 3.0F, 1.2F);
		}
		if (attacker instanceof ServerPlayer player) {
			Integer last = this.deflectMessageTicks.get(player.getUUID());
			if (last == null || this.tickCount - last > 40) {
				this.deflectMessageTicks.put(player.getUUID(), this.tickCount);
				player.sendOverlayMessage(Component.translatable("message.boss_mode.sovereign.carapace_reflect"));
			}
		}
		return SovereignConfig.CARAPACE_HIT_CAP;
	}

	/** The shield turned a blow: sparks, a clang and (throttled) a hint for the attacking player. */
	private void deflect(ServerLevel level, DamageSource source) {
		Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position().add(0, getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.3);
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.4);
		if (this.tickCount - this.lastDeflectSoundTick > 4) {
			this.lastDeflectSoundTick = this.tickCount;
			playSound(ModSounds.SHARD_DRAGON_DEFLECT, 2.0F, 0.6F + getRandom().nextFloat() * 0.3F);
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			Integer last = this.deflectMessageTicks.get(player.getUUID());
			if (last == null || this.tickCount - last > 40) {
				this.deflectMessageTicks.put(player.getUUID(), this.tickCount);
				player.sendOverlayMessage(Component.translatable("message.boss_mode.sovereign.shielded", livingPylons(level)));
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
		if (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.SLOWNESS) || effect.is(MobEffects.WEAKNESS)
				|| effect.is(MobEffects.LEVITATION)) {
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
			stopTriggeredAnim(SovereignAnimations.ACTION_CONTROLLER, null);
			getNavigation().stop();
			this.noPhysics = false;
			dismissMinions(level);
			announce(level, Component.translatable("message.boss_mode.sovereign.defeated"));
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
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.4), getZ(), 16, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.05);
			level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY(0.4), getZ(), 6, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.02);
			level.sendParticles(VOID_DUST, getX(), getY(0.4), getZ(), 8, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0);
		}
		if (this.deathTime == SovereignConfig.DEATH_ANIMATION_TICKS - 10) {
			playSound(SoundEvents.WITHER_DEATH, 5.0F, 0.6F);
		}
		if (this.deathTime >= SovereignConfig.DEATH_ANIMATION_TICKS) {
			level.explode(this, null, LOOT_SAFE_EXPLOSION, getX(), getY() + 1.0, getZ(), SovereignConfig.DEATH_EXPLOSION_POWER, false, Level.ExplosionInteraction.NONE);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 3, 1.5, 1.5, 1.5, 0);
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 120, 3, 4, 3, 0.3);
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
		return ModSounds.VOID_SOVEREIGN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.VOID_SOVEREIGN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.VOID_SOVEREIGN_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 5.0F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
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
		return SovereignPhase.MAX;
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

	/** Every damage type hurts it - except while the Void Shield stands, when nothing does. */
	@Override
	public int barAllowedDamage() {
		if (isShielded()) {
			return 0;
		}
		int mask = 0;
		for (DamageKind kind : DamageKind.values()) {
			mask |= kind.bit();
		}
		return mask;
	}

	@Override
	public int barAccentColor() {
		return isEnraged() ? 0xFFFF1030 : getPhase().accentColor();
	}

	@Override
	public String barPortrait() {
		return "textures/gui/portrait_void_sovereign.png";
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
		output.putBoolean("Shielded", isShielded());
		output.putBoolean("ShieldPending", this.shieldPending);
		output.store("Pylons", UUIDUtil.CODEC.listOf(), List.copyOf(this.pylons));
		this.actions.save(output.child("Cooldowns"));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.introFinished = input.getBooleanOr("IntroFinished", false);
		SovereignPhase phase = SovereignPhase.byNumber(input.getIntOr("Phase", 1));
		setPhase(phase);
		PhaseHealth.apply(this, phasePool(phase), true);
		this.entityData.set(DATA_ENRAGED, input.getBooleanOr("Enraged", false));
		this.lastEntryWavePhase = input.getIntOr("EntryWavePhase", phase.number());
		this.entityData.set(DATA_SHIELDED, input.getBooleanOr("Shielded", false));
		this.shieldPending = input.getBooleanOr("ShieldPending", false);
		this.pylons.clear();
		this.pylons.addAll(input.read("Pylons", UUIDUtil.CODEC.listOf()).orElse(List.of()));
		this.actions.load(input.childOrEmpty("Cooldowns"));
		// an in-flight attack or transition is never restored: the Sovereign resumes from a neutral state
		setBossState(this.introFinished ? BossState.IDLE : BossState.SPAWNING);
		setBodyScale(phase.scale());
		applyCombatModifiers();
		this.bossBar.setStyle(phase.barColor(), phase.darkensSky());
		this.bossBar.updateName();
	}

	// ================================================================================================ GeckoLib
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(SovereignAnimations.BASE_CONTROLLER, 5, this::baseAnimation));
		AnimationController<VoidSovereignEntity> action = new AnimationController<>(SovereignAnimations.ACTION_CONTROLLER, 3, test -> PlayState.STOP);
		for (String clip : SovereignAnimations.ACTION_CLIPS) {
			action.triggerableAnim(clip, SovereignAnimations.once(clip));
		}
		controllers.add(action);
		controllers.add(new AnimationController<VoidSovereignEntity>(SovereignAnimations.HURT_CONTROLLER, 0, test -> PlayState.STOP)
				.additiveAnimations()
				.triggerableAnim(SovereignAnimations.TRIGGER_HURT, SovereignAnimations.HURT));
	}

	/** Client, every frame: reads synced state only. */
	private PlayState baseAnimation(AnimationTest<VoidSovereignEntity> test) {
		BossState state = getBossState();
		if (isDeadOrDying() || state == BossState.DYING) {
			return test.setAndContinue(SovereignAnimations.DEATH);
		}
		if (state == BossState.SPAWNING) {
			return test.setAndContinue(SovereignAnimations.SPAWN);
		}
		if (test.isMoving()) {
			return test.setAndContinue(SovereignAnimations.WALK);
		}
		return test.setAndContinue(SovereignAnimations.IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}
}
