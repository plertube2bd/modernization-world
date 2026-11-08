package com.bossmode;

import com.bossmode.loot.ShardDrops;
import com.bossmode.registry.ModBlocks;
import com.bossmode.registry.ModEntities;
import com.bossmode.registry.ModItems;
import com.bossmode.registry.ModSounds;
import com.bossmode.world.ArenaTerraformer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + dedicated server) entrypoint: registries, attributes and gameplay only.
 * Client-only model / renderer code lives in {@link com.bossmode.client.BossModeClient}.
 */
public class BossMode implements ModInitializer {
	public static final String MOD_ID = "boss_mode";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModEntities.init();
		ModBlocks.init();
		ModItems.init();
		com.bossmode.gear.BossGear.init();
		ShardDrops.init();
		ArenaTerraformer.init();
		LOGGER.info("BOSS Mode initialised: Shard Dragon registered as {}", ModEntities.SHARD_DRAGON_ID);
	}
}
