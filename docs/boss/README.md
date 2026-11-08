# BOSS Mode - Shard Dragon (Minecraft 26.3 · Fabric)

Three raid bosses, a final boss, their minions and mid-boss, all built from the Blender models in `Desktop/BOSS`.

| | |
|---|---|
| Minecraft / Fabric Loader / Fabric API / Loom | 26.3 / 0.19.5 / 0.161.0+26.3 / 1.17 |
| Gradle / Java / GeckoLib | 9.6.0 / 25 / 5.5.7 |
| Mod id / package | `boss_mode` / `com.bossmode` |

| Entity | Model | Role |
|---|---|---|
| `boss_mode:shard_dragon` | BOSS.obj | boss - 10 phases × 1,024 HP, grows ×2.0 → ×4.0 |
| `boss_mode:shard_warlord` | Mob2.obj | mid-boss (phases 5, 7, 9, 10) |
| `boss_mode:shard_hound` / `shard_spitter` / `volatile_hound` | Mob.obj | minions (melee / ranged / explosive) |
| `boss_mode:tempest_wyvern` | BOSS2.obj | flying raid boss - 5 phases x 1,024 HP, grows x1.6 -> x2.2, drops the **Tempest Eye** |
| `boss_mode:bastion_behemoth` | BOSS3.obj | armoured raid boss - 8 phases x 1,024 HP, grows x1.0 -> x1.35, drops the **Behemoth Core** |
| `boss_mode:void_sovereign` | Final BOSS.obj | **final boss** - 10 phases x 1,024 HP, grows x1.0 -> x1.5, Void Shield (Sovereign Shards), mace counters |

## Raid bosses: Tempest Wyvern and Bastion Behemoth

Both are called exactly like the Shard Dragon: **12 Wyrm Shards** on their own altar (sneak-right-click offers the rest),
a 5-second ritual, and the land around turns into a themed arena. Each drops its trophy for the Void Altar.

| | Tempest Wyvern | Bastion Behemoth |
|---|---|---|
| Altar / structure | **Tempest Altar** in the **Tempest Aerie** (`/locate structure boss_mode:tempest_aerie`; mountains, hills, meadows, plains) | **Bastion Altar** in the **Bastion Den** (`/locate structure boss_mode:bastion_den`; badlands, savanna, desert, plains) |
| Arena | 52 blocks, **Storm Reach** (grey-blue sky, rain, sparks): andesite and tuff, calcite / oxidized-copper rune rings, stone spires with lightning rods | 50 blocks, **Ember Waste** (orange sky, ash): basalt and blackstone, thin magma veins, shroomlight rings, basalt spires with campfires |
| Pools | 5 x 1,024 HP, armour 10 | 8 x 1,024 HP, armour 18 / toughness 10, 20 HP/s regeneration after 6 s alone |
| Mechanic | **Flies**: circles 7 blocks above its target, lands every ~13 s for 5 s (or dive-bombs). No regeneration in the air. Taking 7 % of its pool in one flight (a few arrow / trident hits) **shoots it down** - it lies EXPOSED for ~3 s. | **Stone plating**: blows landing on its front (±55°) do half damage - hit its flanks and back (except while it recovers from an attack). **Fortify** (phase 3+): 4.5 s of x0.2 damage, 30 % of melee damage spiked back, then a shockwave. |
| Patterns | Talon Strike, Tail Whip, Wind Gust, Feather Barrage (feathers can be punched back), Dive Bomb (air), Lightning Breath (tracking beam), Storm Bolts (marked lightning), Cyclone (roaming whirlwinds), Thunder Roar, Static Field, Summon (breezes, wraiths) | Maul, Tail Sweep (flanks / back only), Earthquake (jump it), Boulder Throw (molten boulder arcs), Rolling Charge (crushes blocks, ends dizzy), Fortify, Crystal Spikes (lines), Magma Breach (marked eruptions), Seismic Roar, Summon (hounds) |
| Phases | 1: melee + gust / dive / feathers - 2: + lightning - 3: + cyclone, breezes - 4: + thunder roar, static field, a Warlord - 5: everything, enraged at 30 % | 1: maul, tail, quake, boulder - 2: + roll - 3: + fortify - 4: + spikes, hounds - 5: + magma - 6: + seismic roar, a Warlord - 7-8: everything, enraged at 30 % |
| Loot | **Tempest Eye**, breeze rods, wind charges, diamonds, netherite scrap, enchanted golden apple | **Behemoth Core**, netherite scrap, ancient debris, diamonds, iron blocks, obsidian, enchanted golden apple |

