package com.bossmode.test;

import com.bossmode.entity.PhaseHealth;
import com.bossmode.entity.boss.BossPhase;
import com.bossmode.entity.boss.ShardDragonEntity;
import com.bossmode.entity.raid.RaidBossEntity;
import com.bossmode.entity.sovereign.SovereignPhase;
import com.bossmode.entity.sovereign.VoidSovereignEntity;
import com.bossmode.entity.warlord.ShardWarlordEntity;
import com.bossmode.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.function.IntToDoubleFunction;

/**
 * Each boss phase is its own pool: 100 HP in phase 1, rising evenly to 300 / 400 / 500 / 600 in each boss's last phase.
 * A summoned Shard Warlord has 60 % of its summoner's current pool.
 */
public final class BossPhaseHealthGameTests {
    private static final String ARENA = "redstone_transportation_test:arena";

    @GameTest(structure = ARENA, maxTicks = 20)
    public void phasePoolsRiseFromOneHundred(GameTestHelper helper) {
        pools(helper, "Shard Dragon", n -> ShardDragonEntity.phasePool(BossPhase.byNumber(n)),
                100, 122, 144, 167, 189, 211, 233, 256, 278, 300);
        RaidBossEntity<?> wyvern = (RaidBossEntity<?>) make(helper, ModEntities.TEMPEST_WYVERN);
        helper.assertValueEqual(wyvern.maxPhase(), 5, "Tempest Wyvern phases");
        pools(helper, "Tempest Wyvern", wyvern::phasePool, 100, 175, 250, 325, 400);
        RaidBossEntity<?> behemoth = (RaidBossEntity<?>) make(helper, ModEntities.BASTION_BEHEMOTH);
        helper.assertValueEqual(behemoth.maxPhase(), 8, "Bastion Behemoth phases");
        pools(helper, "Bastion Behemoth", behemoth::phasePool, 100, 157, 214, 271, 329, 386, 443, 500);
        pools(helper, "Void Sovereign", n -> VoidSovereignEntity.phasePool(SovereignPhase.byNumber(n)),
                100, 156, 211, 267, 322, 378, 433, 489, 544, 600);
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void everyBossStartsWithAHundred(GameTestHelper helper) {
        for (EntityType<?> type : bosses()) {
            LivingEntity boss = make(helper, type);
            helper.assertTrue(boss.getMaxHealth() == 100.0F && boss.getHealth() == 100.0F,
                    type + " starts with " + boss.getHealth() + "/" + boss.getMaxHealth());
        }
        helper.succeed();
    }

    /**
     * A boss saved by the older version (1,024 HP pools), half-way through its last phase, comes back with the last
     * phase's new pool and still half of it.
     */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void oldSavedBossesKeepTheirShareOfTheNewPool(GameTestHelper helper) {
        int[] lastPhase = {10, 5, 8, 10};
        float[] lastPool = {300, 400, 500, 600};
        EntityType<?>[] types = bosses();
        for (int i = 0; i < types.length; i++) {
            LivingEntity old = make(helper, types[i]);
            old.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0D);
            old.setHealth(512.0F);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            old.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            saved.putInt("Phase", lastPhase[i]);
            LivingEntity loaded = (LivingEntity) types[i].create(helper.getLevel(), EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
            helper.assertTrue(loaded.getMaxHealth() == lastPool[i], types[i] + " came back with a pool of " + loaded.getMaxHealth());
            helper.assertTrue(Math.abs(loaded.getHealth() - lastPool[i] / 2.0F) < 0.01F, types[i] + " came back with " + loaded.getHealth());
        }
        helper.succeed();
    }

    /** A summoned warlord has 60 % of its summoner's current phase pool; one placed directly has 180. */
    @GameTest(structure = ARENA, maxTicks = 20)
    public void warlordsHaveAShareOfTheirSummonersPool(GameTestHelper helper) {
        double[][] expected = {
                {ShardDragonEntity.phasePool(BossPhase.byNumber(5)), 113}, {ShardDragonEntity.phasePool(BossPhase.byNumber(10)), 180},
                {VoidSovereignEntity.phasePool(SovereignPhase.byNumber(4)), 160}, {VoidSovereignEntity.phasePool(SovereignPhase.byNumber(10)), 360},
        };
        for (double[] row : expected) {
            helper.assertTrue(ShardWarlordEntity.poolFor(row[0]) == row[1], "warlord for a pool of " + row[0] + ": " + ShardWarlordEntity.poolFor(row[0]));
        }
        RaidBossEntity<?> wyvern = (RaidBossEntity<?>) make(helper, ModEntities.TEMPEST_WYVERN);
        RaidBossEntity<?> behemoth = (RaidBossEntity<?>) make(helper, ModEntities.BASTION_BEHEMOTH);
        helper.assertTrue(ShardWarlordEntity.poolFor(wyvern.phasePool(4)) == 195, "warlord with the wyvern's phase 4");
        helper.assertTrue(ShardWarlordEntity.poolFor(behemoth.phasePool(8)) == 300, "warlord with the behemoth's phase 8");

        ShardWarlordEntity warlord = (ShardWarlordEntity) make(helper, ModEntities.SHARD_WARLORD);
        helper.assertTrue(warlord.getMaxHealth() == 180.0F && warlord.getHealth() == 180.0F,
                "a warlord placed directly has " + warlord.getHealth() + "/" + warlord.getMaxHealth());
        warlord.setPool(113.0D);
        helper.assertTrue(warlord.getMaxHealth() == 113.0F && warlord.getHealth() == 113.0F,
                "a summoned warlord has " + warlord.getHealth() + "/" + warlord.getMaxHealth());
        warlord.setPool(360.0D);
        helper.assertTrue(warlord.getMaxHealth() == 360.0F && warlord.getHealth() == 360.0F,
                "a stronger summoned warlord has " + warlord.getHealth() + "/" + warlord.getMaxHealth());
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 20)
    public void aNewPoolOnlyHoldsHealthToItsMax(GameTestHelper helper) {
        Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        zombie.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        zombie.setHealth(5.0F);
        PhaseHealth.apply(zombie, 300.0D, false);
        helper.assertTrue(zombie.getMaxHealth() == 300.0F && zombie.getHealth() == 5.0F, "raised pool: " + zombie.getHealth() + "/" + zombie.getMaxHealth());
        zombie.setHealth(300.0F);
        PhaseHealth.apply(zombie, 120.0D, false);
        helper.assertTrue(zombie.getMaxHealth() == 120.0F && zombie.getHealth() == 120.0F, "lowered pool: " + zombie.getHealth() + "/" + zombie.getMaxHealth());
        helper.succeed();
    }

    private static void pools(GameTestHelper helper, String boss, IntToDoubleFunction pool, double... expected) {
        for (int n = 1; n <= expected.length; n++) {
            helper.assertTrue(pool.applyAsDouble(n) == expected[n - 1],
                    boss + " phase " + n + " pool: expected " + expected[n - 1] + ", got " + pool.applyAsDouble(n));
        }
    }

    private static EntityType<?>[] bosses() {
        return new EntityType<?>[] {ModEntities.SHARD_DRAGON, ModEntities.TEMPEST_WYVERN, ModEntities.BASTION_BEHEMOTH, ModEntities.VOID_SOVEREIGN};
    }

    /** A boss made but never added to the world. */
    private static LivingEntity make(GameTestHelper helper, EntityType<?> type) {
        LivingEntity boss = (LivingEntity) type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(boss != null, "could not make a " + type);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        boss.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        return boss;
    }
}
