package com.example.jail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A jail for servers, with nothing to install on the players' side: vanilla blocks, a datapack dimension, commands. */
public final class JailMod implements ModInitializer {
    public static final String ID = "jail";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel jail = Jail.level(server);
            if (jail != null) {
                JailBuilder.ensureBuilt(jail);
                LOGGER.info("Jail ready in {}", jail.dimension());
            } else {
                LOGGER.warn("The jail dimension {} is missing: is the mod's datapack disabled?", Jail.DIMENSION);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Jail.tick(server);
            MiniGames.tickWorld(server);
        });

        // In jail: only coal ore may be mined, no blocks placed, the lectern asks the quiz.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer prisoner && Jail.isJailed(prisoner)) || MiniGames.mayBreak(prisoner, level, state));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer prisoner && Jail.isJailed(prisoner) && state.is(Blocks.COAL_ORE)) {
                MiniGames.coalMined(prisoner, pos);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer prisoner) || !Jail.isJailed(prisoner)) {
                return InteractionResult.PASS;
            }
            if (level.getBlockState(hit.getBlockPos()).is(Blocks.LECTERN)) {
                // A player's game sends a right-click for each hand: ask once, for the main hand.
                if (hand == InteractionHand.MAIN_HAND) {
                    MiniGames.ask(prisoner);
                }
                return InteractionResult.SUCCESS;
            }
            return player.getItemInHand(hand).getItem() instanceof BlockItem ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        // The NPCs: one per minigame in the yard opens its room picker; one in each room leads back to the yard.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            String key = JailBuilder.npcKey(entity);
            if (key == null) {
                return InteractionResult.PASS;
            }
            // Each right-click comes as two packets (with and without a hit point) and for each hand: act on one.
            if (hand == InteractionHand.MAIN_HAND && hit == null && player instanceof ServerPlayer prisoner) {
                if (!Jail.isJailed(prisoner)) {
                    prisoner.sendSystemMessage(Component.literal("감옥에 있는 사람만 쓸 수 있습니다."));
                } else if (key.equals("exit")) {
                    Jail.toYard(prisoner);
                } else {
                    MiniGames.Game game = MiniGames.Game.byKey(key);
                    if (game != null) {
                        RoomMenu.open(prisoner, game);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        });
        // The NPCs cannot be hurt.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> JailBuilder.npcKey(entity) == null);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !MiniGames.answer(sender, message.signedContent()));

        // Dying in jail, or logging back in: back to the yard, with the jail's tools.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (Jail.isJailed(newPlayer)) {
                Jail.toYard(newPlayer);
                Jail.ensureKit(newPlayer);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Prisoner prisoner = Jail.prisoner(handler.player);
            if (prisoner != null && prisoner.remaining > 0) {
                handler.player.sendSystemMessage(Component.literal("아직 감옥에 있습니다. 남은 시간 " + Jail.clock(prisoner.remaining))
                        .withStyle(ChatFormatting.RED));
            }
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[] {"감옥", "jail"}) {
            dispatcher.register(Commands.literal(name)
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 365))
                                    .executes(JailMod::jail))));
        }
        for (String name : new String[] {"석방", "unjail"}) {
            dispatcher.register(Commands.literal(name)
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    JailData.get(context.getSource().getServer()).all().stream().map(p -> p.name), builder))
                            .executes(JailMod::release)));
        }
        for (String name : new String[] {"감옥시간", "jailtime"}) {
            dispatcher.register(Commands.literal(name).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Prisoner prisoner = Jail.prisoner(player);
                context.getSource().sendSuccess(() -> Component.literal(prisoner == null ? "감옥에 있지 않습니다."
                        : "감옥 남은 시간 " + Jail.clock(prisoner.remaining)), false);
                return prisoner == null ? 0 : prisoner.remaining / 20;
            }));
        }
        for (String name : new String[] {"감옥목록", "jaillist"}) {
            dispatcher.register(Commands.literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                var all = JailData.get(context.getSource().getServer()).all();
                StringBuilder text = new StringBuilder(all.isEmpty() ? "감옥에 아무도 없습니다." : "감옥에 있는 사람:");
                all.forEach(p -> text.append("\n- ").append(p.name).append(" (").append(Jail.clock(p.remaining)).append(")"));
                context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
                return all.size();
            }));
        }
    }

    private static int jail(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        Jail.jail(player, minutes);
        context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + "님을 감옥에 " + minutes + "분 동안 보냈습니다."), true);
        return 1;
    }

    /** Lets a prisoner out now, or, if they are offline, as soon as they come back. */
    private static int release(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "player");
        MinecraftServer server = context.getSource().getServer();
        for (ServerPlayer online : List.copyOf(server.getPlayerList().getPlayers())) {
            if (online.getGameProfile().name().equalsIgnoreCase(name) && Jail.release(online)) {
                context.getSource().sendSuccess(() -> Component.literal(name + "님을 석방했습니다."), true);
                return 1;
            }
        }
        JailData data = JailData.get(server);
        for (Map.Entry<UUID, Prisoner> entry : data.entries()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                entry.getValue().remaining = 0;
                data.setDirty();
                context.getSource().sendSuccess(() -> Component.literal(name + "님은 접속하면 바로 석방됩니다."), true);
                return 1;
            }
        }
        context.getSource().sendFailure(Component.literal(name + "님은 감옥에 있지 않습니다."));
        return 0;
    }
}