Numbers: `config/TempestConfig.java`, `config/BastionConfig.java`; phase tables: `TempestPhase.java`, `BastionPhase.java`.

## Final boss: one trophy of each boss -> Void Altar -> Void Sovereign

* The **Void Altar** takes one trophy of each raid boss - a **Wyrm Heart** (Shard Dragon), a **Tempest Eye** (Tempest Wyvern)
  and a **Behemoth Core** (Bastion Behemoth); each sits on its own prong (crimson / cyan / amber). A second trophy of the
  same kind is refused. The third one starts a 7-second ritual; the **Void Sovereign** rises 12 blocks in front of the
  altar while a 56-block arena of deepslate, sculk and crying-obsidian veins forms around it (Void Expanse biome: crimson
  sky and fog, no rain, no natural spawns). Right-click the altar empty-handed to see which trophies are still missing.
* Void Altars generate in **Void Sanctums** (`/locate structure boss_mode:void_sanctum`; rarer than shrines: spacing 36 /
  separation 12 chunks, kept away from Shard Shrines) with a dais, obelisks, soul fires, a loot chest and a round arena.
* Ten phases (`SovereignPhase.java`) with 20 patterns. The original eleven: Claw Swipe, Tentacle Lash (all around), Ground
  Pound (shockwave rings - jump them), Void Orbs (homing, can be punched back), Rift Eruption, Void Grasp (tentacle pull +
  slam), Black Hole (4 s pull, then collapse), Void Step (teleports behind you), Eclipse (meteor storm + darkness), Soul
  Drain (heals it - leave the ring), Summon (the Shard Dragon's legion). New: Null Sweep (phase 4+, a knee-high blade sweeps
  270° - jump it), Tentacle Field (5+, checkerboard eruptions - step onto a square that just erupted), Dimension Rend (6+,
  line slash that leaves a burning rift), Abyssal Spears (7+, five volleys falling where you are heading), **Oblivion**
  (ultimate, 9-10: only the three circles of light are safe). Warlords join in phases 4, 5, 7, 8 and 10; enraged at 35 % in
  phase 10. Phase 6 adds Abyssal Carapace, Dimension Rend and Soul Chains to the earlier patterns.
* **Against aerial smashes (mace)** - the mace still works, but not for free:
  * it senses a mace within 32 blocks and then uses its counters three times as often;
  * **Skyward Lash** (every phase): anyone diving at it from above (falling more than 2 blocks, or launched upward) is
    whipped out of the air and loses the fall height; it reacts by itself, but not while it recovers from an attack (the
    opening is yours) - 5 s between reactions;
  * **Gravity Well** (4+): 6 s in which everything airborne within 16 blocks is dragged down with its fall height reset every
    tick - no smash is possible inside the ring, ground attacks work as usual;
  * **Abyssal Carapace** (6+): 4 s in which every blow is capped at 15 (before armour) and 25 % of the excess (up to 24) is
    thrown back at the attacker - a 30-block smash drops from 28.7 to 4.8 HP;
  * **Soul Chains** (6+): 5 s tethered to the spot you stand on - running, wind-charge launches or pearls yank you back.
* **Void Shield** (phases 3, 6 and 9): 4 / 5 / 6 **Sovereign Shards** rise around the arena; nothing hurts the Sovereign
  until every shard is broken (players only; the shards shoot void orbs). Each break announces how many are left; the last
  one drops the shield at once.
* Loot: 3-5 nether stars, netherite ingots, diamond blocks, enchanted golden apples, totems, echo shards, 50 % elytra,
  6,000 XP. All numbers in `config/SovereignConfig.java`.

## Boss gear: drops, tools, weapons and armour

Each boss drops its material (6-10 crystal scales, 6-10 tempest scales, 7-11 behemoth plates, 8-12 void ingots) along with
its trophy. The material makes a sword, pickaxe, axe, shovel, hoe and spear, and a helmet, chestplate, leggings and boots,
in the vanilla shapes with sticks for handles. One piece of a kind from each boss forges (shapeless) the ultimate piece,
which never wears out and keeps the highest level of every enchantment on the four (the higher of two that cannot go
together). All the numbers are in `gear/BossTier.java`; set and sword effects in `gear/BossGear.java`.

