package net.loyalnetwork.coffeelib.neoforge;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.neoforged.fml.ModContainer;

/** Entry point for the NeoForge platform. */
public final class CoffeeLib {

    private CoffeeLib() {
    }

    /**
     * Creates a {@link ConfigManager} bound to {@code container}'s mod.
     * Call this from the mod's constructor, matching NeoForge's own
     * convention for {@code ModContainer#registerConfig} — configs
     * {@link ConfigManager#load loaded} through it register with NeoForge's
     * config system immediately, but fields only reflect what's on disk
     * once NeoForge fires {@code ModConfigEvent.Loading} (shortly after
     * construction, not synchronously during this call).
     */
    public static ConfigManager forMod(ModContainer container) {
        return new NeoForgeConfigManager(container, container.getEventBus());
    }
}
