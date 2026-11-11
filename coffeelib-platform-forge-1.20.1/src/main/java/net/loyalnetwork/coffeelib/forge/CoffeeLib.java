package net.loyalnetwork.coffeelib.forge;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Entry point for the Forge platform. */
public final class CoffeeLib {

    private CoffeeLib() {
    }

    /**
     * Creates a {@link ReloadableConfigManager} bound to the calling mod.
     * Call this from the mod's constructor: both the mod event bus lookup and
     * {@code ModLoadingContext.get()} rely on thread-local context Forge only
     * populates while a mod is being constructed.
     * <p>
     * What {@link ReloadableConfigManager#load} then does depends on the
     * config class:
     * <ul>
     *   <li>{@code @ConfigFile("x")} (i.e. {@link ConfigFormat#NATIVE}) —
     *       registers with Forge's config system immediately, but the fields
     *       only reflect what is on disk once Forge fires
     *       {@code ModConfigEvent.Loading}, shortly after construction rather
     *       than synchronously during the call.</li>
     *   <li>{@code @ConfigFile(value = "x", format = YAML)} and the other
     *       CoffeeLib-managed formats — the file in {@code config/} is read,
     *       validated and written back during the call, and the instance you
     *       get is already populated. Nothing about it is tied to mod
     *       loading, and it can be reloaded at any time through this same
     *       manager or {@code /coffeelib config}.</li>
     * </ul>
     */
    public static ReloadableConfigManager forMod() {
        return new ForgeConfigManager(
                ModLoadingContext.get().getActiveNamespace(),
                FMLJavaModLoadingContext.get().getModEventBus(),
                CoffeeLib::configDirectory);
    }

    /**
     * Creates a manager for CoffeeLib-managed formats only, callable at any
     * point in a mod's life — not just from its constructor.
     * <p>
     * Use it when you want configs that have nothing to do with the
     * mod-loading lifecycle: loading one reads the file there and then, so a
     * manager built in a server-start handler, a command, or a reload hook
     * works exactly the same as one built during construction. Asking it for
     * a {@link ConfigFormat#NATIVE} config fails with a
     * {@code ConfigException}, since registering a {@code ModConfig} is only
     * legal while the mod is being constructed.
     *
     * @param modId the mod this manager belongs to — also the key an operator
     *              types in {@code /coffeelib config reload <mod>}
     */
    public static ReloadableConfigManager detached(String modId) {
        return new ForgeConfigManager(modId, null, CoffeeLib::configDirectory);
    }

    /**
     * Forge's {@code config/} directory, the same one a native
     * {@code ModConfig} writes its TOML into — so a mod's managed files sit
     * next to the files an operator already expects to find there.
     * <p>
     * Passed around as a supplier, not a resolved path: FML only fills these
     * in once the loader is running, and a mod using native configs
     * exclusively should never make CoffeeLib ask.
     */
    private static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
