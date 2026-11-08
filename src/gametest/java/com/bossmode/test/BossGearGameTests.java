package com.bossmode.test;

import com.bossmode.gear.BossGear;
import com.bossmode.gear.BossGearKind;
import com.bossmode.gear.BossTier;
import com.bossmode.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The boss gear: what the bosses drop, the numbers on each piece, crafting, forging, set effects and sword hits. */
public final class BossGearGameTests {
    private static final String ARENA = "redstone_transportation_test:arena";
    private static final BossTier[] TIERS = BossTier.values();
    private static final BossGearKind[] ARMOUR = {BossGearKind.HELMET, BossGearKind.CHESTPLATE, BossGearKind.LEGGINGS, BossGearKind.BOOTS};

    // ---- Drops ------------------------------------------------------------------------------------

    @GameTest(structure = ARENA, maxTicks = 20)
    public void eachBossDropsItsMaterialInItsRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer killer = helper.makeMockServerPlayerInLevel();
        Object[][] bosses = {
                {ModEntities.SHARD_DRAGON, BossTier.SHARD, 6, 10},
                {ModEntities.TEMPEST_WYVERN, BossTier.TEMPEST, 6, 10},
                {ModEntities.BASTION_BEHEMOTH, BossTier.BEHEMOTH, 7, 11},
                {ModEntities.VOID_SOVEREIGN, BossTier.VOID, 8, 12},
        };
        for (Object[] boss : bosses) {
            EntityType<?> type = (EntityType<?>) boss[0];
            Item material = BossGear.material((BossTier) boss[1]);
            int low = (int) boss[2];
            int high = (int) boss[3];
            Entity victim = type.create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(victim != null, "could not make a " + type);
            victim.snapTo(killer.position());
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, victim)
                    .withParameter(LootContextParams.ORIGIN, victim.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                    .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .create(LootContextParamSets.ENTITY);
            LootTable table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable().orElseThrow());
            int fewest = Integer.MAX_VALUE;
            int most = 0;
            for (int kill = 0; kill < 200; kill++) {
                int count = 0;
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.is(material)) count += stack.getCount();
                }
                fewest = Math.min(fewest, count);
                most = Math.max(most, count);
            }
            victim.discard();
            helper.assertValueEqual(fewest, low, "fewest " + material + " from 200 kills of " + type);
            helper.assertValueEqual(most, high, "most " + material + " from 200 kills of " + type);
        }
        helper.succeed();
    }

    // ---- Numbers ----------------------------------------------------------------------------------

    @GameTest(structure = ARENA, maxTicks = 20)
    public void weaponsAndToolsMatchTheTable(GameTestHelper helper) {
        float[] sword = {9, 10, 11, 12, 14};
        float[] swordSpeed = {1.6F, 1.8F, 1.4F, 1.6F, 1.7F};
        float[] axe = {11, 12, 13, 14, 16};
        float[] spearOverNetherite = {1, 2, 3, 4, 6};
        float[] mining = {10, 11, 11, 13, 15};
        int[] uses = {2600, 3000, 3800, 4500};
        int[] enchantability = {16, 18, 15, 20, 22};
        double netheriteSpear = attribute(new ItemStack(Items.NETHERITE_SPEAR), Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND);
        helper.assertTrue(Math.abs(netheriteSpear - 5.0D) < 1.0E-4D, "the netherite spear hits for " + netheriteSpear);
        for (BossTier tier : TIERS) {
            int i = tier.ordinal();
            ItemStack swordStack = stack(tier, BossGearKind.SWORD);
            close(helper, attribute(swordStack, Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND), sword[i], tier + " sword damage");
            close(helper, attribute(swordStack, Attributes.ATTACK_SPEED, 4.0D, EquipmentSlot.MAINHAND), swordSpeed[i], tier + " sword speed");
            close(helper, attribute(stack(tier, BossGearKind.AXE), Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND), axe[i], tier + " axe damage");
            close(helper, attribute(stack(tier, BossGearKind.SPEAR), Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND) - netheriteSpear,
                    spearOverNetherite[i], tier + " spear over netherite");
            ItemStack pickaxe = stack(tier, BossGearKind.PICKAXE);
            close(helper, pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()), mining[i], tier + " pickaxe on stone");
            helper.assertTrue(pickaxe.isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState()), tier + " pickaxe cannot mine ancient debris");
            close(helper, stack(tier, BossGearKind.SHOVEL).getDestroySpeed(Blocks.DIRT.defaultBlockState()), mining[i], tier + " shovel on dirt");
            for (BossGearKind kind : BossGearKind.values()) {
                ItemStack piece = stack(tier, kind);
                if (tier.unbreakable()) {
                    helper.assertTrue(piece.has(DataComponents.UNBREAKABLE) && !piece.isDamageableItem(), "ultimate " + kind + " wears out");
                } else if (!kind.isArmor()) {
                    helper.assertValueEqual(piece.getMaxDamage(), uses[i], tier + " " + kind + " uses");
                }
                helper.assertValueEqual(piece.getOrDefault(DataComponents.ENCHANTABLE, null).value(), enchantability[i], tier + " " + kind + " enchantability");
            }
        }
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void armourMatchesTheTable(GameTestHelper helper) {
        int[][] defense = {{3, 8, 7, 3}, {3, 9, 7, 3}, {4, 9, 7, 4}, {4, 10, 8, 4}, {5, 10, 8, 5}};
        float[] toughness = {3.5F, 4, 5, 6, 7};
        float[] knockback = {0.1F, 0.05F, 0.2F, 0.15F, 0.25F};
        float[] lastsOverNetherite = {1.15F, 1.3F, 1.6F, 1.9F};
        int netherite = new ItemStack(Items.NETHERITE_CHESTPLATE).getMaxDamage();
        for (BossTier tier : TIERS) {
            int i = tier.ordinal();
            for (int p = 0; p < ARMOUR.length; p++) {
                ItemStack piece = stack(tier, ARMOUR[p]);
                EquipmentSlot slot = ARMOUR[p].slot();
                close(helper, attribute(piece, Attributes.ARMOR, 0.0D, slot), defense[i][p], tier + " " + ARMOUR[p] + " armour");
                close(helper, attribute(piece, Attributes.ARMOR_TOUGHNESS, 0.0D, slot), toughness[i], tier + " " + ARMOUR[p] + " toughness");
                close(helper, attribute(piece, Attributes.KNOCKBACK_RESISTANCE, 0.0D, slot), knockback[i], tier + " " + ARMOUR[p] + " knockback resistance");
            }
            if (!tier.unbreakable()) {
                double ratio = (double) stack(tier, BossGearKind.CHESTPLATE).getMaxDamage() / netherite;
                helper.assertTrue(Math.abs(ratio - lastsOverNetherite[i]) < 0.02D, tier + " armour lasts " + ratio + " times netherite");
            }
        }
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void everyPieceIsTaggedEnchantableAndRepairable(GameTestHelper helper) {
        Map<BossGearKind, String> tags = Map.of(BossGearKind.SWORD, "swords", BossGearKind.PICKAXE, "pickaxes", BossGearKind.AXE, "axes",
                BossGearKind.SHOVEL, "shovels", BossGearKind.HOE, "hoes", BossGearKind.SPEAR, "spears", BossGearKind.HELMET, "head_armor",
                BossGearKind.CHESTPLATE, "chest_armor", BossGearKind.LEGGINGS, "leg_armor", BossGearKind.BOOTS, "foot_armor");
        Map<BossGearKind, ResourceKey<Enchantment>> enchantments = Map.of(BossGearKind.SWORD, Enchantments.SHARPNESS,
                BossGearKind.PICKAXE, Enchantments.EFFICIENCY, BossGearKind.AXE, Enchantments.SHARPNESS,
                BossGearKind.SHOVEL, Enchantments.EFFICIENCY, BossGearKind.HOE, Enchantments.EFFICIENCY,
                BossGearKind.SPEAR, Enchantments.SHARPNESS, BossGearKind.HELMET, Enchantments.PROTECTION,
                BossGearKind.CHESTPLATE, Enchantments.PROTECTION, BossGearKind.LEGGINGS, Enchantments.PROTECTION,
                BossGearKind.BOOTS, Enchantments.FEATHER_FALLING);
        for (BossTier tier : TIERS) {
            for (BossGearKind kind : BossGearKind.values()) {
                ItemStack piece = stack(tier, kind);
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(tags.get(kind)));
                helper.assertTrue(piece.is(tag), tier + " " + kind + " is not in #" + tags.get(kind));
                helper.assertTrue(enchantment(helper, enchantments.get(kind)).value().isSupportedItem(piece),
                        tier + " " + kind + " cannot take " + enchantments.get(kind));
                helper.assertTrue(piece.has(DataComponents.REPAIRABLE), tier + " " + kind + " cannot be repaired");
                if (tier != BossTier.ULTIMATE) {
                    helper.assertTrue(piece.isValidRepairItem(new ItemStack(BossGear.material(tier))),
                            tier + " " + kind + " is not mended with its boss's material");
                }
            }
        }
        helper.succeed();
    }

    // ---- Crafting ---------------------------------------------------------------------------------

    @GameTest(structure = ARENA, maxTicks = 20)
    public void everyBossPieceIsCraftedFromItsMaterial(GameTestHelper helper) {
        Map<BossGearKind, String[]> shapes = Map.of(
                BossGearKind.SWORD, new String[] {"X", "X", "#"}, BossGearKind.PICKAXE, new String[] {"XXX", " # ", " # "},
                BossGearKind.AXE, new String[] {"XX", "X#", " #"}, BossGearKind.SHOVEL, new String[] {"X", "#", "#"},
                BossGearKind.HOE, new String[] {"XX", " #", " #"}, BossGearKind.SPEAR, new String[] {"  X", " # ", "#  "},
                BossGearKind.HELMET, new String[] {"XXX", "X X"}, BossGearKind.CHESTPLATE, new String[] {"X X", "XXX", "XXX"},
                BossGearKind.LEGGINGS, new String[] {"XXX", "X X", "X X"}, BossGearKind.BOOTS, new String[] {"X X", "X X"});
        for (BossTier tier : BossTier.BOSSES) {
            for (BossGearKind kind : BossGearKind.values()) {
                String[] shape = shapes.get(kind);
                List<ItemStack> grid = new ArrayList<>();
                for (String row : shape) {
                    for (char c : row.toCharArray()) {
                        grid.add(c == 'X' ? new ItemStack(BossGear.material(tier)) : c == '#' ? new ItemStack(Items.STICK) : ItemStack.EMPTY);
                    }
                }
                ItemStack made = craft(helper, CraftingInput.of(shape[0].length(), shape.length, grid));
                helper.assertTrue(made.is(BossGear.gear(tier, kind)), tier + " " + kind + " recipe made " + made);
            }
        }
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void oneOfEachBossPieceForgesTheUltimateOne(GameTestHelper helper) {
        for (BossGearKind kind : BossGearKind.values()) {
            List<ItemStack> four = new ArrayList<>();
            for (BossTier tier : BossTier.BOSSES) four.add(stack(tier, kind));
            ItemStack made = craft(helper, CraftingInput.of(2, 2, four));
            helper.assertTrue(made.is(BossGear.gear(BossTier.ULTIMATE, kind)), "four boss " + kind + "s made " + made);
        }
        // Two of one boss's and none of another's is not enough.
        List<ItemStack> wrong = List.of(stack(BossTier.SHARD, BossGearKind.SWORD), stack(BossTier.SHARD, BossGearKind.SWORD),
                stack(BossTier.BEHEMOTH, BossGearKind.SWORD), stack(BossTier.VOID, BossGearKind.SWORD));
        helper.assertTrue(craft(helper, CraftingInput.of(2, 2, wrong)).isEmpty(), "two shard swords and no tempest sword forged one");
        // Worn pieces are fine: what comes out is new and never wears out.
        List<ItemStack> worn = new ArrayList<>();
        for (BossTier tier : BossTier.BOSSES) {
            ItemStack piece = stack(tier, BossGearKind.PICKAXE);
            piece.setDamageValue(piece.getMaxDamage() - 1);
            worn.add(piece);
        }
        ItemStack ultimate = craft(helper, CraftingInput.of(2, 2, worn));
        helper.assertTrue(ultimate.is(BossGear.gear(BossTier.ULTIMATE, BossGearKind.PICKAXE)) && ultimate.has(DataComponents.UNBREAKABLE),
                "worn pickaxes forged " + ultimate);
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void theUltimatePieceKeepsTheHighestEnchantments(GameTestHelper helper) {
        Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
        Holder<Enchantment> smite = enchantment(helper, Enchantments.SMITE);
        Holder<Enchantment> looting = enchantment(helper, Enchantments.LOOTING);
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);
        Holder<Enchantment> fireAspect = enchantment(helper, Enchantments.FIRE_ASPECT);
        ItemStack shard = stack(BossTier.SHARD, BossGearKind.SWORD);
        shard.enchant(sharpness, 3);
        shard.enchant(fireAspect, 1);
        ItemStack tempest = stack(BossTier.TEMPEST, BossGearKind.SWORD);
        tempest.enchant(sharpness, 5);
        ItemStack behemoth = stack(BossTier.BEHEMOTH, BossGearKind.SWORD);
        behemoth.enchant(looting, 2);
        behemoth.enchant(fireAspect, 2);
        ItemStack voidSword = stack(BossTier.VOID, BossGearKind.SWORD);
        voidSword.enchant(smite, 4);
        voidSword.enchant(unbreaking, 3);
        ItemStack made = craft(helper, CraftingInput.of(2, 2, List.of(shard, tempest, behemoth, voidSword)));
        helper.assertTrue(made.is(BossGear.gear(BossTier.ULTIMATE, BossGearKind.SWORD)), "the swords forged " + made);
        var enchantments = made.getEnchantments();
        helper.assertValueEqual(enchantments.getLevel(sharpness), 5, "sharpness on the ultimate sword");
        helper.assertValueEqual(enchantments.getLevel(fireAspect), 2, "fire aspect on the ultimate sword");
        helper.assertValueEqual(enchantments.getLevel(looting), 2, "looting on the ultimate sword");
        helper.assertValueEqual(enchantments.getLevel(unbreaking), 3, "unbreaking on the ultimate sword");
        helper.assertValueEqual(enchantments.getLevel(smite), 0, "smite, which cannot go with the higher sharpness");
        helper.succeed();
    }

    // ---- Sets -------------------------------------------------------------------------------------

    @GameTest(structure = ARENA, maxTicks = 20)
    public void fullSetsGiveTheirEffectsAndTakeThemBack(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new BlockPos(3, 1, 3));
        double baseSpeed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);

        wear(player, BossTier.TEMPEST);
        BossGear.applySetEffects(player);
        close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseSpeed * 1.1D, "speed in tempest armour");
        close(helper, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5D, "fall damage in tempest armour");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        BossGear.applySetEffects(player);
        close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseSpeed, "speed without the tempest helmet");
        close(helper, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1.0D, "fall damage without the tempest helmet");

        wear(player, BossTier.SHARD);
        BossGear.applySetEffects(player);
        helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "no fire resistance in shard armour");

        player.removeAllEffects();
        wear(player, BossTier.VOID);
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
        BossGear.applySetEffects(player);
        close(helper, player.getMaxHealth(), 24.0D, "max health in void armour");
        helper.assertFalse(player.hasEffect(MobEffects.WITHER), "withered in void armour");

        player.setHealth(24.0F);
        wear(player, BossTier.BEHEMOTH);
        player.setItemSlot(EquipmentSlot.FEET, stack(BossTier.VOID, BossGearKind.BOOTS));
        BossGear.applySetEffects(player);
        helper.assertTrue(BossGear.fullSet(player) == null, "a mixed set counts as a full one");
        close(helper, player.getMaxHealth(), 20.0D, "max health once the void set is off");
        helper.assertTrue(player.getHealth() <= 20.0F, "health over the max once the void set is off");

        wear(player, BossTier.ULTIMATE);
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
        BossGear.applySetEffects(player);
        close(helper, player.getMaxHealth(), 28.0D, "max health in ultimate armour");
        close(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseSpeed * 1.1D, "speed in ultimate armour");
        close(helper, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5D, "fall damage in ultimate armour");
        helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "no fire resistance in ultimate armour");
        helper.assertFalse(player.hasEffect(MobEffects.WITHER), "withered in ultimate armour");
        helper.succeed();
    }

    /** Two zombies with the same armour: the one in the behemoth set takes from a blast of 10 what the other takes from 7. */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void behemothArmourTakesLessFromExplosions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BossTier tier : new BossTier[] {BossTier.BEHEMOTH, BossTier.ULTIMATE}) {
            Mob armoured = loose(helper, EntityTypes.ZOMBIE);
            Mob plain = loose(helper, EntityTypes.ZOMBIE);
            wear(armoured, tier);
            helper.assertValueEqual(armoured.getArmorValue(), plain.getArmorValue(), "armour of the two zombies");
            armoured.hurtServer(level, level.damageSources().explosion(null, null), 10.0F);
            plain.hurtServer(level, level.damageSources().explosion(null, null), 10.0F * BossGear.BEHEMOTH_BLAST_TAKEN);
            close(helper, armoured.getHealth(), plain.getHealth(), tier + " set zombie's health after the blast");
            helper.assertTrue(armoured.getHealth() < armoured.getMaxHealth(), "the blast did nothing");
            // Other damage is not cut.
            close(helper, BossGear.adjustIncomingDamage(armoured, level.damageSources().generic(), 10.0F), 10.0D, tier + " set against other damage");
        }
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void shardArmourHitsMeleeAttackersBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper, new BlockPos(3, 1, 3));
        wear(player, BossTier.SHARD);
        LivingEntity attacker = helper.spawn(EntityTypes.HUSK, new BlockPos(5, 1, 3));
        float before = attacker.getHealth();
        player.hurtServer(level, level.damageSources().mobAttack(attacker), 4.0F);
        float lost = before - attacker.getHealth();
        helper.assertTrue(lost > 1.5F && lost <= BossGear.SHARD_THORNS, "the attacker lost " + lost);
        helper.succeed();
    }

    // ---- Swords -----------------------------------------------------------------------------------

    @GameTest(structure = ARENA, maxTicks = 40)
    public void bossSwordsDoTheirOwnThingOnAHit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper, new BlockPos(3, 1, 3));

        LivingEntity burnt = helper.spawn(EntityTypes.HUSK, new BlockPos(5, 1, 3));
        BossGear.swordHit(level, player, burnt, BossTier.SHARD, false);
        helper.assertTrue(burnt.getRemainingFireTicks() > 0, "the shard blade did not set it alight");

        LivingEntity withered = helper.spawn(EntityTypes.HUSK, new BlockPos(5, 1, 5));
        BossGear.swordHit(level, player, withered, BossTier.VOID, false);
        helper.assertTrue(withered.hasEffect(MobEffects.WITHER), "the void blade did not wither it");

        LivingEntity thrown = helper.spawn(EntityTypes.HUSK, new BlockPos(5, 1, 7));
        player.snapTo(thrown.getX() - 2.0D, thrown.getY(), thrown.getZ());
        BossGear.swordHit(level, player, thrown, BossTier.BEHEMOTH, false);
        helper.assertTrue(thrown.getDeltaMovement().x > 1.0D, "the behemoth blade pushed it " + thrown.getDeltaMovement());

        LivingEntity struck = helper.spawn(EntityTypes.HUSK, new BlockPos(9, 1, 9));
        BossGear.swordHit(level, player, struck, BossTier.TEMPEST, true);
        AABB around = struck.getBoundingBox().inflate(2.0D);
        helper.assertFalse(level.getEntitiesOfClass(LightningBolt.class, around).isEmpty(), "the tempest blade called no lightning");

        RandomSource random = RandomSource.create(0x7E3B1L);
        int bolts = 0;
        for (int i = 0; i < 10_000; i++) {
            if (BossGear.rollsLightning(random)) bolts++;
        }
        helper.assertValueInBetween(900, bolts, 1_100, "lightning out of 10,000 tempest hits");
        helper.succeed();
    }

    /** A real swing with the shard sword sets the target alight: the hit reaches the sword's effect. */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void aRealSwingUsesTheSwordsEffect(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new BlockPos(3, 1, 3));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(BossTier.SHARD, BossGearKind.SWORD));
        LivingEntity target = helper.spawn(EntityTypes.HUSK, new BlockPos(4, 1, 3));
        player.attack(target);
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the swing missed");
        helper.assertTrue(target.getRemainingFireTicks() > 0, "the swing did not set it alight");
        helper.succeed();
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private static ItemStack stack(BossTier tier, BossGearKind kind) {
        return new ItemStack(BossGear.gear(tier, kind));
    }

    private static void wear(LivingEntity entity, BossTier tier) {
        for (BossGearKind kind : ARMOUR) entity.setItemSlot(kind.slot(), stack(tier, kind));
    }

    private static double attribute(ItemStack stack, Holder<Attribute> attribute, double base, EquipmentSlot slot) {
        double value = base;
        for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().test(slot)) value += entry.modifier().amount();
        }
        return value;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-3D, what + ": expected " + expected + ", got " + actual);
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack craft(GameTestHelper helper, CraftingInput input) {
        ServerLevel level = helper.getLevel();
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    private static ServerPlayer survivor(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        // Until its client says it has loaded, a player cannot be hurt; a mock one never says so.
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        BlockPos at = helper.absolutePos(pos);
        player.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    /** A mob made but never added to the world, so it never ticks (and never picks up its armour's attributes). */
    private static Mob loose(GameTestHelper helper, EntityType<? extends Mob> type) {
        Mob mob = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(mob != null, "could not make a " + type);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        mob.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        return mob;
    }
}