| | Netherite | Shard | Tempest | Behemoth | Void | Ultimate |
|---|---|---|---|---|---|---|
| Sword damage / speed | 8 / 1.6 | 9 / 1.6 | 10 / 1.8 | 11 / 1.4 | 12 / 1.6 | 14 / 1.7 |
| Axe / spear damage | 10 / 5 | 11 / 6 | 12 / 7 | 13 / 8 | 14 / 9 | 16 / 11 |
| Mining speed / uses | 9 / 2031 | 10 / 2600 | 11 / 3000 | 11 / 3800 | 13 / 4500 | 15 / unbreakable |
| Armour / toughness / knockback res. | 3-8-6-3 / 3 / 0.1 | 3-8-7-3 / 3.5 / 0.1 | 3-9-7-3 / 4 / 0.05 | 4-9-7-4 / 5 / 0.2 | 4-10-8-4 / 6 / 0.15 | 5-10-8-5 / 7 / 0.25 |

Full sets: shard - fire resistance, 2 damage back to melee attackers; tempest - 10 % faster, half fall damage; behemoth -
30 % less from explosions; void - 4 more max health, no wither; ultimate - all of them and 8 more max health. Swords on
hit: shard - fire 3 s; tempest - lightning one hit in ten; behemoth - thrown back; void - wither 2 s; ultimate - all.
Textures, armour layers and models come from `tools/boss_mode/gen_boss_gear.py`.

## Phases, flight and balance

* 10 phases of 1,024 HP each. The dragon grows (x2.0 -> x4.0), its attack pool and minion roster shrink, and damage
  types get sealed one by one (`BossPhase.java`): phases 9-10 take only melee **on the ground**.
* **Flight (phases 6-10):** the dragon alternates ground time and air time (6: 12 s air / 15 s ground ... 9-10: 10 s / 15 s).
  In the air it circles ~9 blocks above its target (out of melee reach) and only uses ranged patterns (volley, beam,
  crystal rain, void roar, cataclysm, summon); then it lands, or in phases 6-8 sometimes meteor-dives onto the target.
  * While it flies, **projectiles always hurt it** - even in the melee-only phases - and the boss bar lights the bow icon.
  * Taking 6 % of its pool during one flight (~4 good arrow hits) **shoots it down**: it crashes and lies EXPOSED for 3 s.
  * It does not regenerate while flying. Takeoff, landing and crash have their own animations and small shockwaves.
* **Phase 10** no longer requires the dragon to be EXPOSED: melee always hurts it on the ground (the old rule plus 12 HP/s
  regeneration made it practically unkillable). EXPOSED still doubles damage in phases 9-10; phase 10 damage x2.25 -> x2.10,
  cooldowns x0.55 -> x0.60.

## Summoning: shards -> Shard Altar -> dragon

* **Wyrm Shards** drop from every hostile mob (zombies, skeletons, creepers, spiders, endermen, ... and the dragon's own
  minions and mid-boss) under one rule: **10 % per player kill, +3 % per Looting level** (`BossConfig.SHARD_DROP_CHANCE`).
  The pool is added in code (`loot/ShardDrops.java`) to every `MONSTER`-category loot table, so natural and raid drops can
  never drift apart. The Shard Dragon itself drops a 2-4 shard trophy; bosses (Ender Dragon, Wither) are excluded.
* **Shard Shrines** generate at random in the overworld (plains, forests, taiga, savanna, badlands, desert, jungle,
  windswept hills, meadow, cherry grove, swamp, snowy plains, ice spikes; `random_spread` spacing 20 / separation 6 chunks).
  `/locate structure boss_mode:shard_shrine`. Each has a dais with the **Shard Altar**, two crystal obelisks, an offering chest
  (1-3 shards + supplies) and a round arena.
* **Shard Altar** (`boss_mode:shard_altar`): right-click with a shard to offer one, sneak-right-click to offer as many as are
  still needed. Each shard lights one of 12 crystal studs. The 12th starts a 5-second ritual and the dragon rises in the
  arena 9 blocks in front of the altar. The last shard is refused while a Shard Dragon is alive within 256 blocks. Mining
  the altar (diamond pickaxe) returns the shards offered so far and drops the altar, so it can be moved.
