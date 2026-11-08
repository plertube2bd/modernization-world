package com.bossmode.gear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.ArmorType;
import org.jetbrains.annotations.Nullable;

/** The ten pieces each boss tier comes in. */
public enum BossGearKind {
    SWORD("sword", null),
    PICKAXE("pickaxe", null),
    AXE("axe", null),
    SHOVEL("shovel", null),
    HOE("hoe", null),
    SPEAR("spear", null),
    HELMET("helmet", ArmorType.HELMET),
    CHESTPLATE("chestplate", ArmorType.CHESTPLATE),
    LEGGINGS("leggings", ArmorType.LEGGINGS),
    BOOTS("boots", ArmorType.BOOTS);

    private final String id;
    private final @Nullable ArmorType armor;

    BossGearKind(String id, @Nullable ArmorType armor) {
        this.id = id;
        this.armor = armor;
    }

    public String id() {
        return id;
    }

    public @Nullable ArmorType armorType() {
        return armor;
    }

    public boolean isArmor() {
        return armor != null;
    }

    public @Nullable EquipmentSlot slot() {
        return armor == null ? null : armor.getSlot();
    }
}
