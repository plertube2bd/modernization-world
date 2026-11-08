package com.bossmode.gear;

import com.bossmode.BossMode;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.resources.ResourceKey;

import java.util.Map;

/**
 * The boss gear tiers, in the order the bosses are fought, and the ultimate gear made from all four bosses' materials.
 * Netherite is the yardstick: sword 8 at 1.6, axe 10, spear 5, mining speed 9, 2,031 uses, armour 3/8/6/3 with
 * toughness 3 and 0.1 knockback resistance a piece.
 */
public enum BossTier {
    //        id          material         sword  speed  axe   spear bonus  mine  uses  ench  helm chest legs boots tough  kb    armour uses  rarity
    SHARD("shard", "crystal_scale", 9.0F, 1.6F, 11.0F, 6.0F, 5.0F, 10.0F, 2600, 16, 3, 8, 7, 3, 3.5F, 0.1F, 43, Rarity.RARE),
    TEMPEST("tempest", "tempest_scale", 10.0F, 1.8F, 12.0F, 7.0F, 5.5F, 11.0F, 3000, 18, 3, 9, 7, 3, 4.0F, 0.05F, 48, Rarity.RARE),
    BEHEMOTH("behemoth", "behemoth_plate", 11.0F, 1.4F, 13.0F, 8.0F, 6.0F, 11.0F, 3800, 15, 4, 9, 7, 4, 5.0F, 0.2F, 59, Rarity.RARE),
    VOID("void", "void_ingot", 12.0F, 1.6F, 14.0F, 9.0F, 7.0F, 13.0F, 4500, 20, 4, 10, 8, 4, 6.0F, 0.15F, 70, Rarity.EPIC),
    /** Made from the ultimate alloy (one of each boss's materials): never wears out. */
    ULTIMATE("ultimate", "ultimate_alloy", 14.0F, 1.7F, 16.0F, 11.0F, 8.5F, 15.0F, 9999, 22, 5, 10, 8, 5, 7.0F, 0.25F, 99, Rarity.EPIC);

    /** The four bosses' tiers, whose materials make the ultimate alloy. */
    public static final BossTier[] BOSSES = {SHARD, TEMPEST, BEHEMOTH, VOID};

    private final String id;
    private final String materialId;
    private final float swordDamage;
    private final float swordSpeed;
    private final float axeDamage;
    private final float spearDamage;
    private final float damageBonus;
    private final float miningSpeed;
    private final int durability;
    private final int enchantability;
    private final Map<ArmorType, Integer> defense;
    private final float toughness;
    private final float knockbackResistance;
    private final int armorDurability;
    private final Rarity rarity;
    private final TagKey<Item> repairTag;
    private final ToolMaterial toolMaterial;
    private final ToolMaterial spearMaterial;
    private final ArmorMaterial armorMaterial;

    BossTier(String id, String materialId, float swordDamage, float swordSpeed, float axeDamage, float spearDamage, float damageBonus,
             float miningSpeed, int durability, int enchantability, int helmet, int chestplate, int leggings, int boots,
             float toughness, float knockbackResistance, int armorDurability, Rarity rarity) {
        this.id = id;
        this.materialId = materialId;
        this.swordDamage = swordDamage;
        this.swordSpeed = swordSpeed;
        this.axeDamage = axeDamage;
        this.spearDamage = spearDamage;
        this.damageBonus = damageBonus;
        this.miningSpeed = miningSpeed;
        this.durability = durability;
        this.enchantability = enchantability;
        this.defense = Map.of(ArmorType.HELMET, helmet, ArmorType.CHESTPLATE, chestplate, ArmorType.LEGGINGS, leggings,
                ArmorType.BOOTS, boots, ArmorType.BODY, chestplate);
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.armorDurability = armorDurability;
        this.rarity = rarity;
        this.repairTag = TagKey.create(Registries.ITEM, BossMode.id("repairs_" + id + "_gear"));
        this.toolMaterial = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, durability, miningSpeed, damageBonus,
                enchantability, repairTag);
        // A spear hits for 1 and its material's bonus, so the spear gets a material of its own.
        this.spearMaterial = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, durability, miningSpeed, spearDamage - 1.0F,
                enchantability, repairTag);
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, BossMode.id(id));
        this.armorMaterial = new ArmorMaterial(armorDurability, defense, enchantability, SoundEvents.ARMOR_EQUIP_NETHERITE,
                toughness, knockbackResistance, repairTag, asset);
    }

    public String id() {
        return id;
    }

    /** What the tier's gear is made from: the boss's drop, or for the ultimate tier the alloy of all four. */
    public String materialId() {
        return materialId;
    }

    public float spearDamage() {
        return spearDamage;
    }

    public ToolMaterial spearMaterial() {
        return spearMaterial;
    }

    public float swordDamage() {
        return swordDamage;
    }

    public float swordSpeed() {
        return swordSpeed;
    }

    public float axeDamage() {
        return axeDamage;
    }

    public float damageBonus() {
        return damageBonus;
    }

    public float miningSpeed() {
        return miningSpeed;
    }

    public int durability() {
        return durability;
    }

    public int defense(ArmorType type) {
        return defense.get(type);
    }

    public float toughness() {
        return toughness;
    }

    public float knockbackResistance() {
        return knockbackResistance;
    }

    public Rarity rarity() {
        return rarity;
    }

    public TagKey<Item> repairTag() {
        return repairTag;
    }

    public ToolMaterial toolMaterial() {
        return toolMaterial;
    }

    public ArmorMaterial armorMaterial() {
        return armorMaterial;
    }

    public boolean unbreakable() {
        return this == ULTIMATE;
    }

    /** True when this tier carries the effects of {@code boss}: its own, or all of them for the ultimate tier. */
    public boolean has(BossTier boss) {
        return this == boss || this == ULTIMATE;
    }
}
