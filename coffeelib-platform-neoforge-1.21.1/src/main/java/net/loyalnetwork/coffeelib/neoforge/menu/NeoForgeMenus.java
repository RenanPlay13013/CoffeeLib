package net.loyalnetwork.coffeelib.neoforge.menu;

import net.loyalnetwork.coffeelib.api.menu.Menu;
import net.loyalnetwork.coffeelib.api.menu.MenuClick;
import net.loyalnetwork.coffeelib.api.menu.MenuSnapshot;
import net.loyalnetwork.coffeelib.api.scheduler.Scheduler;
import net.loyalnetwork.coffeelib.neoforge.text.NeoForgeTextFormatter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Vanilla chest menu type, so neither a custom screen nor a client-only class is required. */
public final class NeoForgeMenus {
    private final String owner;
    private final Scheduler scheduler;
    private final Logger logger;

    public NeoForgeMenus(String owner, Scheduler scheduler) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Logger.getLogger("CoffeeLib." + owner + ".menus");
    }

    public Menu<ItemStack, ServerPlayer> chest(int rows) { return Menu.chest(rows, this::open); }

    private void open(MenuSnapshot<ItemStack, ServerPlayer> spec, ServerPlayer player) {
        if (player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("Menus must be opened on the server thread");
        if (player.hasDisconnected()) throw new IllegalStateException("Player disconnected");
        SimpleContainer contents = new SimpleContainer(spec.rows() * 9);
        for (Map.Entry<Integer, MenuSnapshot.Entry<ItemStack, ServerPlayer>> item : spec.items().entrySet())
            contents.setItem(item.getKey(), item.getValue().stack().copy());
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (window, inventory, viewer) -> new LockedChestMenu(spec, window, inventory, contents, player),
                new NeoForgeTextFormatter().parse(spec.title(), player, spec.context()));
        player.openMenu(provider);
    }

    private final class LockedChestMenu extends ChestMenu {
        private final MenuSnapshot<ItemStack, ServerPlayer> spec;
        private final ServerPlayer viewer;
        private boolean actionQueued;

        private LockedChestMenu(MenuSnapshot<ItemStack, ServerPlayer> spec, int id, Inventory inventory,
                                SimpleContainer contents, ServerPlayer viewer) {
            super(typeFor(spec.rows()), id, inventory, contents, spec.rows());
            this.spec = spec;
            this.viewer = viewer;
        }

        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            // No super.clicked: blocks all moves, swaps, drops, quick-crafting, hotbar
            // swaps, collection and carried-stack interactions on this container.
            // The client may predict a pickup; force the authoritative locked state back.
            broadcastFullState();
            if (clickType != ClickType.PICKUP || (button != 0 && button != 1)) return;
            if (player != viewer || slot < 0 || slot >= spec.rows() * 9) return;
            MenuSnapshot.Entry<ItemStack, ServerPlayer> entry = spec.items().get(slot);
            if (entry == null || actionQueued) return;
            actionQueued = true;
            scheduler.later(1, () -> {
                actionQueued = false;
                if (viewer.containerMenu != this || viewer.hasDisconnected()) return;
                try { entry.onClick().accept(new MenuClick<>(viewer, slot)); }
                catch (RuntimeException error) { logger.log(Level.SEVERE, "Menu click failed in " + owner + " at " + slot, error); }
            });
        }

        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean canDragTo(net.minecraft.world.inventory.Slot slot) { return false; }
        @Override public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) { return false; }
    }

    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Unsupported chest rows: " + rows);
        };
    }
}
