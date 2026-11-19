package net.loyalnetwork.coffeelib.paper.economy;

import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.core.economy.SharedEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * CoffeeLib's economy and Vault's, joined in both directions, as
 * {@code economy.yml} says:
 * <ul>
 *   <li><b>export</b> - a CoffeeLib currency registered with Vault as an
 *       {@link Economy}, at high priority, so Vault plugins use it;</li>
 *   <li><b>import</b> - the best Vault economy that is not CoffeeLib's own
 *       provided to CoffeeLib as a currency, owned by the plugin behind it, and
 *       followed as Vault economies come and go.</li>
 * </ul>
 * Only installed when the Vault plugin is present; nothing here is loaded
 * otherwise.
 */
public final class VaultBridge implements Listener {

    private static final Logger LOGGER = Logger.getLogger(VaultBridge.class.getName());

    private final JavaPlugin plugin;
    private final EconomyConfig config;
    private final Executor serverThread;
    private String importedOwner;

    private VaultBridge(JavaPlugin plugin, EconomyConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.serverThread = task -> {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, task);
            } catch (RuntimeException disabled) {
                task.run(); // CoffeeLib is shutting down: no next tick to wait for
            }
        };
    }

    /** Sets both directions up. Called from CoffeeLib's onEnable when Vault is installed. */
    public static void install(JavaPlugin plugin, EconomyConfig config) {
        VaultBridge bridge = new VaultBridge(plugin, config);
        bridge.export();
        plugin.getServer().getPluginManager().registerEvents(bridge, plugin);
        // After every plugin's onEnable, when their Vault economies are registered.
        plugin.getServer().getScheduler().runTask(plugin, bridge::refreshImport);
    }

    private void export() {
        String id = config.vaultExport == null ? "" : config.vaultExport.trim();
        if (id.isEmpty()) {
            return;
        }
        if (id.equalsIgnoreCase(importId())) {
            LOGGER.warning("[CoffeeLib] economy.yml exports '" + id + "', which is the Vault economy imported "
                    + "into CoffeeLib; not exporting it back to Vault");
            return;
        }
        Currency currency = SharedEconomy.registry().currency(id);
        @SuppressWarnings("deprecation") // Vault's name-based calls have nothing better to go on
        VaultExport export = new VaultExport(currency, name -> Bukkit.getOfflinePlayer(name).getUniqueId());
        plugin.getServer().getServicesManager().register(Economy.class, export, plugin, ServicePriority.High);
        LOGGER.info("[CoffeeLib] Currency '" + id + "' is offered to Vault plugins as the server's economy"
                + (currency.isAvailable() ? "" : " once a mod provides it"));
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            refreshImport();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            refreshImport();
        }
    }

    /** Provides the best non-CoffeeLib Vault economy as {@code vaultImport}, replacing the previous one. */
    void refreshImport() {
        String id = importId();
        if (id.isEmpty()) {
            return;
        }
        if (importedOwner != null) {
            SharedEconomy.forOwner(importedOwner).unregister(id);
            importedOwner = null;
        }
        Optional<RegisteredServiceProvider<Economy>> best = plugin.getServer().getServicesManager()
                .getRegistrations(Economy.class).stream()
                .filter(registration -> !(registration.getProvider() instanceof VaultExport))
                .max(Comparator.comparing(RegisteredServiceProvider::getPriority));
        if (best.isEmpty()) {
            return;
        }
        String owner = best.get().getPlugin().getName();
        Economy economy = best.get().getProvider();
        try {
            SharedEconomy.forOwner(owner).register(new VaultImport(id, economy, serverThread,
                    (UUID account) -> Bukkit.getOfflinePlayer(account)));
            importedOwner = owner;
            LOGGER.info("[CoffeeLib] Vault economy '" + economy.getName() + "' (" + owner + ") is available to "
                    + "CoffeeLib as currency '" + id + "'");
        } catch (CoffeeLibException e) {
            LOGGER.warning("[CoffeeLib] Could not bring Vault economy '" + economy.getName() + "' in as '" + id
                    + "': " + e.getMessage() + ". Change vaultImport in CoffeeLib's economy.yml");
        }
    }

    private String importId() {
        return config.vaultImport == null ? "" : config.vaultImport.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
