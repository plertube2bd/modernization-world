package com.example.jail;

import com.example.jail.MiniGames.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * The room picker an NPC opens: a one-row chest showing a minigame's rooms and who is in each. Clicking a room goes
 * there. Nothing can be taken out of it; it is a plain chest to the player's game, so players need no mod.
 */
public final class RoomMenu extends ChestMenu {
    /** Where each room's icon sits in the row. */
    public static final int[] SLOTS = {2, 4, 6};

    private final Game game;

    private RoomMenu(int id, Inventory inventory, SimpleContainer icons, Game game) {
        super(MenuType.GENERIC_9x1, id, inventory, icons, 1);
        this.game = game;
    }

    public static void open(ServerPlayer player, Game game) {
        SimpleContainer icons = new SimpleContainer(9);
        for (Rooms.Room room : Rooms.of(game)) {
            List<String> inside = Rooms.playersIn(player.level().getServer(), room).stream().map(p -> p.getGameProfile().name()).toList();
            ItemStack icon = new ItemStack(iconOf(game), Math.max(1, inside.size()));
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(room.title() + (inside.isEmpty() ? " (비어 있음)" : " (" + inside.size() + "명)"))
                    .withStyle(inside.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(inside.isEmpty() ? "혼자 하려면 이 방으로" : "같이 하려면 이 방으로").withStyle(ChatFormatting.GRAY));
            for (String name : inside) {
                lore.add(Component.literal("- " + name).withStyle(ChatFormatting.WHITE));
            }
            icon.set(DataComponents.LORE, new ItemLore(lore));
            icons.setItem(SLOTS[room.index()], icon);
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RoomMenu(id, inventory, icons, game),
                Component.literal(game.label + " - 방 고르기")));
    }

    private static Item iconOf(Game game) {
        return switch (game) {
            case COAL -> Items.COAL;
            case PARKOUR -> Items.LEATHER_BOOTS;
            case TARGET -> Items.BOW;
            case FISH -> Items.COD;
            case QUIZ -> Items.BOOK;
        };
    }

    /** The room behind a slot of the menu, or null. */
    public static Rooms.Room roomAt(Game game, int slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                return Rooms.room(game, i);
            }
        }
        return null;
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        // Nothing moves: a click on a room takes the player there; any other click does nothing.
        Rooms.Room room = roomAt(this.game, slot);
        if (room != null && player instanceof ServerPlayer prisoner && Jail.isJailed(prisoner)) {
            prisoner.closeContainer();
            Rooms.enter(prisoner, room);
        } else {
            sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
