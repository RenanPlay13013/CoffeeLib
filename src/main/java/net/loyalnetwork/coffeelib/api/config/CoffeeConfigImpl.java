package net.loyalnetwork.coffeelib.api.config;

import net.loyalnetwork.coffeelib.config.ConfigManager;

public class CoffeeConfigImpl implements CoffeeConfigApi {
    private final ConfigManager configManager;

    public CoffeeConfigImpl(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public ConfigManager configManager() {
        return configManager;
    }
}
