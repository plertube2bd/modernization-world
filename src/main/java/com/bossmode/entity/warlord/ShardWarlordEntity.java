package com.bossmode.entity.warlord;

import com.bossmode.config.BossConfig;
import com.bossmode.entity.PhaseHealth;
import com.bossmode.entity.DamageKind;
import com.bossmode.entity.DecoratedBoss;
import com.bossmode.entity.ShardFaction;
import com.bossmode.entity.action.ActionController;
import com.bossmode.entity.action.ActionHost;
import com.bossmode.entity.boss.BossBarController;
import com.bossmode.entity.boss.BossState;
import com.bossmode.entity.boss.ShardDragonEntity;
import com.bossmode.entity.boss.combat.CombatUtil;
import com.bossmode.entity.minion.ShardHoundEntity;
import com.bossmode.registry.ModSounds;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Shard Warlord (Mob2.obj) - the dragon's mid-boss, summoned when phases 5, 7, 9 and 10 begin (or placed directly).
 * Own decorated boss bar, five actions (staff swing, shard bolts, ground spikes, war cry, blink) on the same
 * server-authoritative action state machine as the dragon.
 */
public class ShardWarlordEntity extends Monster implements ActionHost, ShardFaction, DecoratedBoss {
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(ShardWarlordEntity.class, EntityDataSerializers.BYTE);
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.shard_warlord.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.shard_warlord.walk");
	private static final RawAnimation SPAWN = RawAnimation.begin().thenPlayAndHold("animation.shard_warlord.spawn");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.shard_warlord.death");

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private final BossBarController bossBar;
	private final ActionController<ShardWarlordEntity> actions;
	private @Nullable UUID summoner;
	private int stateTicks;

	public ShardWarlordEntity(EntityType<? extends ShardWarlordEntity> type, Level level) {
		super(type, level);
		this.xpReward = 150;
		this.bossBar = new BossBarController(this, 48.0, 10, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
		this.actions = new ActionController<>(this, WarlordActions.all(), CombatUtil::isValidTarget, 0.6, 40, 10, 20);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BossConfig.WARLORD_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, BossConfig.WARLORD_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, BossConfig.WARLORD_SPEED)
				.add(Attributes.ARMOR, BossConfig.WARLORD_ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, BossConfig.WARLORD_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, BossConfig.WARLORD_SCALE);
	}

	/** The health of a warlord summoned by a boss whose current phase pool is {@code summonerPool}. */
	public static double poolFor(double summonerPool) {
		return Math.round(summonerPool * BossConfig.WARLORD_HEALTH_SHARE);
	}

	/** Sets max health to {@code health}, full. */
	public void setPool(double health) {
		PhaseHealth.apply(this, health, false);
		setHealth(getMaxHealth());
	}

	public void setSummoner(Entity entity) {
		this.summoner = entity.getUUID();
	}

	public boolean isSummonedBy(Entity entity) {
		return entity.getUUID().equals(this.summoner);
	}

	public ActionController<ShardWarlordEntity> getActionController() {
		return this.actions;
	}

