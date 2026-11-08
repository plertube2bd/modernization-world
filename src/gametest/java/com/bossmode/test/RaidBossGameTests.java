package com.bossmode.test;

import com.bossmode.BossMode;
import com.bossmode.block.ShardAltarBlock;
import com.bossmode.config.BastionConfig;
import com.bossmode.config.BossConfig;
import com.bossmode.config.TempestConfig;
import com.bossmode.entity.bastion.BastionBehemothEntity;
import com.bossmode.entity.boss.BossState;
import com.bossmode.entity.minion.ShardHoundEntity;
import com.bossmode.entity.projectile.BoulderProjectile;
import com.bossmode.entity.projectile.WyrmShardProjectile;
import com.bossmode.entity.raid.RaidBossEntity;
import com.bossmode.entity.raid.RaidPhase;
import com.bossmode.entity.tempest.TempestWyvernEntity;
import com.bossmode.entity.warlord.ShardWarlordEntity;
import com.bossmode.registry.ModBlocks;
import com.bossmode.registry.ModEntities;
import com.bossmode.registry.ModItems;
import com.bossmode.world.ArenaTerraformer;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.bossmode.test.ShardDragonClientGameTest.buffPlayer;
import static com.bossmode.test.ShardDragonClientGameTest.check;
import static com.bossmode.test.ShardDragonClientGameTest.checkArena;
import static com.bossmode.test.ShardDragonClientGameTest.checkModel;
import static com.bossmode.test.ShardDragonClientGameTest.findAltar;
import static com.bossmode.test.ShardDragonClientGameTest.fly;
import static com.bossmode.test.ShardDragonClientGameTest.fmt;
import static com.bossmode.test.ShardDragonClientGameTest.look;
import static com.bossmode.test.ShardDragonClientGameTest.offer;
import static com.bossmode.test.ShardDragonClientGameTest.player;
import static com.bossmode.test.ShardDragonClientGameTest.shot;

/**
 * The two raid bosses end to end, each in a normally generated world: its structure generates, twelve Wyrm Shards on
 * its altar call it, the land becomes its themed arena, every pattern runs, its own mechanic works (the Tempest Wyvern's
 * flight / shoot-down / no regeneration in the air, the Bastion Behemoth's frontal plating / Fortify / thorns), every
 * phase breaks into the next with growth and a refilled pool, it enrages, dies and leaves its trophy for the Void Altar.
 */
final class RaidBossGameTests {
	private static final Logger LOG = LoggerFactory.getLogger("boss_mode-test");

	record Kind(String id, String structure, Block altar, EntityType<?> type, int summonDistance, ArenaTerraformer.Theme theme, int arenaRadius,
			ResourceKey<Biome> biome, Item trophy, int bones, int triangles, int clips, int maxPhase, Set<String> forcedOnly, String shots) {
	}

	static final Kind WYVERN = new Kind("tempest_wyvern", "tempest_aerie", ModBlocks.TEMPEST_ALTAR, ModEntities.TEMPEST_WYVERN,
			TempestConfig.ALTAR_SUMMON_DISTANCE, ArenaTerraformer.Theme.STORM, TempestConfig.ARENA_RADIUS, ArenaTerraformer.STORM_REACH,
			ModItems.TEMPEST_EYE, 12, 4116, 21, 5, TempestWyvernEntity.FLIGHT_ACTIONS, "3");
	static final Kind BEHEMOTH = new Kind("bastion_behemoth", "bastion_den", ModBlocks.BASTION_ALTAR, ModEntities.BASTION_BEHEMOTH,
			BastionConfig.ALTAR_SUMMON_DISTANCE, ArenaTerraformer.Theme.EMBER, BastionConfig.ARENA_RADIUS, ArenaTerraformer.EMBER_WASTE,
			ModItems.BEHEMOTH_CORE, 11, 3873, 16, 8, Set.of(), "4");

	private RaidBossGameTests() {
	}

