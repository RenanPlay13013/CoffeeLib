package net.loyalnetwork.coffeelib.paper.menu;

import net.loyalnetwork.coffeelib.api.menu.Menu;
import net.loyalnetwork.coffeelib.api.menu.MenuClick;
import net.loyalnetwork.coffeelib.api.menu.MenuSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Spigot 1.8.8-compatible implementation, with one listener per plugin owner. */
public final class PaperMenus implements Listener {
    private static final Map<JavaPlugin, PaperMenus> INSTANCES = new IdentityHashMap<>();
    private final JavaPlugin owner;
    private final java.util.Set<Inventory> open = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private PaperMenus(JavaPlugin plugin) { this.owner = plugin; }

    public static synchronized PaperMenus forPlugin(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Menu registration must run on the Bukkit main thread");
        if (!plugin.isEnabled()) throw new IllegalStateException("Cannot use menus for a disabled plugin");
        PaperMenus existing = INSTANCES.get(plugin);
        if (existing != null) return existing;
        PaperMenus created = new PaperMenus(plugin);
        Bukkit.getPluginManager().registerEvents(created, plugin);
        INSTANCES.put(plugin, created);
        return created;
    }

    public Menu<ItemStack, Player> chest(int rows) { return Menu.chest(rows, this::open); }

    private void open(MenuSnapshot<ItemStack, Player> snapshot, Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Menus must open on the Bukkit server thread");
        if (!owner.isEnabled() || !player.isOnline()) throw new IllegalStateException("Plugin disabled or player offline");
        Holder holder = new Holder(snapshot);
        // Legacy 1.8 clients support up to 32 characters in the inventory title.
        // Placeholders first - CoffeeLib's with the menu's context, then PlaceholderAPI's - as
        // the Forge and NeoForge menus do, then the colour codes.
        String resolved = new net.loyalnetwork.coffeelib.paper.placeholder.PaperPlaceholderManager(owner, owner.getName())
                .apply(player, snapshot.title(), snapshot.context());
        String title = org.bukkit.ChatColor.translateAlternateColorCodes('&', resolved);
        if (title.length() > 32) throw new IllegalArgumentException("Inventory title exceeds the legacy 32-character limit");
        Inventory inventory = Bukkit.createInventory(holder, snapshot.rows() * 9, title);
        holder.inventory = inventory;
        for (Map.Entry<Integer, MenuSnapshot.Entry<ItemStack, Player>> item : snapshot.items().entrySet()) {
            inventory.setItem(item.getKey(), item.getValue().stack().clone());
        }
        open.add(inventory);
        try { player.openInventory(inventory); }
        catch (RuntimeException error) { open.remove(inventory); throw error; }
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Holder holder = holder(event.getView().getTopInventory());
        if (holder == null) return;
        // Cancel every click: shift, double click, number key, drop, collect-to-cursor,
        // and clicks in the lower player inventory are all denied.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // By name: stable across Bukkit versions, and SWAP_OFFHAND does not exist on 1.8.
        net.loyalnetwork.coffeelib.core.menu.MenuClicks.Decoded click =
                net.loyalnetwork.coffeelib.core.menu.MenuClicks.bukkit(event.getClick().name(), event.getHotbarButton());
        if (click == null) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= holder.snapshot.rows() * 9) return;
        MenuSnapshot.Entry<ItemStack, Player> entry = holder.snapshot.items().get(slot);
        if (entry == null || !entry.accepts(click.type())) return;
        // Only one action can be pending for this menu. A client sending several
        // click packets in one tick must not duplicate a button action.
        if (holder.actionQueued) return;
        holder.actionQueued = true;
        // Bukkit explicitly forbids opening/closing inventories during InventoryClickEvent.
        // Defer callbacks until the following tick so callers can safely close/open screens.
        Bukkit.getScheduler().runTask(owner, () -> {
            holder.actionQueued = false;
            if (!owner.isEnabled() || !player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try { entry.onClick().accept(new MenuClick<>(player, slot, click.type(), click.hotbarKey())); }
            catch (RuntimeException error) { owner.getLogger().log(java.util.logging.Level.SEVERE, "Menu click failed at slot " + slot, error); }
        });
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (holder(event.getView().getTopInventory()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getInventory());
    }

    private Holder holder(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof Holder holder)) return null;
        return holder.manager == this && open.contains(inventory) ? holder : null;
    }

    public static synchronized void releaseOwner(org.bukkit.plugin.Plugin plugin) {
        PaperMenus menu = INSTANCES.remove(plugin);
        if (menu == null) return;
        for (Inventory inventory : new ArrayList<>(menu.open)) {
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(inventory.getViewers())) viewer.closeInventory();
        }
        menu.open.clear();
    }

    private final class Holder implements InventoryHolder {
        private final PaperMenus manager = PaperMenus.this;
        private final MenuSnapshot<ItemStack, Player> snapshot;
        private Inventory inventory;
        private boolean actionQueued;
        private Holder(MenuSnapshot<ItemStack, Player> snapshot) { this.snapshot = snapshot; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