	public BossBarController getBossBar() {
		return this.bossBar;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) BossState.SPAWNING.ordinal());
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

	@Override
	public double getCooldownMultiplier() {
		return 1.0;
	}

	@Override
	public boolean isActionAllowed(String id) {
		return !id.equals("warcry") || !(level() instanceof ServerLevel level)
				|| level.getEntitiesOfClass(ShardHoundEntity.class, getBoundingBox().inflate(32), h -> h.isSummonedBy(this)).size() < 4;
	}

	@Override
	public void faceTarget(Entity target, float maxTurn) {
		Vec3 d = target.position().subtract(position());
		if (d.x * d.x + d.z * d.z < 1.0E-6) {
			return;
		}
		float yaw = Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F, maxTurn);
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new ChaseGoal());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ShardDragonEntity.class, ShardHoundEntity.class, ShardWarlordEntity.class, Vex.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (t, l) -> CombatUtil.isValidTarget(this, t)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false, (t, l) -> CombatUtil.isValidTarget(this, t)));
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level && !isDeadOrDying()) {
			this.bossBar.tick(level);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.stateTicks++;
		BossState state = getBossState();
		this.goalSelector.setControlFlag(Goal.Flag.MOVE, state.allowsFreeMovement());
		this.goalSelector.setControlFlag(Goal.Flag.LOOK, state.allowsFreeMovement());
		if (state == BossState.SPAWNING) {
			getNavigation().stop();
			if (this.stateTicks == 1) {
				playSound(ModSounds.SHARD_WARLORD_WARCRY, 2.5F, 0.7F);
			}
			if (this.stateTicks % 3 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.2, getZ(), 12, 0.8, 0.2, 0.8, 0.1);
			}
			if (this.stateTicks >= BossConfig.WARLORD_INTRO_TICKS) {
				setBossState(BossState.IDLE);
			}
		} else if (state != BossState.DYING) {
			this.actions.tick(level);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() != null && ShardFaction.isMember(source.getEntity())) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && isAlive()) {
			triggerAnim("hurt", "hurt");
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && (getBossState() == BossState.SPAWNING || source.is(DamageTypes.IN_WALL))) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			this.actions.cancel(level);
			setBossState(BossState.DYING);
			this.bossBar.removeAll();
			stopTriggeredAnim("action", null);
		}
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (level() instanceof ServerLevel level && !isRemoved()) {
			if (this.deathTime % 4 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.5), getZ(), 10, 0.6, 1.0, 0.6, 0.05);
			}
			if (this.deathTime >= BossConfig.WARLORD_DEATH_TICKS) {
				level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(0.5), getZ(), 3, 0.5, 0.5, 0.5, 0);
				level.broadcastEntityEvent(this, (byte) 60);
				remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		this.bossBar.removeAll();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SHARD_WARLORD_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SHARD_WARLORD_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.SHARD_WARLORD_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bossBar.updateName();
	}

	// ------------------------------------------------------------------------------------------------ decorated boss bar
	@Override
	public BarStyle barStyle() {
		return BarStyle.MID;
	}

	@Override
	public int barPhase() {
		return 0;
	}

	@Override
	public int barMaxPhase() {
		return 0;
	}

	@Override
	public boolean barEnraged() {
		return false;
	}

	@Override
	public boolean barExposed() {
		BossState state = getBossState();
		return state == BossState.ATTACK_RECOVERY || state == BossState.SPECIAL_RECOVERY;
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
		return 0xFFE0406A;
	}

	@Override
	public String barPortrait() {
		return "textures/gui/portrait_shard_warlord.png";
	}

	// ------------------------------------------------------------------------------------------------ save
	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.summoner != null) {
			output.store("Summoner", UUIDUtil.CODEC, this.summoner);
		}
		output.putBoolean("IntroFinished", getBossState() != BossState.SPAWNING);
		this.actions.save(output.child("Cooldowns"));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.summoner = input.read("Summoner", UUIDUtil.CODEC).orElse(null);
		setBossState(input.getBooleanOr("IntroFinished", false) ? BossState.IDLE : BossState.SPAWNING);
		this.actions.load(input.childOrEmpty("Cooldowns"));
		this.bossBar.updateName();
	}

	// ------------------------------------------------------------------------------------------------ GeckoLib
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("base", 5, this::baseAnimation));
		AnimationController<ShardWarlordEntity> action = new AnimationController<>("action", 3, test -> PlayState.STOP);
		for (String clip : new String[] {"attack", "cast", "slam", "warcry", "blink"}) {
			action.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.shard_warlord." + clip));
		}
		controllers.add(action);
		controllers.add(new AnimationController<ShardWarlordEntity>("hurt", 0, test -> PlayState.STOP).additiveAnimations()
				.triggerableAnim("hurt", RawAnimation.begin().thenPlay("animation.shard_warlord.hurt")));
	}

	private PlayState baseAnimation(AnimationTest<ShardWarlordEntity> test) {
		if (isDeadOrDying()) {
			return test.setAndContinue(DEATH);
		}
		if (getBossState() == BossState.SPAWNING) {
			return test.setAndContinue(SPAWN);
		}
		return test.setAndContinue(test.isMoving() ? WALK : IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}

	/** Path-finding chase (the warlord is human-sized, so vanilla navigation works) that stops at swing range. */
	private final class ChaseGoal extends Goal {
		private int repath;

		ChaseGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			return target != null && target.isAlive() && getBossState().allowsFreeMovement();
		}

		@Override
		public void start() {
			setAggressive(true);
		}

		@Override
		public void stop() {
			setAggressive(false);
			getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.repath > 0) {
				return;
			}
			this.repath = 10;
			if (ActionController.gap(ShardWarlordEntity.this, target) <= BossConfig.WARLORD_SWING_RANGE * 0.7) {
				getNavigation().stop();
			} else {
				getNavigation().moveTo(target, 1.1);
			}
		}
	}
}
