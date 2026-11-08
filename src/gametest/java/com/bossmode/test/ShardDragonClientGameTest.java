package com.bossmode.test;

import com.bossmode.BossMode;
import com.bossmode.client.model.PolyMeshGeoBone;
import com.bossmode.config.BossConfig;
import com.bossmode.entity.DamageKind;
import com.bossmode.block.ShardAltarBlock;
import com.bossmode.block.VoidAltarBlock;
import com.bossmode.config.SovereignConfig;
import com.bossmode.entity.projectile.VoidOrbProjectile;
import com.bossmode.entity.sovereign.SovereignPhase;
import com.bossmode.entity.sovereign.VoidSovereignEntity;
import com.bossmode.entity.boss.BossPhase;
import com.bossmode.entity.boss.BossState;
import com.bossmode.entity.boss.ShardDragonEntity;
import com.bossmode.entity.minion.MinionType;
import com.bossmode.entity.minion.ShardHoundEntity;
import com.bossmode.entity.projectile.WyrmShardProjectile;
import com.bossmode.entity.warlord.ShardWarlordEntity;
import com.bossmode.loot.ShardDrops;
import com.bossmode.registry.ModBlocks;
import com.bossmode.registry.ModEntities;
import com.bossmode.registry.ModItems;
import com.bossmode.world.ArenaTerraformer;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * End-to-end check in a real Minecraft 26.3 client + integrated server: asset baking of all three Blender models,
 * the dragon's intro, all 13 patterns, all 10 phases (growth, sealed damage types, minion roster, mid-bosses),
 * the minion and warlord behaviours, the decorated HUD, save/load, death and loot, the shared shard drop rate of natural
 * and raid mobs, and - in normally generated worlds - the Shard Shrine and an altar summoning, the Tempest Wyvern and the
 * Bastion Behemoth with their structures and altars (RaidBossGameTests), and the Void Sovereign called with their trophies.
 * Run with {@code ./gradlew runClientGameTest}; screenshots go to build/run/clientGameTest/screenshots.
 */
