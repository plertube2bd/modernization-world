package com.bossmode.entity.bastion;

import com.bossmode.config.BastionConfig;
import com.bossmode.entity.action.BossAction;
import com.bossmode.entity.bastion.attack.BastionSummonAttack;
import com.bossmode.entity.bastion.attack.BoulderThrowAttack;
import com.bossmode.entity.bastion.attack.CrystalSpikesAttack;
import com.bossmode.entity.bastion.attack.EarthquakeAttack;
import com.bossmode.entity.bastion.attack.FortifyAction;
import com.bossmode.entity.bastion.attack.MagmaBreachAttack;
import com.bossmode.entity.bastion.attack.MaulAttack;
import com.bossmode.entity.bastion.attack.RollingChargeAttack;
import com.bossmode.entity.bastion.attack.SeismicRoarAttack;
import com.bossmode.entity.bastion.attack.TailSweepAttack;
import com.bossmode.entity.boss.combat.CombatUtil;
import com.bossmode.entity.raid.RaidBossEntity;
import com.bossmode.entity.raid.RaidBossSpec;
import com.bossmode.entity.raid.RaidPhase;
import com.bossmode.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Bastion Behemoth - the armoured raid boss, called at the Bastion Altar with Wyrm Shards; drops the Behemoth Core.
 * <p>
 * Eight 1,024 HP phases ({@link BastionPhase}) behind 18 armour. Its stone-plated <b>front</b> halves every blow that
 * lands on it (except while it recovers from an attack) - fight its flanks and back, where the tail sweep waits.
 * <b>Fortify</b> plants it in place: damage x0.2 and melee attackers are spiked back, until it bursts out of the stance.
 * Left alone it regenerates fast. Everything is decided on the logical server.
 */
public class BastionBehemothEntity extends RaidBossEntity<BastionBehemothEntity> {
	private static final EntityDataAccessor<Boolean> DATA_FORTIFIED = SynchedEntityData.defineId(BastionBehemothEntity.class, EntityDataSerializers.BOOLEAN);
	public static final DustParticleOptions EMBER_DUST = new DustParticleOptions(0xFF9A2A, 2.0F);
	public static final DustParticleOptions STONE_DUST = new DustParticleOptions(0x5A4A3A, 2.4F);
	public static final String[] ACTION_CLIPS = {"maul", "tail_sweep", "earthquake", "boulder_throw", "rolling_charge", "fortify", "crystal_spikes",
			"magma_breach", "seismic_roar", "summon"};

	public static final RaidBossSpec SPEC = new RaidBossSpec("bastion_behemoth", BastionConfig.BASE_SCALE, BastionConfig.REGEN_SHARE_PER_SECOND,
			BastionConfig.REGEN_DELAY, BastionConfig.FIRST_PHASE_HEALTH, BastionConfig.LAST_PHASE_HEALTH, BastionConfig.SPAWN_INTRO_TICKS, BastionConfig.SPAWN_ROAR_TICK, BastionConfig.PHASE_TRANSITION_TICKS,
			BastionConfig.TRANSITION_SHOCKWAVE_RADIUS, BastionConfig.DEATH_ANIMATION_TICKS, BastionConfig.DEATH_EXPLOSION_POWER,
			BastionConfig.ENRAGE_HEALTH_THRESHOLD, BastionConfig.ENRAGE_DAMAGE_MULTIPLIER, BastionConfig.ENRAGE_SPEED_MULTIPLIER,
			BastionConfig.ENRAGE_COOLDOWN_REDUCTION, BastionConfig.BOSS_BAR_RANGE, BossEvent.BossBarOverlay.NOTCHED_10, BastionConfig.NORMAL_ATTACK_CHANCE,
			BastionConfig.GLOBAL_COOLDOWN_AFTER_ATTACK, BastionConfig.GLOBAL_COOLDOWN_AFTER_SPECIAL, BastionConfig.MAX_ACTIVE_MINIONS,
			BastionConfig.MAX_TURN_PER_TICK, BastionConfig.XP_REWARD, EMBER_DUST, ModSounds.BASTION_BEHEMOTH_SPAWN, ModSounds.BASTION_BEHEMOTH_ROAR,
			ModSounds.BASTION_BEHEMOTH_AMBIENT, ModSounds.BASTION_BEHEMOTH_HURT, ModSounds.BASTION_BEHEMOTH_DEATH, ACTION_CLIPS);

	private int lastClangTick;

