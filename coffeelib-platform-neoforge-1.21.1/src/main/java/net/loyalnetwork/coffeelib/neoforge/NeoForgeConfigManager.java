package net.loyalnetwork.coffeelib.neoforge;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry;
import net.loyalnetwork.coffeelib.core.config.reload.ManagedConfigService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a mod gets from {@link CoffeeLib#forMod}. Routes each config class by
 * its {@code @ConfigFile(format = ...)}:
 * <ul>
 *   <li>{@link ConfigFormat#NATIVE} — {@link NeoForgeSpecConfigManager}, i.e.
 *       NeoForge's own {@code ModConfigSpec}. NeoForge owns the file, the
 *       validation, and <em>when</em> the fields get populated (on its
 *       {@code ModConfigEvent}, after mod construction).</li>
 *   <li>anything else (YAML, JSON, HOCON) — {@link ManagedConfigService},
 *       which reads and validates the file synchronously inside {@link #load}
 *       and never involves the mod-loading lifecycle at all.</li>
 * </ul>
 * The {@link net.loyalnetwork.coffeelib.api.config.ConfigReloadService} half
 * of this type covers the managed configs only — they are the ones CoffeeLib
 * can reload on demand or watch for changes. Native configs stay NeoForge's
 * to reload ({@code /neoforge config}, {@code ModConfigEvent.Reloading}),
 * which is why {@link #reloadAll()} leaves them alone rather than pretending
 * to.
 * <p>
 * The managed side is registered in {@link ConfigReloadRegistry} under the
 * owning mod id so {@code /coffeelib config} can reach it — see
 * {@link CoffeeLibCommands}.
 * <p>
 * The native side is created lazily: registering a {@code ModConfig} is only
 * legal while the mod is being constructed, so a mod that uses managed
 * formats exclusively can build its manager whenever it likes (see
 * {@link CoffeeLib#detached}).
 */
final class NeoForgeConfigManager implements ReloadableConfigManager {

    private final String modId;
    private final ModContainer container;
    private final IEventBus modEventBus;
    private final Supplier<Path> configDirectory;

    private ManagedConfigService managed;

    private NeoForgeSpecConfigManager spec;

    NeoForgeConfigManager(String modId, ModContainer container, IEventBus modEventBus,
                           Supplier<Path> configDirectory) {
        this.modId = modId;
        this.container = container;
        this.modEventBus = modEventBus;
        this.configDirectory = configDirectory;
        ConfigReloadRegistry.register(modId, this);
    }

    @Override
    public <T> T load(Class<T> type) {
        return ManagedConfigService.handles(type) ? managed().load(type) : spec().load(type);
    }

    @Override
    public void reload(Object config) {
        ManagedConfigService service = managedOrNull();
        if (service != null && service.owns(config)) {
            service.reload(config);
            return;
        }
        spec().reload(config);
    }

    @Override
    public void save(Object config) {
        ManagedConfigService service = managedOrNull();
        if (service != null && service.owns(config)) {
            service.save(config);
            return;
        }
        spec().save(config);
    }

    @Override
    public List<String> managedFiles() {
        ManagedConfigService service = managedOrNull();
        return service != null ? service.managedFiles() : List.of();
    }

    @Override
    public boolean reload(String fileName) {
        ManagedConfigService service = managedOrNull();
        return service != null && service.reload(fileName);
    }

    @Override
    public int reloadAll() {
        ManagedConfigService service = managedOrNull();
        return service != null ? service.reloadAll() : 0;
    }

    @Override
    public boolean startHotReload() {
        ManagedConfigService service = managedOrNull();
        return service != null && service.startHotReload();
    }

    @Override
    public boolean stopHotReload() {
        ManagedConfigService service = managedOrNull();
        return service != null && service.stopHotReload();
    }

    @Override
    public boolean isHotReloadRunning() {
        ManagedConfigService service = managedOrNull();
        return service != null && service.isHotReloadRunning();
    }

    @Override
    public void addReloadListener(Consumer<Object> listener) {
        managed().addReloadListener(listener);
    }

    /** The mod id this manager belongs to — the key an operator types in {@code /coffeelib config}. */
    String modId() {
        return modId;
    }

    /**
     * The managed-format side, built on first use. Deferred because resolving
     * the loader's {@code config/} directory means touching FML's own
     * statics, which a mod using Forge-native configs exclusively should
     * never pay for (and which no longer makes this type impossible to build
     * outside a running game).
     */
    private synchronized ManagedConfigService managed() {
        if (managed == null) {
            managed = new ManagedConfigService(modId, configDirectory.get());
        }
        return managed;
    }

    /** The managed side if anything has used it yet, {@code null} otherwise — never builds it. */
    private synchronized ManagedConfigService managedOrNull() {
        return managed;
    }

    private synchronized NeoForgeSpecConfigManager spec() {
        if (spec == null) {
            if (container == null || modEventBus == null) {
                throw new ConfigException("This manager was created outside mod construction, "
                        + "so it can only handle CoffeeLib-managed formats. Give the config class an explicit "
                        + "@ConfigFile(format = ...), or build the manager with CoffeeLib.forMod(container) from "
                        + "the mod constructor to use NeoForge's native config system.");
            }
            spec = new NeoForgeSpecConfigManager(container, modEventBus);
        }
        return spec;
    }
}
