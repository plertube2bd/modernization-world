package com.bossmode.gear;

import com.bossmode.BossMode;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Gear forged from what the bosses drop: a sword, pickaxe, axe, shovel, hoe, spear and four pieces of
 * armour for each boss, and the ultimate set forged from one piece of each. See {@link BossTier} for the
 * numbers. A full set of armour adds an effect, and each sword adds one to its hits.
 */
public final class BossGear {
    private static final Map<BossTier, Item> MATERIALS = new EnumMap<>(BossTier.class);
    private static final Map<BossTier, Map<BossGearKind, Item>> GEAR = new EnumMap<>(BossTier.class);
    /** Every piece, tier by tier, for the creative tab. */
    public static final List<Item> ALL_GEAR = new ArrayList<>();

    private static final Identifier SPEED_ID = BossMode.id("tempest_set_speed");
    private static final Identifier FALL_ID = BossMode.id("tempest_set_fall");
    private static final Identifier HEALTH_ID = BossMode.id("void_set_health");
    /** A full set's effects are checked this often. */
    private static final int SET_CHECK_TICKS = 10;
    public static final float SHARD_THORNS = 2.0F;
    public static final double TEMPEST_SPEED = 0.10D;
    public static final double TEMPEST_FALL = -0.5D;
    public static final float BEHEMOTH_BLAST_TAKEN = 0.7F;
    public static final double VOID_HEALTH = 4.0D;
    public static final double ULTIMATE_HEALTH = 8.0D;
    public static final float TEMPEST_LIGHTNING_CHANCE = 0.10F;