	public BastionBehemothEntity(EntityType<? extends BastionBehemothEntity> type, Level level) {
		super(type, level, SPEC);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BastionConfig.FIRST_PHASE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, BastionConfig.ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, BastionConfig.MOVEMENT_SPEED)
				.add(Attributes.ARMOR, BastionConfig.ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, BastionConfig.ARMOR_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, BastionConfig.KNOCKBACK_RESISTANCE)
				.add(Attributes.ATTACK_KNOCKBACK, BastionConfig.ATTACK_KNOCKBACK)
				.add(Attributes.FOLLOW_RANGE, BastionConfig.FOLLOW_RANGE)
				.add(Attributes.STEP_HEIGHT, BastionConfig.STEP_HEIGHT)
				.add(Attributes.SCALE, BastionConfig.BASE_SCALE);
	}

	@Override
	protected List<BossAction<BastionBehemothEntity>> createActions() {
		return List.of(new MaulAttack(), new TailSweepAttack(), new EarthquakeAttack(), new BoulderThrowAttack(), new RollingChargeAttack(),
				new FortifyAction(), new CrystalSpikesAttack(), new MagmaBreachAttack(), new SeismicRoarAttack(), new BastionSummonAttack());
	}

	@Override
	public RaidPhase phase(int number) {
		return BastionPhase.byNumber(number);
	}

	@Override
	public int maxPhase() {
		return BastionPhase.MAX;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FORTIFIED, false);
	}

	/** In the Fortify stance: damage x0.2, melee attackers are spiked back. */
	public boolean isFortified() {
		return this.entityData.get(DATA_FORTIFIED);
	}

	public void setFortified(boolean fortified) {
		this.entityData.set(DATA_FORTIFIED, fortified);
	}

	@Override
	public double chaseStopGap() {
		return BastionConfig.MAUL_RANGE * sizeFactor() * 0.6;
	}

	/** Snout height in front of the body - where boulders leave the Behemoth. */
	public Vec3 headPosition() {
		return position().add(CombatUtil.horizontalForward(this).scale(getBbWidth() * 0.55)).add(0, getBbHeight() * 0.7, 0);
	}

	/** Whether {@code point} lies in front of the stone plating. */
	public boolean isFrontal(Vec3 point) {
		Vec3 to = point.subtract(position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 1.0E-4) {
			return false;
		}
		return CombatUtil.horizontalForward(this).dot(to.normalize()) >= Math.cos(Math.toRadians(BastionConfig.FRONT_ARMOR_HALF_ANGLE));
	}

	// ================================================================================================ defences
	@Override
	protected float adjustDamage(ServerLevel level, DamageSource source, float amount) {
		Vec3 from = source.getSourcePosition();
		if (isFortified()) {
			Entity attacker = source.getEntity();
			if (attacker instanceof LivingEntity living && source.getDirectEntity() == attacker && !source.is(DamageTypes.THORNS)
					&& living.distanceTo(this) < getBbWidth() + 6.0 && CombatUtil.canHit(this, living)) {
				living.hurtServer(level, damageSources().thorns(this), amount * BastionConfig.FORTIFY_THORNS);
				level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY(0.6), living.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			}
			clang(level, from != null ? from : position().add(0, getBbHeight() * 0.5, 0));
			hint(attacker, Component.translatable("message.boss_mode.bastion.fortified"));
			return amount * BastionConfig.FORTIFY_DAMAGE_MULTIPLIER;
		}
		if (from != null && !isExposed() && isFrontal(from)) {
			clang(level, from);
			hint(source.getEntity(), Component.translatable("message.boss_mode.bastion.plating"));
			return amount * BastionConfig.FRONT_ARMOR_MULTIPLIER;
		}
		return amount;
	}

	private void clang(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0);
		if (this.tickCount - this.lastClangTick > 4) {
			this.lastClangTick = this.tickCount;
			playSound(ModSounds.BASTION_BEHEMOTH_CLANG, 2.0F, 0.6F + getRandom().nextFloat() * 0.2F);
		}
	}

	@Override
	protected void tickMechanics(ServerLevel level) {
		if (isFortified() && this.tickCount % 4 == 0) {
			double r = getBbWidth() * 0.65;
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12 + this.tickCount * 0.05;
				level.sendParticles(EMBER_DUST, getX() + Math.cos(a) * r, getY(0.15 + 0.6 * ((i % 3) / 2.0)), getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		setFortified(false);
		super.die(source);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(ModSounds.BASTION_BEHEMOTH_STEP, 1.6F, 0.7F + getRandom().nextFloat() * 0.1F);
	}
}
