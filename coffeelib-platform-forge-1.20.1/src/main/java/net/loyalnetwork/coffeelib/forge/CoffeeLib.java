package net.loyalnetwork.coffeelib.forge;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Entry point for the Forge platform. */
public final class CoffeeLib {

    private CoffeeLib() {
    }

    /**
     * Creates a {@link ConfigManager} bound to the calling mod. Call this
     * from the mod's constructor, matching Forge's own convention for
     * {@code ModLoadingContext.get().registerConfig(...)} — both that and
     * the mod event bus lookup rely on thread-local context Forge only sets
     * up during mod construction. Configs {@link ConfigManager#load loaded}
     * through it register with Forge's config system immediately, but
     * fields only reflect what's on disk once Forge fires
     * {@code ModConfigEvent.Loading} (shortly after construction, not
     * synchronously during this call).
     */
    public static ConfigManager forMod() {
        return new ForgeConfigManager(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