    static {
        for (BossTier tier : BossTier.values()) {
            if (tier.materialId() != null) {
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BossMode.id(tier.materialId()));
                MATERIALS.put(tier, Registry.register(BuiltInRegistries.ITEM, key,
                        new Item(new Item.Properties().setId(key).rarity(tier.rarity()).fireResistant())));
            }
            Map<BossGearKind, Item> pieces = new EnumMap<>(BossGearKind.class);
            for (BossGearKind kind : BossGearKind.values()) {
                Item item = register(tier, kind);
                pieces.put(kind, item);
                ALL_GEAR.add(item);
            }
            GEAR.put(tier, pieces);
        }
    }

    private BossGear() {}

    private static Item register(BossTier tier, BossGearKind kind) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BossMode.id(tier.id() + "_" + kind.id()));
        Item.Properties properties = new Item.Properties().setId(key).rarity(tier.rarity()).fireResistant();
        properties = switch (kind) {
            // Damage figures are what the tier sets, before the player's own 1.
            case SWORD -> properties.sword(tier.toolMaterial(), tier.swordDamage() - 1.0F - tier.damageBonus(), tier.swordSpeed() - 4.0F);
            case PICKAXE -> properties.pickaxe(tier.toolMaterial(), 1.0F, -2.8F);
            case AXE -> properties.axe(tier.toolMaterial(), tier.axeDamage() - 1.0F - tier.damageBonus(), -3.0F);
            case SHOVEL -> properties.shovel(tier.toolMaterial(), 1.5F, -3.0F);
            case HOE -> properties.hoe(tier.toolMaterial(), -tier.damageBonus(), 0.0F);
            // The netherite spear's handling, with the tier's spear damage.
            case SPEAR -> properties.spear(tier.spearMaterial(), 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F);
            default -> properties.humanoidArmor(tier.armorMaterial(), kind.armorType());
        };
        if (tier.unbreakable()) properties = properties.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        Item.Properties finished = properties;
        return Registry.register(BuiltInRegistries.ITEM, key, new BossGearItem(finished, tier, kind));
    }

    /** What the boss of this tier drops to forge its gear from. */
    public static Item material(BossTier tier) {
        return MATERIALS.get(tier);
    }

    public static Item gear(BossTier tier, BossGearKind kind) {
        return GEAR.get(tier).get(kind);
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output ->
                MATERIALS.values().forEach(output::accept));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output ->
                ALL_GEAR.forEach(output::accept));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % SET_CHECK_TICKS != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) applySetEffects(player);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register(BossGear::afterDamage);
    }

    // ---- Full sets --------------------------------------------------------------------------------

    /** The tier of the full set of armour worn, or null without all four pieces of one tier. */
    public static @Nullable BossTier fullSet(LivingEntity entity) {
        BossTier tier = null;
        for (BossGearKind kind : new BossGearKind[] {BossGearKind.HELMET, BossGearKind.CHESTPLATE, BossGearKind.LEGGINGS, BossGearKind.BOOTS}) {
            if (!(entity.getItemBySlot(kind.slot()).getItem() instanceof BossGearItem piece) || piece.kind() != kind) return null;
            if (tier == null) {
                tier = piece.tier();
            } else if (tier != piece.tier()) {
                return null;
            }
        }
        return tier;
    }

    /**
     * Shard: fire resistance. Tempest: 10% faster and half fall damage. Void: two more hearts and no wither.
     * Ultimate: all of them, with four more hearts. (Behemoth's blast armour and shard's thorns work as
     * damage is taken.) Taking a set off takes its effects away within half a second.
     */
    public static void applySetEffects(LivingEntity entity) {
        BossTier set = fullSet(entity);
        boolean shard = set != null && set.has(BossTier.SHARD);
        boolean tempest = set != null && set.has(BossTier.TEMPEST);
        boolean voidSet = set != null && set.has(BossTier.VOID);
        modifier(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, tempest ? TEMPEST_SPEED : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(entity, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, tempest ? TEMPEST_FALL : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        double health = set == BossTier.ULTIMATE ? ULTIMATE_HEALTH : voidSet ? VOID_HEALTH : 0.0D;
        modifier(entity, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADD_VALUE);
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        if (shard) entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 220, 0, true, false, true));
        if (voidSet) entity.removeEffect(MobEffects.WITHER);
    }

    private static void modifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount,
                                 AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        if (amount == 0.0D) {
            instance.removeModifier(id);
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /** Behemoth (and ultimate) armour takes 30% less from explosions. Called as damage arrives. */
    public static float adjustIncomingDamage(LivingEntity target, DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.IS_EXPLOSION)) return amount;
        BossTier set = fullSet(target);
        return set != null && set.has(BossTier.BEHEMOTH) ? amount * BEHEMOTH_BLAST_TAKEN : amount;
    }

    // ---- Hits -------------------------------------------------------------------------------------

    private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
        if (blocked || damageTaken <= 0.0F || !(target.level() instanceof ServerLevel level)) return;
        // Shard armour: whoever strikes it in melee takes some back.
        BossTier set = fullSet(target);
        if (set != null && set.has(BossTier.SHARD) && source.getDirectEntity() instanceof LivingEntity attacker
                && attacker != target && !source.is(DamageTypes.THORNS) && !source.is(DamageTypeTags.IS_PROJECTILE)) {
            attacker.hurtServer(level, level.damageSources().thorns(target), SHARD_THORNS);
        }
        // A boss sword's own effect, on a melee hit.
        if (source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))
                && attacker.getMainHandItem().getItem() instanceof BossGearItem sword && sword.kind() == BossGearKind.SWORD) {
            swordHit(level, attacker, target, sword.tier());
        }
    }

    /** Shard: sets it alight. Tempest: one hit in ten calls down lightning. Behemoth: throws it back. Void: withers it. */
    public static void swordHit(ServerLevel level, LivingEntity attacker, LivingEntity target, BossTier tier) {
        swordHit(level, attacker, target, tier, rollsLightning(attacker.getRandom()));
    }

    /** Whether a tempest blade's hit calls down lightning: one in ten. */
    public static boolean rollsLightning(net.minecraft.util.RandomSource random) {
        return random.nextFloat() < TEMPEST_LIGHTNING_CHANCE;
    }

    /** As above, with the lightning roll already made. */
    public static void swordHit(ServerLevel level, LivingEntity attacker, LivingEntity target, BossTier tier, boolean lightning) {
        if (tier.has(BossTier.SHARD)) target.igniteForSeconds(3.0F);
        if (tier.has(BossTier.TEMPEST) && lightning) {
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(target.getX(), target.getY(), target.getZ());
                if (attacker instanceof ServerPlayer player) bolt.setCause(player);
                level.addFreshEntity(bolt);
            }
        }
        if (tier.has(BossTier.BEHEMOTH)) {
            Vec3 away = target.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() > 1.0E-4D) {
                away = away.normalize();
                target.push(away.x * 1.2D, 0.35D, away.z * 1.2D);
                target.syncVelocity = true;
            }
        }
        if (tier.has(BossTier.VOID)) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), attacker);
    }
}
