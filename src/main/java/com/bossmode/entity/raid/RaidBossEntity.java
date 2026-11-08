package com.bossmode.entity.raid;

import com.bossmode.entity.PhaseHealth;
import com.bossmode.BossMode;
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
import com.bossmode.entity.warlord.ShardWarlordEntity;
import com.bossmode.registry.ModEntities;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.player.Player;
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
import java.util.UUID;

/**
 * Shared machinery of the multi-phase raid bosses added after the Shard Dragon (Tempest Wyvern, Bastion Behemoth):
 * the server-authoritative action state machine, phase pools (a lethal hit before the last phase breaks into the next
 * one), growth and refill during the phase roar, entry waves and Shard Warlords, one-time enrage, regeneration, the
 * decorated boss bar, minion bookkeeping, block crushing, death and save/load. Subclasses supply the phase table,
 * their actions and their own mechanics through the protected hooks.
 */
public abstract class RaidBossEntity<S extends RaidBossEntity<S>> extends Monster implements ActionHost, ShardFaction, DecoratedBoss {
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(RaidBossEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(RaidBossEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(RaidBossEntity.class, EntityDataSerializers.BOOLEAN);
	private static final Identifier COMBAT_SPEED_MODIFIER = BossMode.id("raid_combat_speed");
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

	protected final RaidBossSpec spec;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	protected final BossBarController bossBar;
	protected final ActionController<S> actions;
	private final Map<UUID, Integer> hintTicks = new HashMap<>();
	private final List<UUID> minions = new ArrayList<>();
	private boolean introFinished;
	protected int stateTicks;
	private boolean targetUnreachable;
	private int hurtAnimationCooldown;
	protected int lastHurtTick;
	private boolean phaseBreakPending;
	private boolean allowDeath;
	private int lastEntryWavePhase = 1;
	private float transitionFromScale;
	private float transitionFromHealth;
	private boolean transitionRefills;
	private final RawAnimation idle;
	private final RawAnimation walk;
	private final RawAnimation spawnClip;
	private final RawAnimation deathClip;
	private final RawAnimation hurtClip;

	@SuppressWarnings("unchecked")
	protected RaidBossEntity(EntityType<? extends S> type, Level level, RaidBossSpec spec) {
		super(type, level);
		this.spec = spec;
		this.xpReward = spec.xp();
		this.transitionFromScale = spec.baseScale();
		this.bossBar = new BossBarController(this, spec.bossBarRange(), 10, phase(1).barColor(), spec.overlay());
		this.actions = new ActionController<>((S) this, createActions(), CombatUtil::isValidTarget, spec.normalChance(), 60,
				spec.globalAfterNormal(), spec.globalAfterSpecial());
		this.idle = RawAnimation.begin().thenLoop(spec.clip("idle"));
		this.walk = RawAnimation.begin().thenLoop(spec.clip("walk"));
		this.spawnClip = RawAnimation.begin().thenPlayAndHold(spec.clip("spawn"));
		this.deathClip = RawAnimation.begin().thenPlayAndHold(spec.clip("death"));
		this.hurtClip = RawAnimation.begin().thenPlay(spec.clip("hurt"));
		this.setPersistenceRequired();
	}

	// ================================================================================================ subclass contract
	protected abstract List<BossAction<S>> createActions();

	/** Phase row by 1-based number (clamped by the implementation). */
	public abstract RaidPhase phase(int number);

	public abstract int maxPhase();

	/** The health pool of phase {@code number}: the spec's first pool, rising evenly to its last. */
	public double phasePool(int number) {
		return PhaseHealth.pool(number, maxPhase(), this.spec.firstPhaseHealth(), this.spec.lastPhaseHealth());
	}

	/** Called every AI tick in the fighting states, before the action state machine. */
	protected void tickMechanics(ServerLevel level) {
	}

	/** Regeneration is on unless a mechanic says otherwise (e.g. while flying). */
	protected boolean canRegenerate() {
		return true;
	}

	/**
	 * Adjusts an incoming hit after the faction check. Return the damage to apply, or a negative value to turn it away
	 * entirely. Not called for invulnerability-bypassing damage or during the intro / a phase roar.
	 */
	protected float adjustDamage(ServerLevel level, DamageSource source, float amount) {
		return amount;
	}

	/** After a hit landed ({@code dealt} = health actually lost). */
	protected void afterHurt(ServerLevel level, DamageSource source, float dealt) {
	}

	/** A new phase just started its roar. */
	protected void onPhaseStart(ServerLevel level, RaidPhase phase) {
	}

	/** Loops of the base controller in the fighting states; defaults to walk / idle. */
	protected RawAnimation stanceAnimation(AnimationTest<S> test) {
		return test.isMoving() ? this.walk : this.idle;
	}

	/** Whether the chase goal may walk the boss toward its target right now. */
	public boolean canChase() {
		return true;
	}

	/** The chase goal stops walking once the hitbox gap to the target is this small. */
	public double chaseStopGap() {
		return 2.0 * sizeFactor();
	}

	protected void saveMechanics(ValueOutput output) {
	}

	protected void loadMechanics(ValueInput input) {
	}

	// ================================================================================================ synced state
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) BossState.SPAWNING.ordinal());
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_ENRAGED, false);
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

	public RaidPhase getPhase() {
		return phase(this.entityData.get(DATA_PHASE));
	}

	private void setPhaseNumber(int number) {
		this.entityData.set(DATA_PHASE, (byte) number);
	}

	public boolean isLastPhase() {
		return getPhase().number() >= maxPhase();
	}

	public boolean isEnraged() {
		return this.entityData.get(DATA_ENRAGED);
	}

	public boolean isExposed() {
		BossState state = getBossState();
		return state == BossState.ATTACK_RECOVERY || state == BossState.SPECIAL_RECOVERY;
	}

	public ActionController<S> getActionController() {
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

	public RaidBossSpec spec() {
		return this.spec;
	}

	/** Size relative to phase 1; attack ranges and radii grow with it. */
	public double sizeFactor() {
		return getScale() / this.spec.baseScale();
	}

	public double getDamageMultiplier() {
		return getPhase().damageMultiplier() * (isEnraged() ? this.spec.enrageDamage() : 1.0);
	}

	@Override
	public double getCooldownMultiplier() {
		return getPhase().cooldownMultiplier() * (isEnraged() ? 1.0 - this.spec.enrageCooldownReduction() : 1.0);
	}

	@Override
	public boolean isActionAllowed(String id) {
		return getPhase().attacks().contains(id);
	}

	protected void applyCombatModifiers() {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			double multiplier = getPhase().speedMultiplier() * (isEnraged() ? this.spec.enrageSpeed() : 1.0);
			speed.addOrUpdateTransientModifier(new AttributeModifier(COMBAT_SPEED_MODIFIER, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	protected void setBodyScale(float scale) {
		AttributeInstance scaleAttribute = getAttribute(Attributes.SCALE);
		if (scaleAttribute != null && Math.abs(scaleAttribute.getBaseValue() - scale) > 1.0E-3) {
			scaleAttribute.setBaseValue(scale);
		}
	}

	// ================================================================================================ AI
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new RaidChaseGoal(this));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 48.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(target, level) -> CombatUtil.isValidTarget(this, target)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false,
				(target, level) -> CombatUtil.isValidTarget(this, target)));
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target != null && ShardFaction.isMember(target)) {
			return; // never turns on its own side
		}
		super.setTarget(target);
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
					startTransition(level, Math.min(maxPhase(), getPhase().number() + 1), false);
				} else if (!checkEnrage(level)) {
					tickMechanics(level);
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
			playSound(this.spec.spawnSound(), 6.0F, 1.0F);
		}
		if (t <= this.spec.introTicks() - 10 && t % 2 == 0) {
			double r = getBbWidth() * 0.6;
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 8, r, 0.2, r, 0.05);
			level.sendParticles(this.spec.dust(), getX(), getY(0.5), getZ(), 10, r, getBbHeight() * 0.3, r, 0);
		}
		if (t == this.spec.roarTick()) {
			playSound(this.spec.roarSound(), 7.0F, 0.9F);
			shockwaveParticles(level);
		}
		if (t >= this.spec.introTicks()) {
			this.introFinished = true;
			setBossState(BossState.IDLE);
		}
	}

	private boolean checkEnrage(ServerLevel level) {
		if (!isLastPhase() || isEnraged() || getHealth() / getMaxHealth() > this.spec.enrageThreshold()) {
			return false;
		}
		startTransition(level, getPhase().number(), true);
		return true;
	}

	private void regenerate(ServerLevel level) {
		if (canRegenerate() && this.tickCount - this.lastHurtTick > this.spec.regenDelay() && getHealth() < getMaxHealth()) {
			heal(getMaxHealth() * this.spec.regenSharePerSecond() / 20.0F);
			if (this.tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.GLOW, getX(), getY(0.6), getZ(), 6, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
			}
		}
	}

	private void startTransition(ServerLevel level, int phaseNumber, boolean enrage) {
		this.actions.cancel(level);
		RaidPhase old = getPhase();
		RaidPhase phase = phase(phaseNumber);
		this.transitionFromScale = getScale();
		this.transitionFromHealth = getHealth();
		this.transitionRefills = phase.number() != old.number();
		if (this.transitionRefills) {
			setPhaseNumber(phase.number());
			PhaseHealth.apply(this, phasePool(phase.number()), false);
			this.bossBar.setStyle(phase.barColor(), phase.darkensSky());
			announce(level, Component.translatable("message.boss_mode.raid.phase", getDisplayName(), phase.number()));
			onPhaseStart(level, phase);
		}
		if (enrage) {
			this.entityData.set(DATA_ENRAGED, true);
			announce(level, Component.translatable("message.boss_mode.raid.enraged", getDisplayName()));
		}
		applyCombatModifiers();
		setBossState(BossState.PHASE_TRANSITION);
		getNavigation().stop();
		triggerAnim(actionControllerName(), "roar");
		playSound(this.spec.roarSound(), 7.0F, enrage ? 0.7F : 0.9F);
		for (LivingEntity victim : CombatUtil.targetsInRadius(level, this, position(), this.spec.transitionShockwave() * sizeFactor())) {
			CombatUtil.knockbackFrom(victim, position(), 1.6, 0.5);
		}
		shockwaveParticles(level);
	}

	private void tickTransition(ServerLevel level) {
		setDeltaMovement(0, isNoGravity() ? 0 : getDeltaMovement().y, 0);
		float progress = Math.min(1.0F, this.stateTicks / (float) this.spec.transitionTicks());
		setBodyScale(Mth.lerp(progress, this.transitionFromScale, getPhase().scale()));
		if (this.transitionRefills) {
			setHealth(Mth.lerp(progress, this.transitionFromHealth, getMaxHealth()));
		}
		if (this.stateTicks % 5 == 0) {
			level.sendParticles(this.spec.dust(), getX(), getY(0.5), getZ(), 16, getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0);
		}
		if (this.stateTicks == this.spec.transitionTicks() / 2 && this.lastEntryWavePhase < getPhase().number()) {
			this.lastEntryWavePhase = getPhase().number();
			spawnEntryWave(level);
		}
		if (this.stateTicks >= this.spec.transitionTicks()) {
			setBodyScale(getPhase().scale());
			if (this.transitionRefills) {
				setHealth(getMaxHealth());
			}
			setBossState(getTarget() != null ? BossState.CHASE : BossState.IDLE);
		}
	}

	private void spawnEntryWave(ServerLevel level) {
		RaidPhase phase = getPhase();
		LivingEntity target = getTarget();
		if (!phase.minions().isEmpty()) {
			int count = Math.min(phase.entryWave(), this.spec.maxMinions() - countMinions(level));
			summonMinions(level, count, target);
		}
		int warlords = Math.min(phase.midBosses(), 2 - countWarlords(level));
		for (Vec3 p : ringAround(level, warlords, getBbWidth() * 0.5 + 8.0)) {
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

	// ================================================================================================ minions
	/** Spawns {@code count} minions of the current phase's roster around the boss. */
	public void summonMinions(ServerLevel level, int count, @Nullable LivingEntity target) {
		spawnMinionsAt(level, ringAround(level, count, getBbWidth() * 0.5 + 5.0), target);
	}

	/** Spawns one minion of the current phase's roster at each point (up to the minion cap). */
	public void spawnMinionsAt(ServerLevel level, List<Vec3> points, @Nullable LivingEntity target) {
		RaidPhase phase = getPhase();
		if (phase.minions().isEmpty()) {
			return;
		}
		int room = this.spec.maxMinions() - countMinions(level);
		for (Vec3 p : points) {
			if (room-- <= 0) {
				break;
			}
			MinionType type = phase.minions().get(getRandom().nextInt(phase.minions().size()));
			Mob mob = type.spawn(level, this, type.flies() ? p.add(0, 1.5, 0) : p, target);
			if (mob != null) {
				this.minions.add(mob.getUUID());
			}
		}
	}

	public int countMinions(ServerLevel level) {
		this.minions.removeIf(id -> !(level.getEntity(id) instanceof Mob mob) || !mob.isAlive());
		return this.minions.size();
	}

	public int countWarlords(ServerLevel level) {
		return level.getEntitiesOfClass(ShardWarlordEntity.class, getBoundingBox().inflate(128.0), w -> w.isAlive() && w.isSummonedBy(this)).size();
	}

	protected void dismissMinions(ServerLevel level) {
		List<Entity> doomed = new ArrayList<>();
		for (UUID id : this.minions) {
			Entity e = level.getEntity(id);
			if (e != null) {
				doomed.add(e);
			}
		}
		doomed.addAll(level.getEntitiesOfClass(ShardWarlordEntity.class, getBoundingBox().inflate(128.0), w -> w.isSummonedBy(this)));
		for (Entity e : doomed) {
			level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY(0.5), e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			e.discard();
		}
		this.minions.clear();
	}

	/** Ground-snapped points evenly spread around the boss. */
	public List<Vec3> ringAround(ServerLevel level, int count, double radius) {
		double start = getRandom().nextDouble() * Math.PI * 2;
		List<Vec3> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double a = start + Math.PI * 2 * i / Math.max(1, count);
			double x = getX() + Math.cos(a) * radius, z = getZ() + Math.sin(a) * radius;
			points.add(new Vec3(x, CombatUtil.groundY(level, x, z), z));
		}
		return points;
	}

	// ================================================================================================ helpers
	public void announce(ServerLevel level, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceTo(this) <= this.spec.bossBarRange()) {
				player.sendOverlayMessage(message);
			}
		}
	}

	/** Throttled overlay hint for one player (deflections and the like). */
	protected void hint(@Nullable Entity who, Component message) {
		if (who instanceof ServerPlayer player) {
			Integer last = this.hintTicks.get(player.getUUID());
			if (last == null || this.tickCount - last > 40) {
				this.hintTicks.put(player.getUUID(), this.tickCount);
				player.sendOverlayMessage(message);
			}
			if (this.hintTicks.size() > 32) {
				this.hintTicks.clear();
			}
		}
	}

	protected void shockwaveParticles(ServerLevel level) {
		double r = getBbWidth() * 0.6;
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2 * i / 48;
			level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, Math.cos(a), 0.02, Math.sin(a), 0.7);
		}
		level.sendParticles(this.spec.dust(), getX(), getY(0.5), getZ(), 40, r, getBbHeight() * 0.4, r, 0);
	}

	/** Crushes breakable blocks in front of the boss (only with the mobGriefing rule). */
	public void crushObstacles(ServerLevel level, Vec3 direction) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		AABB box = getBoundingBox().expandTowards(direction.scale(1.5));
		int minY = Mth.floor(getY() + 1.0);
		int crushed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), minY, Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && WitherBoss.canDestroy(state) && state.getDestroySpeed(level, pos) >= 0 && state.getDestroySpeed(level, pos) < 30.0F) {
				level.destroyBlock(pos, false, this);
				if (++crushed >= 24) {
					break;
				}
			}
		}
	}

	public double gapTo(Entity target) {
		return ActionController.gap(this, target);
	}

	public Vec3 frontPoint(double distance) {
		return position().add(CombatUtil.horizontalForward(this).scale(getBbWidth() / 2.0 + distance));
	}

	@Override
	public void faceTarget(Entity target, float maxTurn) {
		faceDirection(target.position().subtract(position()), maxTurn);
	}

	public void faceDirection(Vec3 direction, float maxTurn) {
		if (direction.x * direction.x + direction.z * direction.z < 1.0E-6) {
			return;
		}
		float desired = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
		float yaw = Mth.approachDegrees(getYRot(), desired, maxTurn);
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	// ================================================================================================ damage
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() != null && ShardFaction.isMember(source.getEntity())) {
			return false;
		}
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass && !getBossState().isInvulnerable()) {
			if (DamageKind.classify(source) == DamageKind.EXPLOSION) {
				amount *= 0.5F;
			}
			amount = adjustDamage(level, source, amount);
			if (amount < 0) {
				return false;
			}
		}
		this.allowDeath = bypass;
		float before = getHealth();
		boolean hurt;
		try {
			hurt = super.hurtServer(level, source, amount);
		} finally {
			this.allowDeath = false;
		}
		if (hurt && isAlive()) {
			this.lastHurtTick = this.tickCount;
			level.sendParticles(this.spec.dust(), getX(), getY(0.6), getZ(), 10, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
			if (this.hurtAnimationCooldown <= 0) {
				this.hurtAnimationCooldown = 10;
				triggerAnim("hurt", "hurt");
			}
			if (!this.phaseBreakPending) {
				afterHurt(level, source, Math.max(0.0F, before - getHealth()));
			}
		}
		return hurt;
	}

	/** Each phase is its own pool: a lethal hit before the last phase leaves 1 HP and breaks into the next phase. */
	@Override
	public void setHealth(float health) {
		if (health <= 0.0F && !this.allowDeath && !level().isClientSide() && isAlive() && !isLastPhase()) {
			super.setHealth(1.0F);
			this.phaseBreakPending = true;
			return;
		}
		super.setHealth(health);
	}

	public boolean isPhaseBreakPending() {
		return this.phaseBreakPending;
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
		super.die(source); // loot table + XP once, by vanilla
		if (level() instanceof ServerLevel level) {
			this.actions.cancel(level);
			setBossState(BossState.DYING);
			this.bossBar.removeAll();
			stopTriggeredAnim(actionControllerName(), null);
			getNavigation().stop();
			this.noPhysics = false;
			setNoGravity(false);
			dismissMinions(level);
			announce(level, Component.translatable("message.boss_mode.raid.defeated", getDisplayName()));
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
			level.sendParticles(this.spec.dust(), getX(), getY(0.4), getZ(), 10, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(0.4), getZ(), 4, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
		}
		if (this.deathTime >= this.spec.deathTicks()) {
			level.explode(this, null, LOOT_SAFE_EXPLOSION, getX(), getY() + 1.0, getZ(), this.spec.deathExplosionPower(), false, Level.ExplosionInteraction.NONE);
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
		return this.spec.ambientSound();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.spec.hurtSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.spec.deathSound();
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 220;
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
		return maxPhase();
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

	@Override
	public int barAllowedDamage() {
		int mask = 0;
		for (DamageKind kind : DamageKind.values()) {
			mask |= kind.bit();
		}
		return mask;
	}

	@Override
	public int barAccentColor() {
		return isEnraged() ? 0xFFFF3020 : getPhase().accentColor();
	}

	@Override
	public String barPortrait() {
		return this.spec.portrait();
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
		output.store("Minions", UUIDUtil.CODEC.listOf(), List.copyOf(this.minions));
		this.actions.save(output.child("Cooldowns"));
		saveMechanics(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.introFinished = input.getBooleanOr("IntroFinished", false);
		RaidPhase phase = phase(input.getIntOr("Phase", 1));
		setPhaseNumber(phase.number());
		PhaseHealth.apply(this, phasePool(phase.number()), true);
		this.entityData.set(DATA_ENRAGED, input.getBooleanOr("Enraged", false));
		this.lastEntryWavePhase = input.getIntOr("EntryWavePhase", phase.number());
		this.minions.clear();
		this.minions.addAll(input.read("Minions", UUIDUtil.CODEC.listOf()).orElse(List.of()));
		this.actions.load(input.childOrEmpty("Cooldowns"));
		// an in-flight attack or transition is never restored
		setBossState(this.introFinished ? BossState.IDLE : BossState.SPAWNING);
		setBodyScale(phase.scale());
		applyCombatModifiers();
		this.bossBar.setStyle(phase.barColor(), phase.darkensSky());
		this.bossBar.updateName();
		loadMechanics(input);
	}

	// ================================================================================================ GeckoLib
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<S>("base", 5, this::baseAnimation));
		AnimationController<S> action = new AnimationController<>("action", 3, test -> PlayState.STOP);
		for (String clip : this.spec.actionClips()) {
			action.triggerableAnim(clip, RawAnimation.begin().thenPlay(this.spec.clip(clip)));
		}
		action.triggerableAnim("roar", RawAnimation.begin().thenPlay(this.spec.clip("roar")));
		controllers.add(action);
		controllers.add(new AnimationController<S>("hurt", 0, test -> PlayState.STOP).additiveAnimations().triggerableAnim("hurt", this.hurtClip));
	}

	private PlayState baseAnimation(AnimationTest<S> test) {
		BossState state = getBossState();
		if (isDeadOrDying() || state == BossState.DYING) {
			return test.setAndContinue(this.deathClip);
		}
		if (state == BossState.SPAWNING) {
			return test.setAndContinue(this.spawnClip);
		}
		return test.setAndContinue(stanceAnimation(test));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}
}