	static void run(ClientGameTestContext context, Kind k) {
		String boss = "@e[type=boss_mode:" + k.id() + ",limit=1]";
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
			context.runOnClient(mc -> checkModel(k.id(), k.bones(), k.triangles(), k.clips()));

			// ---- the structure generates, with a dormant altar
			BlockPos located = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.getOrThrow(ResourceKey.create(Registries.STRUCTURE, BossMode.id(k.structure())));
				Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(structure), player(s).blockPosition(), 150, false);
				check(found != null, k.structure() + " generates in the world (found like /locate)");
				return found.getFirst();
			});
			BlockPos spawn = server.computeOnServer(s -> player(s).blockPosition());
			LOG.info("[boss_mode-test] nearest {} at {} ({} blocks from spawn)", k.structure(), located.toShortString(),
					(int) Math.sqrt(located.distToLowCornerSqr(spawn.getX(), located.getY(), spawn.getZ())));
			server.runCommand(fmt("tp @a %d 250 %d", located.getX(), located.getZ()));
			server.waitFor(s -> findAltar(s.overworld(), located, k.altar()) != null, 1200);
			BlockPos altarPos = server.computeOnServer(s -> findAltar(s.overworld(), located, k.altar()));
			Direction facing = server.computeOnServer(s -> s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.FACING));
			Vec3 summon = server.computeOnServer(s -> ShardAltarBlock.summonPosition(s.overworld(), altarPos, s.overworld().getBlockState(altarPos),
					k.summonDistance()));
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockState state = level.getBlockState(altarPos);
				check(state.getValue(ShardAltarBlock.CHARGE) == 0 && state.getValue(ShardAltarBlock.RITUAL) == 0,
						k.structure() + ": altar " + altarPos.toShortString() + " is dormant, facing " + facing);
				check(BlockPos.betweenClosedStream(new AABB(altarPos).inflate(3)).anyMatch(p -> level.getBlockState(p).is(Blocks.CHEST)),
						k.structure() + ": loot chest beside the altar");
				check(Math.abs(summon.y - altarPos.getY()) <= 2, k.structure() + ": arena floor in front of the altar (summon point " + summon + ")");
			});
			Vec3 front = Vec3.atLowerCornerOf(facing.getUnitVec3i());
			Vec3 side = Vec3.atLowerCornerOf(facing.getClockWise().getUnitVec3i());
			Vec3 altarTop = Vec3.atBottomCenterOf(altarPos).add(0, 0.9, 0);
			Vec3 overview = summon.add(front.scale(11)).add(side.scale(7)).add(0, 11, 0);
			fly(server);
			look(server, overview, altarTop.add(front.scale(4)));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, k.shots() + "0_" + k.structure());
			look(server, Vec3.atBottomCenterOf(altarPos).add(front.scale(2.2)).add(side.scale(1.3)).add(0, 1.62, 0), altarTop);
			context.waitTicks(5);
			shot(context, k.shots() + "1_altar");

			// ---- twelve Wyrm Shards call the boss
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_SHARD, ShardAltarBlock.COST));
				offer(s, altarPos, false);
				check(s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.CHARGE) == 1, k.id() + ": one shard offered");
				offer(s, altarPos, true);
				BlockState state = s.overworld().getBlockState(altarPos);
				check(state.getValue(ShardAltarBlock.CHARGE) == ShardAltarBlock.COST && state.getValue(ShardAltarBlock.RITUAL) == 1,
						k.id() + ": twelve shards start the ritual");
				check(p.getMainHandItem().isEmpty(), k.id() + ": shards consumed");
			});
			fly(server);
			look(server, overview, altarTop.add(front.scale(4)));
			context.waitTicks(BossConfig.ALTAR_RITUAL_STEPS * BossConfig.ALTAR_RITUAL_STEP_TICKS * 3 / 5);
			shot(context, k.shots() + "2_ritual");
			server.waitFor(s -> find(s, k) != null, BossConfig.ALTAR_RITUAL_STEPS * BossConfig.ALTAR_RITUAL_STEP_TICKS + 40);
			server.runOnServer(s -> {
				RaidBossEntity<?> b = require(s, k);
				check(Math.abs(b.getX() - summon.x) < 1.5 && Math.abs(b.getZ() - summon.z) < 1.5, k.id() + " rose in front of its altar");
				check(b.getBossState() == BossState.SPAWNING, k.id() + " plays its intro");
				check(b.getMaxHealth() == 100.0F && b.getHealth() == 100.0F && b.getPhase().number() == 1 && b.maxPhase() == k.maxPhase(),
						k.id() + ": phase 1 of " + k.maxPhase() + ", 100 HP pool (" + b.getHealth() + "/" + b.getMaxHealth() + ")");
				BlockState state = s.overworld().getBlockState(altarPos);
				check(state.getValue(ShardAltarBlock.CHARGE) == 0 && state.getValue(ShardAltarBlock.RITUAL) == 0, k.id() + ": altar spent and reusable");
			});
			context.waitTicks(20);
			shot(context, k.shots() + "3_summoned");

			// ---- its arena
			server.waitFor(s -> !ArenaTerraformer.isBusy(s.overworld()), 2400);
			int floorY = ShardAltarBlock.arenaFloor(altarPos, summon);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				checkArena(level, altarPos, floorY, k.id(), k.arenaRadius(), k.altar(), k.theme());
				check(level.getBiome(altarPos.relative(facing.getClockWise(), 25)).is(k.biome()), k.id() + ": arena biome is now " + k.biome().identifier());
			});
			server.waitFor(s -> require(s, k).isIntroFinished(), 160);
			Vec3 arenaMid = Vec3.atBottomCenterOf(altarPos).add(front.scale(12));
			look(server, arenaMid.add(side.scale(40)).add(front.scale(24)).add(0, 32, 0), arenaMid.add(0, floorY + 1 - arenaMid.y, 0));
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			shot(context, k.shots() + "4_arena");
			server.runOnServer(s -> {
				// a second one is refused while it lives
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WYRM_SHARD, ShardAltarBlock.COST));
				offer(s, altarPos, true);
				check(s.overworld().getBlockState(altarPos).getValue(ShardAltarBlock.CHARGE) == 0 && p.getMainHandItem().getCount() == ShardAltarBlock.COST,
						k.id() + ": final shard refused while one is alive");
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			});
			Vec3 bossView = summon.add(front.scale(20)).add(side.scale(14)).add(0, 9, 0);
			look(server, bossView, summon.add(0, 3, 0));
			context.waitTicks(5);
			shot(context, k.shots() + "5_" + k.id());

			// ---- every pattern
			buffPlayer(server);
			server.runCommand("gamemode survival @a");
			Vec3 ground = new Vec3(summon.x, floorY + 1, summon.z).add(front.scale(14));
			server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", ground.x, ground.y, ground.z, summon.x, summon.y + 3, summon.z));
			server.waitFor(s -> require(s, k).getTarget() != null, 200);
			List<String> ids = server.computeOnServer(s -> require(s, k).getActionController().actionIds()).stream()
					.filter(id -> !k.forcedOnly().contains(id)).toList();
			check(ids.size() == (k == WYVERN ? 11 : 10), k.id() + ": " + ids.size() + " patterns " + ids);
			for (String id : ids) {
				server.runOnServer(s -> {
					require(s, k).setHealth(require(s, k).getMaxHealth());
					player(s).setHealth(player(s).getMaxHealth());
				});
				Vec3 at = server.computeOnServer(s -> require(s, k).position());
				double groundY = server.computeOnServer(s -> (double) com.bossmode.entity.boss.combat.CombatUtil.groundY(s.overworld(), at.x + front.x * 13,
						at.z + front.z * 13));
				server.runCommand(fmt("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", at.x + front.x * 13, groundY, at.z + front.z * 13, at.x, at.y + 2, at.z));
				context.waitTicks(3);
				server.runOnServer(s -> {
					require(s, k).setTarget(player(s));
					check(require(s, k).getActionController().forceAction(s.overworld(), id), k.id() + " started " + id);
				});
				if (id.equals("feather_barrage")) {
					// feathers are fast: catch them in flight instead of after a fixed delay
					server.waitFor(s -> !s.overworld().getEntities(ModEntities.WYRM_SHARD, e -> e.getItem().is(ModItems.STORM_FEATHER)).isEmpty(), 60);
					server.runOnServer(s -> check(true, "storm feathers launched"));
				}
				server.waitFor(s -> {
					BossState st = require(s, k).getBossState();
					return st == BossState.SPECIAL_EXECUTION || st == BossState.ATTACKING || st.name().endsWith("RECOVERY");
				}, 160);
				context.waitTicks(switch (id) {
					case "lightning_breath", "static_field", "fortify", "cyclone", "rolling_charge" -> 12;
					case "earthquake" -> 4;
					default -> 2;
				});
				if (id.equals("fortify")) {
					server.runOnServer(s -> check(((BastionBehemothEntity) require(s, k)).isFortified(), "fortify: the Behemoth is fortified"));
				}
				if (id.equals("boulder_throw")) {
					server.runOnServer(s -> check(!s.overworld().getEntities(ModEntities.BOULDER, e -> true).isEmpty(), "molten boulder thrown"));
				}
				if (id.equals("cyclone")) {
					server.runOnServer(s -> check(((TempestWyvernEntity) require(s, k)).whirlwindCount() > 0, "cyclone: a whirlwind roams the arena"));
				}
				lookAt(server, boss);
				shot(context, k.shots() + "6_" + id);
				server.waitFor(s -> !id.equals(require(s, k).getActionController().currentActionId()), 240);
				server.runOnServer(s -> check(!id.equals(require(s, k).getActionController().currentActionId()), k.id() + ": " + id + " finished"));
				if (id.equals("fortify")) {
					server.runOnServer(s -> check(!((BastionBehemothEntity) require(s, k)).isFortified(), "fortify ends with the action"));
				}
				if (id.equals("cyclone")) {
					// the whirlwinds would drag the camera around for the rest of the patterns
					server.runOnServer(s -> ((TempestWyvernEntity) require(s, k)).clearWhirlwinds());
				}
			}
			clearMinions(server);

			// ---- its own mechanic
			if (k == WYVERN) {
				testFlight(context, server, k, boss);
			} else {
				testDefences(context, server, k, boss);
			}
			server.runCommand("gamemode creative @a");
			clearMinions(server);

			// ---- phases 2..max
			for (int n = 2; n <= k.maxPhase(); n++) {
				server.runOnServer(s -> {
					RaidBossEntity<?> b = require(s, k);
					if (b instanceof TempestWyvernEntity w && w.isFlying()) {
						w.setFlying(false);
					}
					b.setHealth(3.0F);
					b.damageCooldownTime = 0;
				});
				server.runCommand("damage " + boss + " 200 minecraft:player_attack by @p");
				server.waitFor(s -> require(s, k).getBossState() == BossState.PHASE_TRANSITION, 60);
				server.waitFor(s -> require(s, k).getBossState() != BossState.PHASE_TRANSITION, 120);
				final int number = n;
				server.runOnServer(s -> {
					RaidBossEntity<?> b = require(s, k);
					RaidPhase phase = b.phase(number);
					check(b.getPhase().number() == number, k.id() + " reached phase " + number);
					check(Math.abs(b.getScale() - phase.scale()) < 0.01F, k.id() + " phase " + number + " size x" + phase.scale());
					check(b.getMaxHealth() == (float) b.phasePool(number), k.id() + " phase " + number + " pool is " + b.phasePool(number) + " (" + b.getMaxHealth() + ")");
					check(b.getHealth() == b.getMaxHealth(), k.id() + " phase " + number + " pool refilled");
					check(b.countWarlords(s.overworld()) == phase.midBosses(), k.id() + " phase " + number + " Shard Warlords: " + phase.midBosses());
				});
				clearMinions(server);
			}
			server.runOnServer(s -> require(s, k).setHealth(require(s, k).getMaxHealth() * 0.25F));
			server.waitFor(s -> require(s, k).isEnraged(), 120);
			server.waitFor(s -> require(s, k).getBossState() != BossState.PHASE_TRANSITION, 120);
			Vec3 finalAt = server.computeOnServer(s -> require(s, k).position());
			look(server, finalAt.add(front.scale(26)).add(side.scale(10)).add(0, 12, 0), finalAt.add(0, 3, 0));
			context.waitTicks(10);
			shot(context, k.shots() + "7_final_phase_hud");
			server.runOnServer(s -> check(require(s, k).isEnraged() && require(s, k).isLastPhase(), k.id() + ": last phase, enraged"));

			// ---- death and the trophy
			server.runCommand("damage " + boss + " 1 minecraft:player_attack by @p");
			server.runCommand("kill " + boss);
			context.waitTicks(30);
			shot(context, k.shots() + "8_death");
			server.waitFor(s -> find(s, k) == null, 160);
			server.runOnServer(s -> {
				List<ItemEntity> items = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(finalAt, finalAt).inflate(32));
				check(items.stream().anyMatch(i -> i.getItem().is(k.trophy())), k.id() + " left its trophy " + k.trophy());
				check(items.stream().anyMatch(i -> i.getItem().is(ModItems.WYRM_SHARD)), k.id() + " dropped Wyrm Shards");
				check(s.overworld().getEntitiesOfClass(Breeze.class, new AABB(finalAt, finalAt).inflate(128)).isEmpty()
						&& s.overworld().getEntitiesOfClass(ShardHoundEntity.class, new AABB(finalAt, finalAt).inflate(128)).isEmpty(), k.id() + ": minions dismissed");
				LOG.info("[boss_mode-test] {} drops: {}", k.id(), items.stream().map(i -> i.getItem().toString()).toList());
			});
		}
	}

	// ==================================================================================================== Tempest Wyvern
	private static void testFlight(ClientGameTestContext context, TestServerContext server, Kind k, String boss) {
		// a fresh flight: land first if its own flight cycle already took it up
		server.runOnServer(s -> {
			TempestWyvernEntity w = (TempestWyvernEntity) require(s, k);
			if (w.isFlying()) {
				w.getActionController().forceAction(s.overworld(), "land");
			}
		});
		server.waitFor(s -> !((TempestWyvernEntity) require(s, k)).isFlying() && require(s, k).getActionController().currentActionId() == null, 100);
		server.runOnServer(s -> check(require(s, k).getActionController().forceAction(s.overworld(), "takeoff"), "Wyvern takes off"));
		server.waitFor(s -> require(s, k).getActionController().currentActionId() == null, 60);
		server.runOnServer(s -> {
			TempestWyvernEntity w = (TempestWyvernEntity) require(s, k);
			double height = w.getY() - w.groundBelow(s.overworld());
			check(w.isFlying() && w.isNoGravity() && height > 4.0, fmt("Wyvern airborne %.1f blocks above the ground", height));
			w.setHealth(w.getMaxHealth() * 0.5F);
		});
		// the camera hovers in plain sight (survival with flight, so it stays a valid target the Wyvern keeps circling)
		Vec3 wyvern = server.computeOnServer(s -> require(s, k).position());
		server.runOnServer(s -> {
			player(s).getAbilities().mayfly = true;
			player(s).getAbilities().flying = true;
			player(s).onUpdateAbilities();
		});
		look(server, wyvern.add(-12, 2, 14), wyvern.add(0, 2, 0));
		context.waitTicks(3);
		shot(context, "39_wyvern_flying");
		context.waitTicks(30);
		server.runOnServer(s -> {
			TempestWyvernEntity w = (TempestWyvernEntity) require(s, k);
			check(!w.isFlying() || w.getHealth() == w.getMaxHealth() * 0.5F, "no regeneration while airborne (" + w.getHealth() + (w.isFlying() ? "" : ", landed meanwhile") + ")");
		});
		server.runOnServer(s -> {
			TempestWyvernEntity w = (TempestWyvernEntity) require(s, k);
			if (!w.isFlying()) {
				w.getActionController().forceAction(s.overworld(), "takeoff");
			}
		});
		server.waitFor(s -> ((TempestWyvernEntity) require(s, k)).isFlying() && require(s, k).getActionController().currentActionId() == null, 80);
		for (int i = 0; i < 4 && server.computeOnServer(s -> ((TempestWyvernEntity) require(s, k)).isFlying()); i++) {
			server.runOnServer(s -> require(s, k).damageCooldownTime = 0);
			server.runCommand("damage " + boss + " 40 minecraft:arrow by @p");
			context.waitTicks(1);
		}
		server.runOnServer(s -> {
			TempestWyvernEntity w = (TempestWyvernEntity) require(s, k);
			check(!w.isFlying() && "crash".equals(w.getActionController().currentActionId()), "arrows shot the Wyvern out of the sky");
		});
		context.waitTicks(20);
		lookAt(server, boss);
		shot(context, "39_wyvern_shot_down");
		server.runOnServer(s -> check(require(s, k).isExposed(), "shot-down Wyvern lies exposed"));
		server.waitFor(s -> require(s, k).getActionController().currentActionId() == null, 100);
	}

	// ==================================================================================================== Bastion Behemoth
	private static void testDefences(ClientGameTestContext context, TestServerContext server, Kind k, String boss) {
		server.runOnServer(s -> {
			BastionBehemothEntity b = (BastionBehemothEntity) require(s, k);
			b.getActionController().cancel(s.overworld());
			b.setNoAi(true);
			b.setBossState(BossState.IDLE);
		});
		float fromFront = hitFrom(server, k, boss, 1.0);
		float fromBehind = hitFrom(server, k, boss, -1.0);
		check(fromFront > 0 && fromFront < fromBehind * 0.65F, fmt("stone plating: a blow from the front takes %.1f HP, from behind %.1f", fromFront, fromBehind));
		float playerBefore = server.computeOnServer(s -> {
			((BastionBehemothEntity) require(s, k)).setFortified(true);
			return player(s).getHealth();
		});
		float fortified = hitFrom(server, k, boss, -1.0);
		server.runOnServer(s -> {
			check(fortified < fromBehind * 0.35F, fmt("fortified: the same blow from behind takes only %.1f HP", fortified));
			check(player(s).getHealth() < playerBefore, fmt("fortified: melee attacker spiked back (%.1f -> %.1f)", playerBefore, player(s).getHealth()));
		});
		lookAt(server, boss);
		shot(context, "49_behemoth_fortified");
		server.runOnServer(s -> {
			BastionBehemothEntity b = (BastionBehemothEntity) require(s, k);
			b.setFortified(false);
			b.setNoAi(false);
			b.setHealth(b.getMaxHealth() * 0.5F);
		});
		float half = server.computeOnServer(s -> require(s, k).getMaxHealth() * 0.5F);
		context.waitTicks(BastionConfig.REGEN_DELAY + 30);
		server.runOnServer(s -> check(require(s, k).getHealth() > half, "left alone, the Behemoth regenerates (" + require(s, k).getHealth() + ")"));
	}

	/** Damage a player-attributed 20-point blow does from in front ({@code dir} 1) or behind (-1) of the Behemoth. */
	private static float hitFrom(TestServerContext server, Kind k, String boss, double dir) {
		Vec3 spot = server.computeOnServer(s -> {
			BastionBehemothEntity b = (BastionBehemothEntity) require(s, k);
			b.setHealth(b.getMaxHealth());
			b.damageCooldownTime = 0;
			return b.position().add(com.bossmode.entity.boss.combat.CombatUtil.horizontalForward(b).scale(dir * (b.getBbWidth() * 0.5 + 3.0)));
		});
		server.runCommand(fmt("tp @a %.2f %.2f %.2f", spot.x, spot.y, spot.z));
		context(server);
		server.runCommand("damage " + boss + " 20 minecraft:player_attack by @p");
		return server.computeOnServer(s -> require(s, k).getMaxHealth() - require(s, k).getHealth());
	}

	private static void context(TestServerContext server) {
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
	}

	// ==================================================================================================== helpers
	static @Nullable RaidBossEntity<?> find(MinecraftServer server, Kind k) {
		for (Entity e : server.overworld().getAllEntities()) {
			if (e.getType() == k.type() && e instanceof RaidBossEntity<?> b && !b.isRemoved()) {
				return b;
			}
		}
		return null;
	}

	static RaidBossEntity<?> require(MinecraftServer server, Kind k) {
		RaidBossEntity<?> b = find(server, k);
		if (b == null) {
			throw new AssertionError("[boss_mode-test] FAILED: " + k.id() + " exists");
		}
		return b;
	}

	private static void lookAt(TestServerContext server, String selector) {
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ facing entity " + selector + " feet");
	}

	private static void clearMinions(TestServerContext server) {
		server.runOnServer(s -> {
			AABB area = new AABB(player(s).position(), player(s).position()).inflate(200);
			List<Entity> doomed = new ArrayList<>();
			doomed.addAll(s.overworld().getEntitiesOfClass(ShardHoundEntity.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(ShardWarlordEntity.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(Vex.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(Breeze.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(WyrmShardProjectile.class, area));
			doomed.addAll(s.overworld().getEntitiesOfClass(BoulderProjectile.class, area));
			doomed.forEach(Entity::discard);
		});
	}
}
