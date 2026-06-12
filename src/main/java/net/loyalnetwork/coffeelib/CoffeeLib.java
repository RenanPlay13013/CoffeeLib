package net.loyalnetwork.coffeelib;

import net.loyalnetwork.coffeelib.api.config.CoffeeConfigApi;
import net.loyalnetwork.coffeelib.api.config.CoffeeConfigImpl;
import net.loyalnetwork.coffeelib.config.ConfigManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoffeeLib extends JavaPlugin {

    @Override
    public void onEnable() {
        ConfigManager configManager = ConfigManager.builder(this).build();
        configManager.load();

        CoffeeConfigApi api = new CoffeeConfigImpl(configManager);
        getServer().getServicesManager().register(
                CoffeeConfigApi.class,
                api,
                this,
                ServicePriority.Normal
        );

        getLogger().info("CoffeeLib config service registered.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }
}