* **Arena terraforming** (`world/ArenaTerraformer.java`): when the dragon rises, a shockwave spreads from the altar and
  levels a 48-block radius into a flat corrupted waste 1-3 blocks below the altar (hills, trees, water and buildings
  cleared, holes filled), with a rune circle where the dragon stands, 14 obsidian spires as Cataclysm cover (too hard for
  the dragon to crush) and a 12-block ring that slopes back to the original terrain. The area becomes the **Shard Wastes**
  biome (violet sky and fog, no rain or snow, no natural spawns). Only natural terrain is removed within 7 blocks of the
  altar, so the shrine's dais (or whatever a player built around a moved altar) stays. The work is capped at 8,000 block
  changes per tick (a mountain top took 18 ticks, the slowest one 20 ms). Everything is tunable in `BossConfig`
  (`ALTAR_TERRAFORM`, `ARENA_*`). **Anything a player built inside the radius, other than next to the altar, is destroyed.**

## Build / run / test

```bash
./gradlew build                # -> build/libs/boss_mode-1.0.0.jar (needs Fabric API + GeckoLib 5.5.7 at runtime)
./gradlew runClient            # dev client
./gradlew runClientGameTest    # automated in-game test (~490 checks: models, every pattern and phase of all four bosses, flight /
                               #  shoot-down, plating / Fortify, minions, mid-boss, HUD, save/load, loot, shard drop rates, the four
                               #  structures and altars, arena terraforming, the Void Altar's three trophies)
BOSS_MODE_TEST_ONLY=altar ./gradlew runClientGameTest       # just the shard / shrine / altar part (~50 s)
BOSS_MODE_TEST_ONLY=wyvern ./gradlew runClientGameTest      # just the Tempest Wyvern (aerie, altar, arena, patterns, flight, phases)
BOSS_MODE_TEST_ONLY=behemoth ./gradlew runClientGameTest    # just the Bastion Behemoth (den, altar, arena, patterns, plating, phases)
BOSS_MODE_TEST_ONLY=sovereign ./gradlew runClientGameTest   # just the final boss (~2 min)
```

Game-test screenshots: `build/run/clientGameTest/screenshots/`.

## Layout

* `src/main/java/com/bossmode`
  * `config/BossConfig.java` - every number (stats, attack timings / ranges / damage, minions, mid-boss)
  * `entity/boss/BossPhase.java` - the 10-phase table (size, multipliers, attack pool, minion roster, sealed damage types)
  * `entity/boss/ShardDragonEntity.java`, `entity/boss/combat/*` - the dragon and its 13 patterns
  * `entity/action/*` - server-side action state machine shared by the dragon and the warlord
  * `entity/minion/*`, `entity/warlord/*`, `entity/projectile/*`
  * `block/ShardAltarBlock.java` (+ `TempestAltarBlock`, `BastionAltarBlock`), `block/VoidAltarBlock.java`,
    `world/ArenaTerraformer.java` (shard / storm / ember / void themes), `loot/ShardDrops.java`
  * `entity/raid/*` - the shared multi-phase boss base (`RaidBossEntity`: action machine, phase pools, growth, enrage,
    regeneration, HUD, minions, save/load) of the two raid bosses
  * `entity/tempest/*`, `entity/bastion/*` - the Tempest Wyvern (flight) and the Bastion Behemoth (plating, Fortify) and their
    patterns (`attack/*`); `entity/projectile/BoulderProjectile.java`
  * `entity/sovereign/*` - the Void Sovereign, its 10 phases, Sovereign Shards (`VoidPylonEntity`) and 21 actions
    (`attack/*`); `config/SovereignConfig.java`
* `src/main/resources/data/boss_mode/worldgen` - Shard Shrine / Tempest Aerie / Bastion Den / Void Sanctum structures,
  structure sets, template pools, weathering processors, arena biomes
* `src/client/java/com/bossmode/client` - GeckoLib poly-mesh loader, renderers, decorated boss bar HUD (+ mixin accessor)
* `src/gametest` - Fabric client game test
* `tools/blender` - Blender → GeckoLib conversion pipeline (see `tools/blender/README.md`); after editing only the
  animation keyframes, `python tools/blender/export_anims.py` rewrites the animation JSON without Blender
* `tools/altar` - `gen_shrine.py` / `gen_themed_shrine.py` (aerie, den) / `gen_void_sanctum.py` build the structure
  templates, `gen_altar_assets.py` (shard / tempest / bastion palettes) / `gen_void_altar_assets.py` the altar textures /
  models / blockstates (pure Python, no dependencies)
* `tools/pylon/gen_void_pylon.py` - the Void Pylon's cube model, animation and textures