public class ShardDragonClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("boss_mode-test");
	private static final String BOSS = "@e[type=boss_mode:shard_dragon,limit=1]";

	@Override
	public void runTest(ClientGameTestContext context) {
		checkAssets(context);
		if ("altar".equals(System.getenv("BOSS_MODE_TEST_ONLY"))) {
			testShrine(context);
			LOG.info("[boss_mode-test] altar checks passed");
			return;
		}
		if ("sovereign".equals(System.getenv("BOSS_MODE_TEST_ONLY"))) {
			testSovereign(context);
			LOG.info("[boss_mode-test] sovereign checks passed");
			return;
		}
		if ("wyvern".equals(System.getenv("BOSS_MODE_TEST_ONLY"))) {
			RaidBossGameTests.run(context, RaidBossGameTests.WYVERN);
			LOG.info("[boss_mode-test] wyvern checks passed");
			return;
		}
		if ("behemoth".equals(System.getenv("BOSS_MODE_TEST_ONLY"))) {
			RaidBossGameTests.run(context, RaidBossGameTests.BEHEMOTH);
			LOG.info("[boss_mode-test] behemoth checks passed");
			return;
		}
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			Vec3 player = server.computeOnServer(s -> player(s).position());
			Vec3 boss = player.add(0, 0, -24);

			// ---------------------------------------------------------------- intro / phase 1
			view(server, boss, 26, 9);
			server.runCommand(fmt("summon boss_mode:shard_dragon %.2f %.2f %.2f", boss.x, boss.y, boss.z));
			context.waitTicks(14);
			assertState(server, BossState.SPAWNING, "spawn intro plays");
			shot(context, "01_spawn");
			server.waitFor(s -> stateOf(s) == BossState.IDLE, 120);
			server.runOnServer(s -> {
				ShardDragonEntity d = requireBoss(s);
				check(d.getMaxHealth() == 100.0F && d.getHealth() == 100.0F, "phase 1 pool of 100 HP (" + d.getHealth() + "/" + d.getMaxHealth() + ")");
				check(Math.abs(d.getScale() - 2.0F) < 0.01F, "phase 1 size x2.0 (" + d.getScale() + ")");
				check(d.getBossBar().viewerCount() == 1, "decorated boss bar shown to the player");
			});
			context.waitTicks(10);
			shot(context, "02_phase1_hud");

			// ---------------------------------------------------------------- every dragon pattern
			buffPlayer(server);
			server.runCommand("gamemode survival @a");
			server.waitFor(s -> requireBoss(s).getTarget() != null, 200);
			List<String> all = server.computeOnServer(s -> requireBoss(s).getActionController().actionIds());
			List<String> ids = all.stream().filter(id -> !ShardDragonEntity.FLIGHT_ACTIONS.contains(id)).toList();
			check(ids.size() == 13, "13 dragon patterns (Wither Storm: ~6 abilities)");
			check(all.containsAll(ShardDragonEntity.FLIGHT_ACTIONS), "plus flight maneuvers " + ShardDragonEntity.FLIGHT_ACTIONS);
			for (String id : ids) {
				Vec3 at = server.computeOnServer(s -> requireBoss(s).position());
				view(server, at, 22, 8);
				context.waitTicks(3);
				server.runOnServer(s -> check(requireBoss(s).getActionController().forceAction(s.overworld(), id), "started " + id));
				int executionTick = server.waitFor(s -> {
					BossState st = stateOf(s);
					return st == BossState.SPECIAL_EXECUTION || st == BossState.ATTACKING;
				}, 140);
				context.waitTicks(id.equals("prism_beam") ? 10 : id.equals("crystal_rain") ? 20 : 2);
				shot(context, "03_" + id);
				if (id.equals("shard_volley")) {
					server.runOnServer(s -> check(!s.overworld().getEntities(ModEntities.WYRM_SHARD, e -> true).isEmpty(), "volley spawned projectiles"));
				}
				server.waitFor(s -> !id.equals(requireBoss(s).getActionController().currentActionId()), 200);
				server.runOnServer(s -> {
					ShardDragonEntity d = requireBoss(s);
					check(!id.equals(d.getActionController().currentActionId()) && !d.noPhysics, id + " finished and released the state machine");
					if (id.equals("obsidian_charge") || id.equals("meteor_dive")) {
						check(d.position().distanceTo(at) > 3.0, id + " moved the dragon " + String.format(Locale.ROOT, "%.1f", d.position().distanceTo(at)) + " blocks");
					}
				});
				LOG.info("[boss_mode-test] {} executed after {} ticks", id, executionTick);
			}
			server.runCommand("gamemode creative @a");
			clearMinions(server);

			// ---------------------------------------------------------------- phases 2..9
			for (int n = 2; n <= 9; n++) {
				BossPhase phase = BossPhase.byNumber(n);
				breakPhase(server);
				server.waitFor(s -> stateOf(s) == BossState.PHASE_TRANSITION, 60);
				server.waitFor(s -> stateOf(s) != BossState.PHASE_TRANSITION, 120);
				final int number = n;
				server.runOnServer(s -> {
					ShardDragonEntity d = requireBoss(s);
					check(d.getPhase().number() == number, "reached phase " + number);
					check(Math.abs(d.getScale() - phase.scale()) < 0.01F, "phase " + number + " size x" + phase.scale());
					check(d.getMaxHealth() == (float) ShardDragonEntity.phasePool(d.getPhase()), "phase " + number + " pool is " + ShardDragonEntity.phasePool(d.getPhase()) + " (" + d.getMaxHealth() + ")");
					check(d.getHealth() == d.getMaxHealth(), "phase " + number + " pool refilled");
					checkEntryWave(s, d, phase);
				});
				checkSealed(server, phase);
				if (n == 9) {
					testFlight(context, server);
				}
				if (n == 5) {
					Vec3 at = server.computeOnServer(s -> requireBoss(s).position());
					view(server, at, 34, 12);
					context.waitTicks(40);
					shot(context, "04_phase5_warlord_hud");
				}
				clearMinions(server);
			}

			// ---------------------------------------------------------------- phase 10: melee always (no more "exposed only")
			breakPhase(server);
			server.waitFor(s -> stateOf(s) == BossState.PHASE_TRANSITION, 60);
			server.waitFor(s -> stateOf(s) != BossState.PHASE_TRANSITION, 120);
			Vec3 at10 = server.computeOnServer(s -> requireBoss(s).position());
			view(server, at10, 40, 14);
			context.waitTicks(10);
			shot(context, "05_phase10_hud");
			server.runOnServer(s -> {
				ShardDragonEntity d = requireBoss(s);
				check(d.getPhase() == BossPhase.TEN && Math.abs(d.getScale() - 4.0F) < 0.01F, "phase 10 reached at size x4.0");
				check(d.getBossBar().isDarkeningSky(), "sky darkens in the final phases");
			});
			checkSealed(server, BossPhase.TEN);
			server.runCommand("gamemode survival @a");
			server.waitFor(s -> requireBoss(s).getTarget() != null, 200);
			server.runOnServer(s -> requireBoss(s).getActionController().forceAction(s.overworld(), "cataclysm"));
			server.waitFor(s -> stateOf(s) == BossState.SPECIAL_RECOVERY, 120);
			shot(context, "06_cataclysm_exposed");
			float before = server.computeOnServer(s -> {
				requireBoss(s).damageCooldownTime = 0;
				return requireBoss(s).getHealth();
			});
			server.runCommand("damage " + BOSS + " 30 minecraft:player_attack by @p");
			server.runOnServer(s -> check(requireBoss(s).getHealth() < before, "EXPOSED dragon takes melee damage in phase 10"));
			server.runCommand("gamemode creative @a");
			server.waitFor(s -> stateOf(s) != BossState.SPECIAL_RECOVERY, 120);
			clearMinions(server);
		}

		// ---------------------------------------------------------------- save / load
		try (TestSingleplayerContext world = save.open()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.waitFor(s -> findBoss(s) != null, 200);
			server.runOnServer(s -> {
				ShardDragonEntity d = requireBoss(s);
				check(d.getPhase() == BossPhase.TEN, "phase 10 persisted across save/load");
				check(Math.abs(d.getScale() - 4.0F) < 0.01F, "size persisted across save/load");
				check(d.isIntroFinished() && d.getBossState() != BossState.SPAWNING, "intro not replayed after load");
			});
			Vec3 dragonAt = server.computeOnServer(s -> requireBoss(s).position());

			// ------------------------------------------------------------ minions
			testMinions(context, server);
			// ------------------------------------------------------------ mid-boss
			testWarlord(context, server);

			// ------------------------------------------------------------ death, loot, cleanup
			// the minion tests ran 100 blocks away; come back so the dragon's chunk is loaded again
			view(server, dragonAt, 36, 14);
			server.waitFor(s -> findBoss(s) != null, 300);
			Vec3 at = server.computeOnServer(s -> requireBoss(s).position());
			view(server, at, 36, 14);
			server.runOnServer(s -> {
				ShardDragonEntity d = requireBoss(s);
				LOG.info("[boss_mode-test] before the kill: phase {}, state {}, health {}/{}", d.getPhase().number(), d.getBossState(), d.getHealth(), d.getMaxHealth());
			});
			server.runCommand("damage " + BOSS + " 1 minecraft:player_attack by @p");
			server.runCommand("kill " + BOSS);
			// The death animation lasts 50 ticks: look at it straight away, before a slow screenshot lets it end.
			context.waitTicks(5);
			server.runOnServer(s -> {
				ShardDragonEntity d = findBoss(s);
				List<? extends ShardDragonEntity> any = s.overworld().getEntities(ModEntities.SHARD_DRAGON, e -> true);
				String seen = d != null ? "phase " + d.getPhase().number() + ", state " + d.getBossState() + ", health " + d.getHealth() + ", deathTime " + d.deathTime
						: any.isEmpty() ? "no dragon at all" : "removed: " + any.getFirst().getRemovalReason();
				check(d != null && d.getBossState() == BossState.DYING, "death animation running (" + seen + ")");
				check(d.getBossBar().viewerCount() == 0, "boss bar removed on death");
			});
			context.waitTicks(20);
			shot(context, "09_death");
			server.waitFor(s -> findBoss(s) == null, 120);
			server.runOnServer(s -> {
				AABB area = new AABB(at, at).inflate(20);
				List<ItemEntity> items = s.overworld().getEntitiesOfClass(ItemEntity.class, area);
				check(items.stream().anyMatch(i -> i.getItem().is(ModItems.WYRM_SHARD)), "loot dropped Wyrm Shards");
				check(items.stream().anyMatch(i -> i.getItem().is(ModItems.WYRM_HEART)), "the Shard Dragon left a Wyrm Heart (3 call the final boss)");
				check(!s.overworld().getEntitiesOfClass(ExperienceOrb.class, area).isEmpty(), "experience dropped");
				LOG.info("[boss_mode-test] drops: {}", items.stream().map(i -> i.getItem().toString()).toList());
			});
		}

		// ---------------------------------------------------------------- Shard Shrine world generation + altar summoning
		testShrine(context);

		// ---------------------------------------------------------------- the raid bosses: Tempest Aerie / Wyvern, Bastion Den / Behemoth
		RaidBossGameTests.run(context, RaidBossGameTests.WYVERN);
		RaidBossGameTests.run(context, RaidBossGameTests.BEHEMOTH);

		// ---------------------------------------------------------------- the final boss: Void Sanctum, Void Altar, Void Sovereign
		testSovereign(context);
		LOG.info("[boss_mode-test] all checks passed");
	}

	// ==================================================================================================== shard economy
	/** Rolls each loot table many times with a player kill and compares the shard rate. */
	private static void checkShardDrops(TestServerContext server) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer killer = player(s);
			check(ShardDrops.dropTables().contains(EntityTypes.ZOMBIE.getDefaultLootTable().orElseThrow()), "zombie loot table gets the shard pool");
			check(!ShardDrops.dropTables().contains(ModEntities.SHARD_DRAGON.getDefaultLootTable().orElseThrow()), "the dragon keeps its own shard table");
			int rolls = 4000;
			List<EntityType<? extends Mob>> all = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER, EntityTypes.SPIDER,
					EntityTypes.ENDERMAN, ModEntities.SHARD_HOUND, ModEntities.SHARD_SPITTER, ModEntities.VOLATILE_HOUND, ModEntities.SHARD_WARLORD);
			double expected = BossConfig.SHARD_DROP_CHANCE * rolls;
			double sigma = Math.sqrt(rolls * BossConfig.SHARD_DROP_CHANCE * (1 - BossConfig.SHARD_DROP_CHANCE));
			for (EntityType<? extends Mob> type : all) {
				int shards = rollShards(level, type, killer, rolls);
				check(Math.abs(shards - expected) < 5 * sigma, type.getDescriptionId() + ": " + shards + " shards / " + rolls + " player kills (expected ~"
						+ (int) expected + ")");
			}
			check(rollShards(level, EntityTypes.ZOMBIE, null, 1000) == 0, "no shards without a player kill");
			int min = Integer.MAX_VALUE, max = 0;
			for (int i = 0; i < 50; i++) {
				int n = rollShards(level, ModEntities.SHARD_DRAGON, killer, 1);
				min = Math.min(min, n);
				max = Math.max(max, n);
			}
			check(min >= 2 && max <= 4, "dragon trophy drop is 2-4 shards (50 kills: " + min + "-" + max + ")");
		});
	}

	private static int rollShards(ServerLevel level, EntityType<? extends Mob> type, @Nullable ServerPlayer killer, int rolls) {
		Mob victim = type.create(level, EntitySpawnReason.COMMAND);
		if (victim == null) {
			throw new AssertionError("[boss_mode-test] FAILED: could not create " + type.getDescriptionId());
		}
		victim.snapTo(level.players().getFirst().position());
		LootParams.Builder builder = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, victim)
				.withParameter(LootContextParams.ORIGIN, victim.position());
		if (killer != null) {
			builder.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
					.withParameter(LootContextParams.ATTACKING_ENTITY, killer)
					.withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
					.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
		} else {
			builder.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
		}
		LootParams params = builder.create(LootContextParamSets.ENTITY);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable().orElseThrow());
		int shards = 0;
		for (int i = 0; i < rolls; i++) {
			for (ItemStack stack : table.getRandomItems(params)) {
				if (stack.is(ModItems.WYRM_SHARD)) {
					shards += stack.getCount();
				}
			}
		}
		victim.discard();
		return shards;
	}

	// ==================================================================================================== shrine / altar
	private static void testShrine(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(settings -> {
			settings.setSeed("26030");
			settings.setGenerateStructures(true);
			settings.setAllowCommands(true);
			settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
		}).create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:send_command_feedback false");
			// one shard drop rule for natural mobs and raid mobs
			checkShardDrops(server);
			server.runCommand("locate structure boss_mode:shard_shrine");
			BlockPos located = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Holder<Structure> shrine = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.getOrThrow(ResourceKey.create(Registries.STRUCTURE, BossMode.id("shard_shrine")));
				Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(shrine), player(s).blockPosition(), 100, false);
				check(found != null, "Shard Shrine generates in the world (found like /locate)");
				return found.getFirst();
			});
			BlockPos spawn = server.computeOnServer(s -> player(s).blockPosition());
			LOG.info("[boss_mode-test] nearest Shard Shrine at {} ({} blocks from spawn)", located.toShortString(),
					(int) Math.sqrt(located.distToLowCornerSqr(spawn.getX(), located.getY(), spawn.getZ())));
			server.runCommand(fmt("tp @a %d 200 %d", located.getX(), located.getZ()));
			server.waitFor(s -> findAltar(s.overworld(), located) != null, 1200);
			BlockPos altarPos = server.computeOnServer(s -> findAltar(s.overworld(), located));
			Direction facing = server.computeOnServer(s -> s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.FACING));
			Vec3 summon = server.computeOnServer(s -> ShardAltarBlock.summonPosition(s.overworld(), altarPos, s.overworld().getBlockState(altarPos)));
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockState state = level.getBlockState(altarPos);
				check(state.getValue(ShardAltarBlock.CHARGE) == 0 && state.getValue(ShardAltarBlock.RITUAL) == 0,
						"generated altar " + altarPos.toShortString() + " is dormant, facing " + facing);
				check(BlockPos.betweenClosedStream(new AABB(altarPos).inflate(3)).anyMatch(p -> level.getBlockState(p).is(Blocks.CHEST)), "offering chest beside the altar");
				check(Math.abs(summon.y - altarPos.getY()) <= 2, "arena floor in front of the altar (summon point " + summon + ")");
			});
			Vec3 front = Vec3.atLowerCornerOf(facing.getUnitVec3i());
			Vec3 side = Vec3.atLowerCornerOf(facing.getClockWise().getUnitVec3i());
			Vec3 altarTop = Vec3.atBottomCenterOf(altarPos).add(0, 0.9, 0);
			Vec3 overview = summon.add(front.scale(10)).add(side.scale(6)).add(0, 10, 0);
			Vec3 closeUp = Vec3.atBottomCenterOf(altarPos).add(front.scale(2.2)).add(side.scale(1.3)).add(0, 1.62, 0);
			fly(server);
			look(server, overview, altarTop.add(front.scale(4)));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, "10_shard_shrine");
			look(server, closeUp, altarTop);
			context.waitTicks(5);
			shot(context, "11_altar_dormant");

			// ---- offering: one by one, then the rest while sneaking
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_SHARD, ShardAltarBlock.COST));
				for (int i = 0; i < 5; i++) {
					offer(s, altarPos, false);
				}
				check(s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.CHARGE) == 5, "five shards offered one by one (charge 5)");
				check(p.getMainHandItem().getCount() == ShardAltarBlock.COST - 5, "offered shards consumed");
			});
			look(server, closeUp, altarTop);
			context.waitTicks(40);
			shot(context, "12_altar_charging");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				offer(s, altarPos, true);
				BlockState state = s.overworld().getBlockState(altarPos);
				check(state.getValue(ShardAltarBlock.CHARGE) == ShardAltarBlock.COST && state.getValue(ShardAltarBlock.RITUAL) == 1,
						"sneak-offering filled the altar and started the ritual");
				check(p.getMainHandItem().isEmpty(), "all " + ShardAltarBlock.COST + " shards consumed");
			});
			fly(server);
			look(server, overview, altarTop.add(front.scale(4)));
			context.waitTicks(BossConfig.ALTAR_RITUAL_STEPS * BossConfig.ALTAR_RITUAL_STEP_TICKS * 3 / 5);
			shot(context, "13_altar_ritual");
			server.runOnServer(s -> check(s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.RITUAL) > 5, "ritual advancing"));
			server.waitFor(s -> findBoss(s) != null, BossConfig.ALTAR_RITUAL_STEPS * BossConfig.ALTAR_RITUAL_STEP_TICKS + 40);
			server.runOnServer(s -> {
				ShardDragonEntity d = requireBoss(s);
				check(d.position().distanceTo(summon) < 1.5, "Shard Dragon rose in the arena in front of the altar (" + d.blockPosition().toShortString() + ")");
				check(d.getBossState() == BossState.SPAWNING, "summoned dragon plays its intro");
				BlockState state = s.overworld().getBlockState(altarPos);
				check(state.getValue(ShardAltarBlock.CHARGE) == 0 && state.getValue(ShardAltarBlock.RITUAL) == 0, "altar spent and reusable");
			});
			context.waitTicks(16);
			shot(context, "14_altar_summoned");

			// ---- the summoning levels and corrupts the land around the altar into an arena
			fly(server);
			server.waitFor(s -> !ArenaTerraformer.isBusy(s.overworld()), 1200);
			int floorY = ShardAltarBlock.arenaFloor(altarPos, summon);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				checkArena(level, altarPos, floorY, "shrine");
				BlockPos inside = altarPos.relative(facing.getClockWise(), 25);
				check(level.getBiome(inside).is(ArenaTerraformer.SHARD_WASTES), "arena biome is now Shard Wastes");
				BlockPos outside = altarPos.relative(facing.getClockWise(), BossConfig.ARENA_RADIUS + BossConfig.ARENA_BLEND + 20);
				check(!level.getBiome(outside).is(ArenaTerraformer.SHARD_WASTES), "biome outside the arena unchanged");
			});
			Vec3 arenaMid = Vec3.atBottomCenterOf(altarPos).add(front.scale(12));
			look(server, arenaMid.add(side.scale(42)).add(front.scale(26)).add(0, 34, 0), arenaMid.add(0, floorY + 1 - arenaMid.y, 0));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, "15_arena_overview");
			look(server, new Vec3(arenaMid.x, floorY + 3.2, arenaMid.z).add(front.scale(22)), Vec3.atBottomCenterOf(altarPos).add(0, 3, 0));
			context.waitTicks(10);
			shot(context, "16_arena_ground");

			// ---- a second dragon is refused; breaking the altar gives back what was offered (survival rules)
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_SHARD, ShardAltarBlock.COST));
				offer(s, altarPos, true);
				check(s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.CHARGE) == 0 && p.getMainHandItem().getCount() == ShardAltarBlock.COST,
						"final shard refused while a Shard Dragon is alive");
				offer(s, altarPos, false);
				offer(s, altarPos, false);
				check(s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.CHARGE) == 2, "pre-charging still allowed");
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
				check(p.gameMode.destroyBlock(altarPos), "altar mined with a diamond pickaxe");
				List<ItemEntity> drops = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(altarPos).inflate(2));
				int shards = drops.stream().filter(i -> i.getItem().is(ModItems.WYRM_SHARD)).mapToInt(i -> i.getItem().getCount()).sum();
				check(shards == 2, "breaking the altar returned the 2 offered shards");
				check(drops.stream().anyMatch(i -> i.getItem().is(ModItems.SHARD_ALTAR)), "altar dropped itself");
				requireBoss(s).discard();
			});
			fly(server);

			// ---- worst case: an altar placed on a mountain flattens a whole peak
			BlockPos peak = server.computeOnServer(s -> {
				Pair<BlockPos, Holder<Biome>> found = s.overworld().findClosestBiome3d(
						b -> b.is(Biomes.JAGGED_PEAKS) || b.is(Biomes.STONY_PEAKS) || b.is(Biomes.FROZEN_PEAKS) || b.is(Biomes.WINDSWEPT_HILLS),
						altarPos, 4000, 32, 64);
				check(found != null, "found mountains for the worst-case arena");
				return found.getFirst();
			});
			server.runCommand(fmt("tp @a %d 330 %d", peak.getX(), peak.getZ()));
			server.waitFor(s -> {
				for (int cx = -4; cx <= 4; cx++) {
					for (int cz = -4; cz <= 4; cz++) {
						if (!s.overworld().hasChunk((peak.getX() >> 4) + cx, (peak.getZ() >> 4) + cz)) {
							return false;
						}
					}
				}
				return true;
			}, 1200);
			BlockPos mountainAltar = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockPos top = new BlockPos(peak.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, peak.getX(), peak.getZ()), peak.getZ());
				level.setBlockAndUpdate(top, ModBlocks.SHARD_ALTAR.defaultBlockState());
				return top;
			});
			Vec3 mountainSummon = server.computeOnServer(s -> ShardAltarBlock.summonPosition(s.overworld(), mountainAltar, s.overworld().getBlockState(mountainAltar)));
			int mountainRelief = server.computeOnServer(s -> {
				int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
				for (int dx = -BossConfig.ARENA_RADIUS; dx <= BossConfig.ARENA_RADIUS; dx += 8) {
					for (int dz = -BossConfig.ARENA_RADIUS; dz <= BossConfig.ARENA_RADIUS; dz += 8) {
						int h = s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mountainAltar.getX() + dx, mountainAltar.getZ() + dz);
						min = Math.min(min, h);
						max = Math.max(max, h);
					}
				}
				return max - min;
			});
			look(server, Vec3.atBottomCenterOf(mountainAltar).add(-50, 40, -50), Vec3.atBottomCenterOf(mountainAltar));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, "17_mountain_before");
			server.runOnServer(s -> {
				player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_SHARD, ShardAltarBlock.COST));
				offer(s, mountainAltar, true);
			});
			server.waitFor(s -> findBoss(s) != null, BossConfig.ALTAR_RITUAL_STEPS * BossConfig.ALTAR_RITUAL_STEP_TICKS + 40);
			server.waitFor(s -> !ArenaTerraformer.isBusy(s.overworld()), 2400);
			server.runOnServer(s -> {
				LOG.info("[boss_mode-test] mountain site: {} blocks of relief across the arena", mountainRelief);
				int floor = ShardAltarBlock.arenaFloor(mountainAltar, mountainSummon);
				check(floor >= mountainAltar.getY() - 3 && floor <= mountainAltar.getY() - 1, "mountain arena floor " + floor + " stays at the altar's level (" + mountainAltar.getY() + ")");
				checkArena(s.overworld(), mountainAltar, floor, "mountain");
				ShardDragonEntity d = requireBoss(s);
				check(Math.abs(d.getY() - (floor + 1)) < 1.0, "dragon stands on the new arena floor (y=" + String.format(Locale.ROOT, "%.1f", d.getY()) + ")");
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, "18_mountain_after");
			server.runOnServer(s -> requireBoss(s).discard());
		}
	}

	/** Puts the camera (eyes) at {@code from} looking at {@code at}; /tp aims from the feet, hence the eye-height shift. */
	static void look(TestServerContext server, Vec3 from, Vec3 at) {
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", from.x, from.y - 1.62, from.z, at.x, at.y - 1.62, at.z));
	}

	/** Creative flight so a camera placed in the air stays there. */
	static void fly(TestServerContext server) {
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			player(s).getAbilities().flying = true;
			player(s).onUpdateAbilities();
		});
	}

	/** Every column of the flat arena is at the floor height (or is an obsidian spire / crystal), none is natural ground. */
	static void checkArena(ServerLevel level, BlockPos altar, int floorY, String label) {
		checkArena(level, altar, floorY, label, BossConfig.ARENA_RADIUS, ModBlocks.SHARD_ALTAR);
	}

	private static void checkArena(ServerLevel level, BlockPos altar, int floorY, String label, int r, Block altarBlock) {
		checkArena(level, altar, floorY, label, r, altarBlock, altarBlock == ModBlocks.VOID_ALTAR ? ArenaTerraformer.Theme.VOID : ArenaTerraformer.Theme.SHARD);
	}

	static void checkArena(ServerLevel level, BlockPos altar, int floorY, String label, int r, Block altarBlock, ArenaTerraformer.Theme theme) {
		int flat = 0, spire = 0, crystals = 0;
		List<String> bad = new ArrayList<>();
		Set<Block> spireBlocks = switch (theme) {
			case SHARD, VOID -> Set.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER,
					Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.END_ROD);
			case STORM -> Set.of(Blocks.STONE_BRICKS, Blocks.TUFF_BRICKS, Blocks.COPPER_BLOCK.waxed().oxidized(), Blocks.LIGHTNING_ROD.waxed().oxidized());
			case EMBER -> Set.of(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.CAMPFIRE);
		};
		Set<Block> crowns = Set.of(Blocks.AMETHYST_CLUSTER, Blocks.END_ROD, Blocks.LIGHTNING_ROD.waxed().oxidized(), Blocks.CAMPFIRE);
		Set<Block> natural = new HashSet<>(Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.STONE, Blocks.SAND, Blocks.WATER,
				Blocks.GRAVEL, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.POWDER_SNOW, Blocks.PACKED_ICE, Blocks.ICE));
		if (theme == ArenaTerraformer.Theme.STORM) {
			natural.removeAll(Set.of(Blocks.STONE, Blocks.GRAVEL, Blocks.ANDESITE)); // the storm plateau's own floor
		}
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d <= BossConfig.ARENA_PROTECT_RADIUS || d > r) {
					continue;
				}
				int x = altar.getX() + dx, z = altar.getZ() + dz;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockState floor = level.getBlockState(new BlockPos(x, floorY, z));
				BlockState topState = level.getBlockState(new BlockPos(x, top, z));
				if (natural.contains(floor.getBlock())) {
					bad.add(x + "," + z + " floor " + floor.getBlock());
				} else if (top == floorY) {
					flat++;
				} else if (top > floorY && spireBlocks.contains(topState.getBlock())) {
					spire++;
					crystals += crowns.contains(topState.getBlock()) ? 1 : 0;
				} else {
					bad.add(x + "," + z + " top y=" + top + " " + topState.getBlock());
				}
			}
		}
		check(bad.isEmpty(), label + " arena levelled to y=" + floorY + ": " + flat + " flat columns, " + spire + " spire/crystal columns"
				+ (bad.isEmpty() ? "" : ", " + bad.size() + " not levelled: " + bad.subList(0, Math.min(8, bad.size()))));
		check(crystals >= BossConfig.ARENA_SPIRES / 2, label + ": " + crystals + " capped spires left as cover");
		check(level.getBlockState(altar).is(altarBlock), label + ": altar and its dais untouched");
		int leftovers = 0;
		for (BlockPos p : BlockPos.betweenClosed(altar.offset(-BossConfig.ARENA_PROTECT_RADIUS, 1 - (altar.getY() - floorY), -BossConfig.ARENA_PROTECT_RADIUS),
				altar.offset(BossConfig.ARENA_PROTECT_RADIUS, 24, BossConfig.ARENA_PROTECT_RADIUS))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.SNOW)) {
				leftovers++;
			}
		}
		check(leftovers == 0, label + ": no rock, soil, trees or snow left above the floor around the altar (" + leftovers + ")");
	}

	// ==================================================================================================== final boss
	private static final String SOVEREIGN = "@e[type=boss_mode:void_sovereign,limit=1]";

	private static void testSovereign(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(settings -> {
			settings.setSeed("26030");
			settings.setGenerateStructures(true);
			settings.setAllowCommands(true);
			settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
		}).create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:send_command_feedback false");
			context.runOnClient(mc -> {
				checkModel("void_sovereign", 15, 3914, 27);
				checkModel("void_pylon", 3, 0, 1);
			});

			// ---- the Void Sanctum generates in the world
			BlockPos located = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Holder<Structure> sanctum = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.getOrThrow(ResourceKey.create(Registries.STRUCTURE, BossMode.id("void_sanctum")));
				Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(sanctum), player(s).blockPosition(), 150, false);
				check(found != null, "Void Sanctum generates in the world (found like /locate)");
				return found.getFirst();
			});
			BlockPos spawn = server.computeOnServer(s -> player(s).blockPosition());
			LOG.info("[boss_mode-test] nearest Void Sanctum at {} ({} blocks from spawn)", located.toShortString(),
					(int) Math.sqrt(located.distToLowCornerSqr(spawn.getX(), located.getY(), spawn.getZ())));
			server.runCommand(fmt("tp @a %d 200 %d", located.getX(), located.getZ()));
			server.waitFor(s -> findAltar(s.overworld(), located, ModBlocks.VOID_ALTAR) != null, 1200);
			BlockPos altarPos = server.computeOnServer(s -> findAltar(s.overworld(), located, ModBlocks.VOID_ALTAR));
			Direction facing = server.computeOnServer(s -> s.overworld().getBlockState(altarPos).getValue(VoidAltarBlock.FACING));
			Vec3 summon = server.computeOnServer(s -> VoidAltarBlock.summonPosition(s.overworld(), altarPos, s.overworld().getBlockState(altarPos)));
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				check(VoidAltarBlock.trophies(level.getBlockState(altarPos)) == 0, "generated Void Altar " + altarPos.toShortString() + " is dormant");
				check(BlockPos.betweenClosedStream(new AABB(altarPos).inflate(4)).anyMatch(p -> level.getBlockState(p).is(Blocks.CHEST)), "sanctum chest beside the altar");
			});
			Vec3 front = Vec3.atLowerCornerOf(facing.getUnitVec3i());
			Vec3 side = Vec3.atLowerCornerOf(facing.getClockWise().getUnitVec3i());
			Vec3 altarTop = Vec3.atBottomCenterOf(altarPos).add(0, 1.0, 0);
			Vec3 overview = summon.add(front.scale(12)).add(side.scale(8)).add(0, 12, 0);
			fly(server);
			look(server, overview, altarTop.add(front.scale(5)));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, "20_void_sanctum");
			look(server, Vec3.atBottomCenterOf(altarPos).add(front.scale(2.4)).add(side.scale(1.4)).add(0, 1.62, 0), altarTop);
			context.waitTicks(5);
			shot(context, "21_void_altar");

			// ---- one trophy of each raid boss calls the Void Sovereign
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_SHARD, 12));
				offer(s, altarPos, true);
				check(VoidAltarBlock.trophies(s.overworld().getBlockState(altarPos)) == 0 && p.getMainHandItem().getCount() == 12,
						"the Void Altar takes no Wyrm Shards");
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_HEART, 3));
				offer(s, altarPos, true);
				check(s.overworld().getBlockState(altarPos).getValue(VoidAltarBlock.HEART) && p.getMainHandItem().getCount() == 2,
						"one Wyrm Heart set (sneaking sets only one)");
				offer(s, altarPos, false);
				check(VoidAltarBlock.trophies(s.overworld().getBlockState(altarPos)) == 1 && p.getMainHandItem().getCount() == 2,
						"a second Wyrm Heart is refused - three hearts no longer call the Sovereign");
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TEMPEST_EYE));
				offer(s, altarPos, false);
				check(s.overworld().getBlockState(altarPos).getValue(VoidAltarBlock.EYE) && p.getMainHandItem().isEmpty(), "Tempest Eye set");
				check(s.overworld().getBlockState(altarPos).getValue(VoidAltarBlock.RITUAL) == 0, "two trophies are not enough");
			});
			look(server, Vec3.atBottomCenterOf(altarPos).add(front.scale(2.4)).add(side.scale(1.4)).add(0, 1.62, 0), altarTop);
			context.waitTicks(50); // let the offering particles settle
			shot(context, "21_void_altar_two_trophies");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BEHEMOTH_CORE));
				offer(s, altarPos, false);
				BlockState state = s.overworld().getBlockState(altarPos);
				check(state.getValue(VoidAltarBlock.CORE) && VoidAltarBlock.trophies(state) == VoidAltarBlock.COST
						&& state.getValue(VoidAltarBlock.RITUAL) == 1, "heart, eye and core start the ritual");
				check(p.getMainHandItem().isEmpty(), "trophies consumed");
			});
			context.waitTicks(10);
			shot(context, "22_void_altar_trophies");
			fly(server);
			look(server, overview, altarTop.add(front.scale(5)));
			context.waitTicks(SovereignConfig.ALTAR_RITUAL_STEPS * SovereignConfig.ALTAR_RITUAL_STEP_TICKS / 2);
			shot(context, "23_void_ritual");
			server.waitFor(s -> findSovereign(s) != null, SovereignConfig.ALTAR_RITUAL_STEPS * SovereignConfig.ALTAR_RITUAL_STEP_TICKS + 40);
			server.runOnServer(s -> {
				VoidSovereignEntity v = requireSovereign(s);
				check(Math.abs(v.getX() - summon.x) < 1.5 && Math.abs(v.getZ() - summon.z) < 1.5, "Void Sovereign rose in front of the altar");
				check(v.getBossState() == BossState.SPAWNING, "Sovereign plays its intro");
				check(v.getMaxHealth() == 100.0F && v.getHealth() == 100.0F && v.getPhase() == SovereignPhase.ONE, "phase 1 of 10, 100 HP pool (" + v.getHealth() + "/" + v.getMaxHealth() + ")");
				check(VoidAltarBlock.trophies(s.overworld().getBlockState(altarPos)) == 0, "altar spent");
			});
			server.waitFor(s -> !ArenaTerraformer.isBusy(s.overworld()), 1200);
			int floorY = ShardAltarBlock.arenaFloor(altarPos, summon);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				checkArena(level, altarPos, floorY, "void", SovereignConfig.ARENA_RADIUS, ModBlocks.VOID_ALTAR);
				check(level.getBiome(altarPos.relative(facing.getClockWise(), 30)).is(ArenaTerraformer.VOID_EXPANSE), "arena biome is now Void Expanse");
			});
			server.waitFor(s -> requireSovereign(s).isIntroFinished(), 120);
			Vec3 arenaView = summon.add(front.scale(24)).add(side.scale(20)).add(0, 16, 0);
			look(server, arenaView, summon.add(0, 4, 0));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, "24_void_sovereign");

			// ---- every pattern
			buffPlayer(server);
			server.runCommand("gamemode survival @a");
			Vec3 ground = new Vec3(summon.x, floorY + 1, summon.z).add(front.scale(14));
			server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", ground.x, ground.y, ground.z, summon.x, summon.y + 3, summon.z));
			server.waitFor(s -> requireSovereign(s).getTarget() != null, 200);
			List<String> ids = server.computeOnServer(s -> requireSovereign(s).getActionController().actionIds()).stream()
					.filter(id -> !VoidSovereignEntity.FORCED_ACTIONS.contains(id)).toList();
			check(ids.size() == 20, "20 Void Sovereign patterns (+ the Void Shield)");
			for (String id : ids) {
				server.runOnServer(s -> {
					requireSovereign(s).setHealth(requireSovereign(s).getMaxHealth());
					player(s).setHealth(player(s).getMaxHealth());
				});
				Vec3 at = server.computeOnServer(s -> requireSovereign(s).position());
				server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", at.x + front.x * 12, floorY + 1.0, at.z + front.z * 12, at.x, at.y + 3, at.z));
				context.waitTicks(3);
				server.runOnServer(s -> check(requireSovereign(s).getActionController().forceAction(s.overworld(), id), "Sovereign started " + id));
				server.waitFor(s -> {
					BossState st = requireSovereign(s).getBossState();
					return st == BossState.SPECIAL_EXECUTION || st == BossState.ATTACKING;
				}, 160);
				context.waitTicks(switch (id) {
					case "black_hole", "eclipse", "soul_drain", "gravity_well", "soul_chains", "null_sweep", "abyssal_spears", "dimension_rend" -> 30;
					case "oblivion" -> 5;
					case "ground_pound", "tentacle_field" -> 8;
					default -> 2;
				});
				lookAtSovereign(server);
				shot(context, "25_" + id);
				if (id.equals("void_orbs")) {
					server.runOnServer(s -> check(!s.overworld().getEntities(ModEntities.VOID_ORB, e -> true).isEmpty(), "void orbs launched"));
				}
				server.waitFor(s -> !id.equals(requireSovereign(s).getActionController().currentActionId()), 220);
				server.runOnServer(s -> check(!id.equals(requireSovereign(s).getActionController().currentActionId()), id + " finished"));
			}
			clearSovereignMinions(server);

			// ---- the mace still works, but the Sovereign answers aerial smashes
			testMaceCounters(context, server, front, floorY);
			server.runCommand("gamemode creative @a");
			clearSovereignMinions(server);

			// ---- phases 2..10, the Void Shield and its Sovereign Shards
			for (int n = 2; n <= SovereignPhase.MAX; n++) {
				SovereignPhase phase = SovereignPhase.byNumber(n);
				server.runOnServer(s -> {
					requireSovereign(s).setHealth(3.0F);
					requireSovereign(s).damageCooldownTime = 0;
				});
				server.runCommand("damage " + SOVEREIGN + " 200 minecraft:player_attack by @p");
				server.waitFor(s -> requireSovereign(s).getBossState() == BossState.PHASE_TRANSITION, 60);
				server.waitFor(s -> requireSovereign(s).getBossState() != BossState.PHASE_TRANSITION, 120);
				final int number = n;
				server.runOnServer(s -> {
					VoidSovereignEntity v = requireSovereign(s);
					check(v.getPhase().number() == number, "Sovereign reached phase " + number);
					check(Math.abs(v.getScale() - phase.scale()) < 0.01F, "Sovereign phase " + number + " size x" + phase.scale());
					check(v.getMaxHealth() == (float) VoidSovereignEntity.phasePool(v.getPhase()), "Sovereign phase " + number + " pool is " + VoidSovereignEntity.phasePool(v.getPhase()) + " (" + v.getMaxHealth() + ")");
					check(v.getHealth() == v.getMaxHealth(), "Sovereign phase " + number + " pool refilled");
					int warlords = s.overworld().getEntitiesOfClass(ShardWarlordEntity.class, v.getBoundingBox().inflate(128), w -> w.isSummonedBy(v)).size();
					check(warlords == phase.midBosses(), "Sovereign phase " + number + " mid-bosses: " + warlords);
				});
				if (phase.pylons() > 0) {
					server.waitFor(s -> requireSovereign(s).isShielded(), 120);
					server.runOnServer(s -> check(requireSovereign(s).livingPylons(s.overworld()) == phase.pylons(), "phase " + number + ": Void Shield with "
							+ phase.pylons() + " Sovereign Shards"));
					float before = server.computeOnServer(s -> {
						requireSovereign(s).damageCooldownTime = 0;
						return requireSovereign(s).getHealth();
					});
					server.runCommand("damage " + SOVEREIGN + " 30 minecraft:player_attack by @p");
					server.runOnServer(s -> check(requireSovereign(s).getHealth() == before, "phase " + number + ": the shield turns every blow"));
					Vec3 at = server.computeOnServer(s -> requireSovereign(s).position());
					look(server, at.add(front.scale(30)).add(0, 14, 0), at.add(0, 4, 0));
					context.waitTicks(20);
					shot(context, "26_phase" + number + "_void_shield");
					if (number == 3) {
						// one by one: every break announces how many are left - "1 left" must say 1
						List<UUID> shards = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.VOID_PYLON, Entity::isAlive).stream()
								.map(Entity::getUUID).toList());
						for (int i = 0; i < shards.size(); i++) {
							int left = shards.size() - i - 1;
							server.runCommand("damage " + shards.get(i) + " 100 minecraft:player_attack by @p");
							server.runOnServer(s -> {
								VoidSovereignEntity v = requireSovereign(s);
								check(v.lastShardsLeft() == left && v.livingPylons(s.overworld()) == left,
										"Sovereign Shard broken: announced " + v.lastShardsLeft() + " left (" + left + " really left)");
								check(v.isShielded() == (left > 0), left > 0 ? "the shield holds while shards stand" : "the last shard drops the shield at once");
							});
						}
					} else {
						server.runCommand("kill @e[type=boss_mode:void_pylon]");
					}
					server.waitFor(s -> !requireSovereign(s).isShielded(), 60);
					float open = server.computeOnServer(s -> {
						requireSovereign(s).damageCooldownTime = 0;
						return requireSovereign(s).getHealth();
					});
					server.runCommand("damage " + SOVEREIGN + " 30 minecraft:player_attack by @p");
					server.runOnServer(s -> check(requireSovereign(s).getHealth() < open, "phase " + number + ": shards broken, the shield is gone"));
				}
				clearSovereignMinions(server);
			}
			server.runOnServer(s -> requireSovereign(s).setHealth(requireSovereign(s).getMaxHealth() * 0.3F));
			server.waitFor(s -> requireSovereign(s).isEnraged(), 120);
			server.waitFor(s -> requireSovereign(s).getBossState() != BossState.PHASE_TRANSITION, 120);
			Vec3 finalAt = server.computeOnServer(s -> requireSovereign(s).position());
			look(server, finalAt.add(front.scale(34)).add(side.scale(10)).add(0, 14, 0), finalAt.add(0, 5, 0));
			context.waitTicks(10);
			shot(context, "27_sovereign_phase10_hud");
			server.runOnServer(s -> check(requireSovereign(s).getPhase() == SovereignPhase.TEN && requireSovereign(s).getScale() > 1.49F
					&& requireSovereign(s).isEnraged(), "phase 10, x1.5, enraged at 35 %"));

			// ---- death and loot
			server.runCommand("damage " + SOVEREIGN + " 1 minecraft:player_attack by @p");
			server.runCommand("kill " + SOVEREIGN);
			context.waitTicks(30);
			shot(context, "28_sovereign_death");
			server.waitFor(s -> findSovereign(s) == null, 140);
			server.runOnServer(s -> {
				List<ItemEntity> items = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(finalAt, finalAt).inflate(24));
				check(items.stream().anyMatch(i -> i.getItem().is(Items.NETHER_STAR)), "the Sovereign dropped nether stars");
				check(items.stream().anyMatch(i -> i.getItem().is(Items.NETHERITE_INGOT)), "the Sovereign dropped netherite");
				check(s.overworld().getEntities(ModEntities.VOID_PYLON, e -> true).isEmpty(), "no pylons left behind");
				LOG.info("[boss_mode-test] Sovereign drops: {}", items.stream().map(i -> i.getItem().toString()).toList());
			});
		}
	}

	/**
	 * A real mace smash is capped by the Abyssal Carapace (and partly thrown back), a player diving at the Sovereign is
	 * lashed out of the air, Gravity Well takes the fall out of anyone airborne, and Soul Chains pull a runner back.
	 */
	private static void testMaceCounters(ClientGameTestContext context, TestServerContext server, Vec3 front, int floorY) {
		server.runOnServer(s -> {
			VoidSovereignEntity v = requireSovereign(s);
			v.getActionController().cancel(s.overworld());
			v.setHealth(v.getMaxHealth());
			player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
			player(s).setHealth(player(s).getMaxHealth());
		});
		Vec3 at = server.computeOnServer(s -> requireSovereign(s).position());
		double width = server.computeOnServer(s -> (double) requireSovereign(s).getBbWidth());
		double height = server.computeOnServer(s -> (double) requireSovereign(s).getBbHeight());
		Vec3 close = new Vec3(at.x + front.x * (width * 0.5 + 1.5), floorY + 1.0, at.z + front.z * (width * 0.5 + 1.5));
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", close.x, close.y, close.z, at.x, at.y + 3, at.z));
		server.waitFor(s -> requireSovereign(s).isMaceThreat(), 40);
		server.runOnServer(s -> check(requireSovereign(s).isMaceThreat(), "the Sovereign senses the mace"));

		// the same 30-block smash, into the open Sovereign and into the Abyssal Carapace
		context.waitTicks(25);
		float plain = server.computeOnServer(s -> smash(s, 30.0));
		server.runOnServer(s -> requireSovereign(s).getActionController().forceAction(s.overworld(), "abyssal_carapace"));
		server.waitFor(s -> requireSovereign(s).isCarapaced(), 40);
		context.waitTicks(25);
		float playerBefore = server.computeOnServer(s -> player(s).getHealth());
		float capped = server.computeOnServer(s -> smash(s, 30.0));
		server.runOnServer(s -> {
			check(plain > 20.0F, fmt("a 30-block mace smash on the open Sovereign takes %.1f HP", plain));
			check(capped < plain * 0.3F, fmt("the Abyssal Carapace caps the same smash to %.1f HP", capped));
			check(player(s).getHealth() < playerBefore, fmt("the carapace throws part of it back (%.1f -> %.1f HP)", playerBefore, player(s).getHealth()));
		});
		lookAtSovereign(server);
		shot(context, "25b_abyssal_carapace_mace");
		server.waitFor(s -> requireSovereign(s).getActionController().currentActionId() == null, 160);

		// diving at it from above: Skyward Lash on its own
		server.runOnServer(s -> requireSovereign(s).getActionController().cancel(s.overworld()));
		Vec3 above = close.add(0, height + 6.0, 0);
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", above.x, above.y, above.z, at.x, at.y, at.z));
		server.waitFor(s -> "skyward_lash".equals(requireSovereign(s).getActionController().currentActionId()), 60);
		server.runOnServer(s -> check(true, "a player diving at it triggers Skyward Lash by itself"));
		server.waitFor(s -> requireSovereign(s).getBossState() == BossState.SPECIAL_RECOVERY, 30);
		context.waitTicks(4);
		shot(context, "25c_skyward_lash_counter");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			double d = Math.hypot(p.getX() - at.x, p.getZ() - at.z);
			check(d > width * 0.5 + 3.0, fmt("the diver is flung aside (%.1f blocks out)", d));
		});
		server.waitFor(s -> requireSovereign(s).getActionController().currentActionId() == null, 60);

		// Gravity Well: nothing airborne keeps its fall
		server.runCommand(fmt("tp @a %.2f %.2f %.2f", close.x, close.y, close.z));
		context.waitTicks(5);
		server.runOnServer(s -> requireSovereign(s).getActionController().forceAction(s.overworld(), "gravity_well"));
		server.waitFor(s -> requireSovereign(s).getBossState() == BossState.SPECIAL_EXECUTION, 40);
		Vec3 high = close.add(front.scale(3)).add(0, 12.0, 0);
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", high.x, high.y, high.z, at.x, at.y, at.z));
		context.waitTicks(4);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			check(!p.onGround() && p.fallDistance < 1.5 && p.getDeltaMovement().y < -0.9,
					fmt("Gravity Well drags the airborne player down with no fall built up (fall %.2f, vy %.2f)", p.fallDistance, p.getDeltaMovement().y));
		});
		shot(context, "25d_gravity_well");
		server.waitFor(s -> requireSovereign(s).getActionController().currentActionId() == null, 200);

		// Soul Chains: a runner (or a pearl) is pulled back to where it stood
		server.runCommand(fmt("tp @a %.2f %.2f %.2f", close.x + front.x * 8, floorY + 1.0, close.z + front.z * 8));
		context.waitTicks(5);
		Vec3 anchor = server.computeOnServer(s -> player(s).position());
		server.runOnServer(s -> requireSovereign(s).getActionController().forceAction(s.overworld(), "soul_chains"));
		server.waitFor(s -> requireSovereign(s).getBossState() == BossState.SPECIAL_EXECUTION, 40);
		context.waitTicks(2);
		server.runCommand(fmt("tp @a %.2f %.2f %.2f", anchor.x + 20, anchor.y, anchor.z));
		context.waitTicks(3);
		server.runOnServer(s -> check(player(s).position().distanceTo(anchor) < 3.0,
				fmt("Soul Chains drag a far runner back to its spot (%.1f blocks off)", player(s).position().distanceTo(anchor))));
		shot(context, "25e_soul_chains");
		server.waitFor(s -> requireSovereign(s).getActionController().currentActionId() == null, 200);
		server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
	}

	/** A real mace attack by the player after a {@code fall}-block fall; returns the health the Sovereign lost. */
	private static float smash(MinecraftServer s, double fall) {
		VoidSovereignEntity v = requireSovereign(s);
		ServerPlayer p = player(s);
		v.setHealth(v.getMaxHealth());
		v.damageCooldownTime = 0;
		float before = v.getHealth();
		p.fallDistance = fall;
		p.attack(v);
		return before - v.getHealth();
	}

	private static @Nullable VoidSovereignEntity findSovereign(MinecraftServer server) {
		List<? extends VoidSovereignEntity> all = server.overworld().getEntities(ModEntities.VOID_SOVEREIGN, e -> !e.isRemoved());
		return all.isEmpty() ? null : all.getFirst();
	}

	private static VoidSovereignEntity requireSovereign(MinecraftServer server) {
		VoidSovereignEntity sovereign = findSovereign(server);
		if (sovereign == null) {
			throw new AssertionError("[boss_mode-test] FAILED: Void Sovereign exists");
		}
		return sovereign;
	}

	private static void lookAtSovereign(TestServerContext server) {
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ facing entity " + SOVEREIGN + " feet");
	}

	private static void clearSovereignMinions(TestServerContext server) {
		server.runOnServer(s -> {
			AABB area = new AABB(player(s).position(), player(s).position()).inflate(200);
			List<Entity> doomed = new ArrayList<>();
			doomed.addAll(s.overworld().getEntitiesOfClass(ShardHoundEntity.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(ShardWarlordEntity.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(Vex.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(VoidOrbProjectile.class, area));
			doomed.forEach(Entity::discard);
		});
	}

	/** A right-click on the altar's top with whatever the player holds, going through the real block interaction. */
	static void offer(MinecraftServer s, BlockPos altar, boolean sneaking) {
		ServerPlayer p = player(s);
		p.setShiftKeyDown(sneaking);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(altar).add(0, 0.4, 0), Direction.UP, altar, false);
		s.overworld().getBlockState(altar).useItemOn(p.getMainHandItem(), s.overworld(), p, InteractionHand.MAIN_HAND, hit);
		p.setShiftKeyDown(false);
	}

	static @Nullable BlockPos findAltar(ServerLevel level, BlockPos around) {
		return findAltar(level, around, ModBlocks.SHARD_ALTAR);
	}

	static @Nullable BlockPos findAltar(ServerLevel level, BlockPos around, Block block) {
		for (int dx = -48; dx <= 48; dx++) {
			for (int dz = -48; dz <= 48; dz++) {
				int x = around.getX() + dx, z = around.getZ() + dz;
				if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
					return null;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				for (int y = top + 2; y > top - 16; y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (level.getBlockState(p).is(block)) {
						return p;
					}
				}
			}
		}
		return null;
	}

	// ==================================================================================================== phases
	/** Empties the current phase pool with a player-attributed melee hit. */
	private static void breakPhase(TestServerContext server) {
		server.runOnServer(s -> {
			requireBoss(s).setHealth(3.0F);
			requireBoss(s).damageCooldownTime = 0;
		});
		server.runCommand("damage " + BOSS + " 200 minecraft:player_attack by @p");
	}

	private static void checkEntryWave(MinecraftServer s, ShardDragonEntity d, BossPhase phase) {
		AABB area = d.getBoundingBox().inflate(96);
		List<ShardHoundEntity> hounds = s.overworld().getEntitiesOfClass(ShardHoundEntity.class, area, h -> h.isSummonedBy(d));
		List<Vex> wraiths = s.overworld().getEntitiesOfClass(Vex.class, area, v -> v.getOwner() == d);
		Set<MinionType> seen = new HashSet<>();
		hounds.forEach(h -> seen.add(switch (h.getVariant()) {
			case HOUND -> MinionType.HOUND;
			case SPITTER -> MinionType.SPITTER;
			case VOLATILE -> MinionType.VOLATILE;
		}));
		if (!wraiths.isEmpty()) {
			seen.add(MinionType.WRAITH);
		}
		check(phase.minions().containsAll(seen), "phase " + phase.number() + " minions " + seen + " within roster " + phase.minions());
		check(hounds.size() + wraiths.size() == phase.entryWave(), "phase " + phase.number() + " entry wave of " + phase.entryWave());
		int warlords = s.overworld().getEntitiesOfClass(ShardWarlordEntity.class, area, w -> w.isSummonedBy(d)).size();
		check(warlords == Math.min(phase.midBosses(), BossConfig.MAX_ACTIVE_WARLORDS), "phase " + phase.number() + " mid-bosses: " + warlords);
	}

	/** Every sealed damage type bounces off, every open one still hurts. */
	private static void checkSealed(TestServerContext server, BossPhase phase) {
		String[][] probes = {{"projectile", "minecraft:arrow by @p"}, {"explosion", "minecraft:explosion by @p"}, {"environment", "minecraft:cactus"}};
		for (String[] probe : probes) {
			com.bossmode.entity.DamageKind kind = com.bossmode.entity.DamageKind.valueOf(probe[0].toUpperCase(Locale.ROOT));
			float before = server.computeOnServer(s -> {
				requireBoss(s).damageCooldownTime = 0;
				return requireBoss(s).getHealth();
			});
			boolean flying = server.computeOnServer(s -> requireBoss(s).isFlying());
			server.runCommand("damage " + BOSS + " 20 " + probe[1]);
			float after = server.computeOnServer(s -> requireBoss(s).getHealth());
			boolean open = phase.allowedDamage().contains(kind) || (kind == DamageKind.PROJECTILE && flying);
			check(open == (after < before), "phase " + phase.number() + ": " + probe[0] + (open ? " still hurts" : " is sealed"));
			server.runOnServer(s -> requireBoss(s).setHealth(requireBoss(s).getMaxHealth()));
		}
		float before = server.computeOnServer(s -> {
			requireBoss(s).damageCooldownTime = 0;
			return requireBoss(s).getHealth();
		});
		server.runCommand("damage " + BOSS + " 20 minecraft:player_attack by @p");
		server.runOnServer(s -> {
			check(requireBoss(s).getHealth() < before, "phase " + phase.number() + ": melee hurts at any time (" + requireBoss(s).getBossState() + ")");
			requireBoss(s).setHealth(requireBoss(s).getMaxHealth());
		});
	}

	// ==================================================================================================== flight
	/**
	 * Phase 9 (melee only on the ground): takeoff to cruising height, air-only pattern pool, arrows while it flies,
	 * shoot-down into an EXPOSED crash, landing, a meteor dive out of the air and the automatic air / ground cycle.
	 */
	private static void testFlight(ClientGameTestContext context, TestServerContext server) {
		buffPlayer(server);
		server.runCommand("gamemode survival @a");
		server.waitFor(s -> requireBoss(s).getTarget() != null, 200);
		takeOff(server);
		context.waitTicks(5);
		server.runOnServer(s -> {
			ShardDragonEntity d = requireBoss(s);
			double height = d.getY() - d.groundBelow(s.overworld());
			check(d.isFlying() && d.isNoGravity() && height >= BossConfig.FLY_ALTITUDE - 3,
					"phase 9: flying " + fmt("%.1f", height) + " blocks above the ground (out of melee reach)");
			check(!d.isActionAllowed("bite") && !d.isActionAllowed("tail_sweep") && !d.isActionAllowed("shatter_slam"), "ground patterns locked while airborne");
			check(d.isActionAllowed("prism_beam") && d.isActionAllowed("cataclysm") && d.isActionAllowed("summon"), "phase 9 air patterns available");
			check((d.barAllowedDamage() & DamageKind.PROJECTILE.bit()) != 0, "boss bar lights up projectiles while it flies");
		});
		lookAtBoss(server);
		context.waitTicks(3);
		shot(context, "05a_phase9_flying");

		// arrows pierce it in the air even though phase 9 only accepts melee on the ground
		float before = server.computeOnServer(s -> {
			requireBoss(s).damageCooldownTime = 0;
			return requireBoss(s).getHealth();
		});
		server.runCommand("damage " + BOSS + " 20 minecraft:arrow by @p");
		server.runOnServer(s -> check(requireBoss(s).getHealth() < before, "phase 9: arrows hurt it while it flies"));
		int shots = 1;
		while (shots < 12 && !server.computeOnServer(s -> "crash".equals(requireBoss(s).getActionController().currentActionId()))) {
			server.runOnServer(s -> requireBoss(s).damageCooldownTime = 0);
			server.runCommand("damage " + BOSS + " 30 minecraft:arrow by @p");
			shots++;
		}
		final int volleys = shots;
		server.runOnServer(s -> {
			ShardDragonEntity d = requireBoss(s);
			check("crash".equals(d.getActionController().currentActionId()) && !d.isFlying(),
					"shot down after " + volleys + " arrow hits (" + fmt("%.0f", d.getMaxHealth() - d.getHealth()) + " damage in the air)");
		});
		server.waitFor(s -> requireBoss(s).onGround() && stateOf(s) == BossState.SPECIAL_RECOVERY, 80);
		lookAtBoss(server);
		context.waitTicks(4);
		shot(context, "05b_phase9_shot_down");
		float grounded = server.computeOnServer(s -> {
			check(requireBoss(s).isExposed(), "shot-down dragon lies EXPOSED");
			requireBoss(s).damageCooldownTime = 0;
			return requireBoss(s).getHealth();
		});
		server.runCommand("damage " + BOSS + " 20 minecraft:player_attack by @p");
		server.runOnServer(s -> check(requireBoss(s).getHealth() < grounded, "melee lands on the crashed dragon"));
		server.waitFor(s -> !"crash".equals(requireBoss(s).getActionController().currentActionId()), 100);
		float landed = server.computeOnServer(s -> {
			requireBoss(s).damageCooldownTime = 0;
			return requireBoss(s).getHealth();
		});
		server.runCommand("damage " + BOSS + " 20 minecraft:arrow by @p");
		server.runOnServer(s -> check(requireBoss(s).getHealth() == landed, "phase 9: arrows bounce off again once it is grounded"));

		// a plain landing
		takeOff(server);
		server.runOnServer(s -> check(requireBoss(s).getActionController().forceAction(s.overworld(), "land"), "landing forced"));
		server.waitFor(s -> !requireBoss(s).isFlying() && requireBoss(s).onGround(), 80);
		server.runOnServer(s -> check(!requireBoss(s).isNoGravity(), "landing brought it back to the ground"));

		// a meteor dive straight out of the sky ends the flight too
		takeOff(server);
		server.runOnServer(s -> check(requireBoss(s).getActionController().forceAction(s.overworld(), "meteor_dive"), "meteor dive from the air"));
		server.waitFor(s -> !"meteor_dive".equals(requireBoss(s).getActionController().currentActionId()), 120);
		server.runOnServer(s -> check(!requireBoss(s).isFlying() && !requireBoss(s).noPhysics, "the dive ended the flight on the ground"));

		// left alone, the dragon keeps cycling: takes off after its ground time, comes down after its air time
		BossPhase phase = server.computeOnServer(s -> requireBoss(s).getPhase());
		server.runOnServer(s -> requireBoss(s).setHealth(requireBoss(s).getMaxHealth()));
		server.waitFor(s -> requireBoss(s).isFlying(), phase.groundTicks() + 200);
		server.runOnServer(s -> check(true, "took off by itself after its ground time"));
		server.waitFor(s -> !requireBoss(s).isFlying() && requireBoss(s).onGround(), phase.airTicks() + 200);
		server.runOnServer(s -> check(true, "came back down by itself after its air time"));

		server.runCommand("gamemode creative @a");
		server.waitFor(s -> !requireBoss(s).getActionController().isActing(), 200);
		clearMinions(server);
		server.runOnServer(s -> requireBoss(s).setHealth(requireBoss(s).getMaxHealth()));
	}

	/**
	 * Starts a fresh flight (full air time ahead). With a target, phases 6-10 may already be airborne on their own, so an
	 * ongoing flight is ended with a landing first.
	 */
	private static void takeOff(TestServerContext server) {
		server.waitFor(s -> !requireBoss(s).getActionController().isActing(), 300);
		if (server.computeOnServer(s -> requireBoss(s).isFlying())) {
			server.runOnServer(s -> requireBoss(s).getActionController().forceAction(s.overworld(), "land"));
			server.waitFor(s -> !requireBoss(s).isFlying() && requireBoss(s).onGround() && !requireBoss(s).getActionController().isActing(), 120);
		}
		server.runOnServer(s -> check(requireBoss(s).getActionController().forceAction(s.overworld(), "takeoff"), "takeoff"));
		server.waitFor(s -> requireBoss(s).isFlying() && !"takeoff".equals(requireBoss(s).getActionController().currentActionId()), 80);
	}

	private static void lookAtBoss(TestServerContext server) {
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ facing entity " + BOSS + " feet");
	}

	// ==================================================================================================== minions / warlord
	private static void testMinions(ClientGameTestContext context, TestServerContext server) {
		buffPlayer(server);
		server.runCommand("gamemode survival @a");
		Vec3 p = server.computeOnServer(s -> player(s).position());
		Vec3 base = p.add(100, 0, 0); // outside the dragon's 64-block follow range
		server.runCommand(fmt("tp @a %.2f %.2f %.2f", base.x, base.y, base.z));
		context.waitTicks(20);
		server.runCommand(fmt("summon boss_mode:shard_hound %.2f %.2f %.2f", base.x + 4, base.y, base.z));
		server.runCommand(fmt("summon boss_mode:shard_spitter %.2f %.2f %.2f", base.x + 11, base.y, base.z + 2));
		server.runCommand(fmt("summon boss_mode:volatile_hound %.2f %.2f %.2f", base.x - 5, base.y, base.z));
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", base.x, base.y, base.z + 7, base.x + 2, base.y + 0.5, base.z));
		context.waitTicks(28);
		shot(context, "07_minions");
		server.waitFor(s -> s.overworld().getEntities(ModEntities.VOLATILE_HOUND, e -> true).isEmpty(), 200);
		server.runOnServer(s -> check(true, "volatile hound detonated"));
		server.waitFor(s -> s.overworld().getEntities(ModEntities.WYRM_SHARD, e -> e.getOwner() instanceof ShardHoundEntity).size() > 0, 200);
		server.runOnServer(s -> check(true, "spitter fired a shard"));
		server.waitFor(s -> player(s).getLastHurtByMob() instanceof ShardHoundEntity h && h.getVariant() == ShardHoundEntity.Variant.HOUND, 300);
		server.runOnServer(s -> check(player(s).getLastHurtByMob() instanceof ShardHoundEntity, "hound bit the player"));
		server.runCommand("kill @e[type=boss_mode:shard_hound]");
		server.runCommand("kill @e[type=boss_mode:shard_spitter]");
	}

	private static void testWarlord(ClientGameTestContext context, TestServerContext server) {
		Vec3 p = server.computeOnServer(s -> player(s).position());
		Vec3 at = p.add(0, 0, -10);
		server.runCommand(fmt("summon boss_mode:shard_warlord %.2f %.2f %.2f", at.x, at.y, at.z));
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", p.x + 4, p.y, p.z, at.x, at.y + 2, at.z));
		context.waitTicks(20);
		shot(context, "08_warlord_spawn");
		server.waitFor(s -> warlord(s) != null && warlord(s).getBossState() != BossState.SPAWNING, 80);
		server.waitFor(s -> warlord(s).getTarget() != null, 200);
		for (String id : List.of("staff_swing", "shard_bolt", "ground_spikes", "warcry", "blink")) {
			server.runOnServer(s -> check(warlord(s).getActionController().forceAction(s.overworld(), id), "warlord started " + id));
			server.waitFor(s -> {
				BossState st = warlord(s).getBossState();
				return st == BossState.SPECIAL_EXECUTION || st == BossState.ATTACKING;
			}, 60);
			context.waitTicks(2);
			shot(context, "08_warlord_" + id);
			server.waitFor(s -> !id.equals(warlord(s).getActionController().currentActionId()), 80);
		}
		server.runOnServer(s -> {
			ShardWarlordEntity w = warlord(s);
			check(w.distanceTo(player(s)) < 8.0, "blink put the warlord next to its target");
			check(!s.overworld().getEntitiesOfClass(ShardHoundEntity.class, w.getBoundingBox().inflate(16), h -> h.isSummonedBy(w)).isEmpty(), "war cry called hounds");
		});
		server.runCommand("kill @e[type=boss_mode:shard_warlord]");
		server.waitFor(s -> warlord(s) == null, 80);
		server.runCommand("kill @e[type=boss_mode:shard_hound]");
		server.runCommand("kill @e[type=boss_mode:shard_spitter]");
		server.runCommand("gamemode creative @a");
	}

	// ==================================================================================================== helpers
	private static void checkAssets(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			checkModel("shard_dragon", 14, 5202, 24);
			checkModel("shard_hound", 7, 3386, 9);
			checkModel("shard_warlord", 9, 4005, 10);
		});
	}

	static void checkModel(String name, int bones, int triangles, int clips) {
		BakedGeoModel model = GeckoLibResources.getBakedModels().getModel(BossMode.id("entity/" + name));
		check(model != null && !model.isMissingno(), name + ".geo.json baked by GeckoLib");
		int[] stats = new int[2];
		countBones(model.topLevelBones(), stats);
		check(stats[0] == bones && stats[1] == triangles, name + ": " + stats[0] + " bones / " + stats[1] + " Blender triangles");
		BakedAnimations animations = GeckoLibResources.getBakedAnimations().cache().get(BossMode.id("entity/" + name));
		check(animations != null && animations.animations().size() == clips, name + ": " + clips + " animation clips");
	}

	private static void countBones(GeoBone[] bones, int[] stats) {
		for (GeoBone bone : bones) {
			stats[0]++;
			if (bone instanceof PolyMeshGeoBone mesh) {
				stats[1] += mesh.triangleCount();
			}
			countBones(bone.children(), stats);
		}
	}

	static void buffPlayer(TestServerContext server) {
		server.runCommand("effect give @a minecraft:resistance infinite 3 true");
		server.runCommand("effect give @a minecraft:regeneration infinite 5 true");
		server.runCommand("effect give @a minecraft:saturation infinite 5 true");
		server.runCommand("effect give @a minecraft:health_boost infinite 9 true");
		server.runCommand("effect give @a minecraft:instant_health 1 20 true");
	}

	private static void clearMinions(TestServerContext server) {
		server.runOnServer(s -> {
			List<Entity> doomed = new ArrayList<>();
			AABB area = new AABB(player(s).position(), player(s).position()).inflate(200);
			doomed.addAll(s.overworld().getEntitiesOfClass(ShardHoundEntity.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(ShardWarlordEntity.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(Vex.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(WyrmShardProjectile.class, area));
			doomed.forEach(Entity::discard);
		});
	}

	/** Moves the player so the dragon at {@code at} is in frame. */
	private static void view(TestServerContext server, Vec3 at, double distance, double height) {
		server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", at.x + distance * 0.55, at.y + height, at.z + distance * 0.85, at.x, at.y + height * 0.4, at.z));
	}

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static @Nullable ShardDragonEntity findBoss(MinecraftServer server) {
		List<? extends ShardDragonEntity> all = server.overworld().getEntities(ModEntities.SHARD_DRAGON, e -> !e.isRemoved());
		return all.isEmpty() ? null : all.getFirst();
	}

	private static @Nullable ShardWarlordEntity warlord(MinecraftServer server) {
		List<? extends ShardWarlordEntity> all = server.overworld().getEntities(ModEntities.SHARD_WARLORD, e -> !e.isRemoved() && e.isAlive());
		return all.isEmpty() ? null : all.getFirst();
	}

	private static ShardDragonEntity requireBoss(MinecraftServer server) {
		ShardDragonEntity dragon = findBoss(server);
		check(dragon != null, "Shard Dragon exists");
		return dragon;
	}

	private static @Nullable BossState stateOf(MinecraftServer server) {
		ShardDragonEntity dragon = findBoss(server);
		return dragon == null ? null : dragon.getBossState();
	}

	private static void assertState(TestServerContext server, BossState expected, String message) {
		server.runOnServer(s -> check(stateOf(s) == expected, message + " (was " + stateOf(s) + ")"));
	}

	static void shot(ClientGameTestContext context, String name) {
		LOG.info("[boss_mode-test] screenshot {} -> {}", name, context.takeScreenshot("shard_" + name));
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("[boss_mode-test] FAILED: " + message);
		}
		if (!message.equals("Shard Dragon exists")) {
			LOG.info("[boss_mode-test] ok: {}", message);
		}
	}

	static String fmt(String format, Object... args) {
		return String.format(Locale.ROOT, format, args);
	}
}
