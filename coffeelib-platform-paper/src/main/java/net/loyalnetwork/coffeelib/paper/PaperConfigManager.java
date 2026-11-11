package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry;
import net.loyalnetwork.coffeelib.core.config.reload.ManagedConfigService;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a plugin gets from {@link CoffeeLib#forPlugin}.
 * <p>
 * Paper has no config system of its own that CoffeeLib defers to, so every
 * config here is one CoffeeLib manages end to end:
 * {@link ConfigFormat#NATIVE} resolves to {@link ConfigFormat#YAML} (what
 * this platform has always written), and {@code @ConfigFile(format = JSON)}
 * or {@code HOCON} picks a different backend for that one class. Loading is
 * synchronous, as it always was.
 * <p>
 * Because they are all managed, they all get
 * {@link net.loyalnetwork.coffeelib.api.config.ConfigReloadService}: manual
 * reload by file name and an optional file watcher, the same contract the
 * Forge-like platforms expose through {@code /coffeelib config}. Paper
 * plugins usually ship their own {@code /myplugin reload}, so this module
 * registers no command of its own — call the service from yours, or let the
 * watcher do it.
 */
final class PaperConfigManager implements ReloadableConfigManager {

    private final ManagedConfigService managed;

    PaperConfigManager(String pluginName, Path dataFolder) {
        this.managed = new ManagedConfigService(pluginName, dataFolder);
        ConfigReloadRegistry.register(pluginName, this);
    }

    @Override
    public <T> T load(Class<T> type) {
        return managed.load(type, ConfigFormat.YAML);
    }

    @Override
    public void reload(Object config) {
        managed.reload(config);
    }

    @Override
    public void save(Object config) {
        managed.save(config);
    }

    @Override
    public List<String> managedFiles() {
        return managed.managedFiles();
    }

    @Override
    public boolean reload(String fileName) {
        return managed.reload(fileName);
    }

    @Override
    public int reloadAll() {
        return managed.reloadAll();
    }

    @Override
    public boolean startHotReload() {
        return managed.startHotReload();
    }

    @Override
    public boolean stopHotReload() {
        return managed.stopHotReload();
    }

    @Override
    public boolean isHotReloadRunning() {
        return managed.isHotReloadRunning();
    }

    @Override
    public void addReloadListener(Consumer<Object> listener) {
        managed.addReloadListener(listener);
    }
}
