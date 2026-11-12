package net.loyalnetwork.coffeelib.core.config.reload;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ConfigReloadService;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.loyalnetwork.coffeelib.core.config.ConfigFileNames;
import net.loyalnetwork.coffeelib.core.config.DefaultConfigManager;
import net.loyalnetwork.coffeelib.core.config.format.ConfigBackends;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns every config an owner loaded in a format CoffeeLib manages itself —
 * i.e. anything but {@link ConfigFormat#NATIVE} — and implements the
 * operator-facing {@link ConfigReloadService} over them.
 * <p>
 * The point of a managed format on a Forge-like platform is that nothing here
 * waits on the mod-loading lifecycle: {@link #load} reads, validates and
 * writes the file synchronously and hands back a fully populated instance,
 * and from then on reloading is explicit — {@link #reload(String)} /
 * {@link #reloadAll()} on demand, or {@link #startHotReload()} for a watcher
 * that reloads as the file changes on disk. No {@code ModConfigEvent}, no
 * {@code registerConfig}, no "fields are populated later".
 * <p>
 * One service per owner; it holds a {@link DefaultConfigManager} per format
 * (all pointed at the same base directory) because the backend, not the
 * manager, is what varies between formats.
 * <p>
 * Thread safety: the watcher runs on its own daemon thread while commands
 * arrive on the server thread, so the entry list is copy-on-write and every
 * reload of a given entry is serialized on that entry.
 */
public final class ManagedConfigService implements ConfigReloadService {

    private static final Logger LOGGER = Logger.getLogger(ManagedConfigService.class.getName());

    /** How long to wait after a change event before reading, letting a slow writer finish. */
    private static final long SETTLE_MILLIS = 150L;

    private final Path baseDirectory;
    private final String ownerId;
    private final Map<ConfigFormat, DefaultConfigManager> managers = new ConcurrentHashMap<>();
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final List<Consumer<Object>> listeners = new CopyOnWriteArrayList<>();

    private volatile ConfigWatcher watcher;

    /**
     * @param ownerId the plugin/mod id these configs belong to, used in log
     *                lines and as the key operators type in commands
     * @param baseDirectory directory the files live in (the owner's data
     *                      folder, or the loader's {@code config/})
     */
    public ManagedConfigService(String ownerId, Path baseDirectory) {
        this.ownerId = ownerId;
        this.baseDirectory = baseDirectory;
    }

    public String ownerId() {
        return ownerId;
    }

    public Path baseDirectory() {
        return baseDirectory;
    }

    /** Whether {@code type} declares a format this service handles (anything but NATIVE). */
    public static boolean handles(Class<?> type) {
        ConfigFile configFile = type.getAnnotation(ConfigFile.class);
        return configFile != null && configFile.format().isManagedByCoffeeLib();
    }

    /**
     * Reads {@code type}'s file now (creating it with defaults on first run)
     * and registers the instance for later reloads. Validation failures
     * surface from this call, not from a later lifecycle event.
     */
    public <T> T load(Class<T> type) {
        return load(type, null);
    }

    /**
     * Same as {@link #load(Class)}, but resolves {@link ConfigFormat#NATIVE}
     * to {@code nativeFallback} instead of rejecting it.
     * <p>
     * For a platform whose native format <em>is</em> a format CoffeeLib
     * parses — Paper, where NATIVE means the YAML this service already
     * speaks — that makes every config a managed one, so
     * {@code @ConfigFile(format = ...)} means the same thing there as on a
     * Forge-like platform instead of being quietly ignored.
     *
     * @param nativeFallback format to use for a NATIVE class, or {@code null}
     *                       to reject NATIVE (the Forge-like platforms, where
     *                       the loader's own config system handles it)
     */
    public <T> T load(Class<T> type, ConfigFormat nativeFallback) {
        ConfigFile configFile = type.getAnnotation(ConfigFile.class);
        if (configFile == null) {
            throw new ConfigException("Class " + type.getName() + " is not annotated with @ConfigFile");
        }
        ConfigFormat format = configFile.format();
        if (format == ConfigFormat.NATIVE && nativeFallback != null) {
            format = nativeFallback;
        }
        if (!format.isManagedByCoffeeLib()) {
            throw new ConfigException("Format " + format + " is not managed by CoffeeLib: " + type.getName());
        }

        DefaultConfigManager manager = managerFor(format);
        T instance = manager.load(type);

        Path file = manager.fileOf(type);
        // Named by its path relative to the base directory, so a config in a subdirectory
        // stays distinguishable from another one with the same file name.
        String name = ConfigFileNames.identifier(baseDirectory, file);
        requireNotAlreadyLoaded(name, file, type);

        Entry entry = new Entry(name, file, instance, manager);
        entry.rememberContents();
        entries.add(entry);

        ConfigWatcher running = watcher;
        if (running != null) {
            running.watch(file.getParent());
        }
        return instance;
    }

    /**
     * Two configs resolving to the same file would both reload it and both
     * write it, each overwriting the other's values, so loading one twice is
     * rejected rather than left to produce a confusing duplicate in
     * {@link #managedFiles()}.
     */
    private void requireNotAlreadyLoaded(String name, Path file, Class<?> type) {
        for (Entry entry : entries) {
            if (entry.file().equals(file)) {
                throw new ConfigException("Config file '" + name + "' is already loaded by "
                        + entry.instance().getClass().getName() + "; " + type.getName()
                        + " would write the same file");
            }
        }
    }

    /** Whether {@code config} is an instance this service loaded. */
    public boolean owns(Object config) {
        return findByInstance(config) != null;
    }

    /** Re-reads the file backing {@code config}, an instance {@link #load} returned. */
    public void reload(Object config) {
        reload(requireEntry(config));
    }

    /** Writes {@code config}'s current in-memory state back to its file. */
    public void save(Object config) {
        Entry entry = requireEntry(config);
        synchronized (entry) {
            entry.manager().save(config);
            entry.rememberContents();
        }
    }

    @Override
    public List<String> managedFiles() {
        List<String> names = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            names.add(entry.fileName());
        }
        return List.copyOf(names);
    }

    @Override
    public boolean reload(String fileName) {
        Entry entry = findByName(fileName);
        if (entry == null) {
            return false;
        }
        reload(entry);
        return true;
    }

    /**
     * Resolves what an operator typed: the name {@link #managedFiles()} reports
     * ({@code "mymod/server.yml"}), or just the file name when exactly one
     * managed config has it.
     * <p>
     * The bare name is a convenience, not a guess — with two configs called
     * {@code server.yml} in different subdirectories it fails instead of
     * reloading whichever happened to be registered first, because silently
     * acting on the wrong file is worse than an error telling you to be
     * specific.
     */
    private Entry findByName(String fileName) {
        for (Entry entry : entries) {
            if (entry.fileName().equalsIgnoreCase(fileName)) {
                return entry;
            }
        }

        List<Entry> byFileName = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.file().getFileName().toString().equalsIgnoreCase(fileName)) {
                byFileName.add(entry);
            }
        }
        if (byFileName.size() > 1) {
            List<String> candidates = new ArrayList<>();
            for (Entry entry : byFileName) {
                candidates.add(entry.fileName());
            }
            throw new ConfigException("'" + fileName + "' matches more than one managed config ("
                    + String.join(", ", candidates) + "); name the one you mean");
        }
        return byFileName.isEmpty() ? null : byFileName.get(0);
    }

    @Override
    public int reloadAll() {
        int reloaded = 0;
        for (Entry entry : entries) {
            try {
                reload(entry);
                reloaded++;
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "[CoffeeLib/" + ownerId + "] Could not reload " + entry.fileName(), e);
            }
        }
        return reloaded;
    }

    @Override
    public synchronized boolean startHotReload() {
        if (watcher != null || entries.isEmpty()) {
            return false;
        }
        ConfigWatcher started;
        try {
            started = new ConfigWatcher();
        } catch (IOException e) {
            throw new ConfigException("Could not start the config watcher for " + baseDirectory, e);
        }
        for (Entry entry : entries) {
            started.watch(entry.file().getParent());
        }
        started.start();
        watcher = started;
        return true;
    }

    @Override
    public synchronized boolean stopHotReload() {
        ConfigWatcher running = watcher;
        if (running == null) {
            return false;
        }
        watcher = null;
        running.shutdown();
        return true;
    }

    @Override
    public boolean isHotReloadRunning() {
        return watcher != null;
    }

    @Override
    public void addReloadListener(Consumer<Object> listener) {
        listeners.add(listener);
    }

    private void reload(Entry entry) {
        Object instance;
        synchronized (entry) {
            entry.manager().reload(entry.instance());
            entry.rememberContents();
            instance = entry.instance();
        }
        for (Consumer<Object> listener : listeners) {
            try {
                listener.accept(instance);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "[CoffeeLib/" + ownerId + "] Reload listener failed for "
                        + entry.fileName(), e);
            }
        }
    }

    private DefaultConfigManager managerFor(ConfigFormat format) {
        return managers.computeIfAbsent(format, f -> {
            ConfigBackend backend = ConfigBackends.of(f);
            return new DefaultConfigManager(baseDirectory, backend);
        });
    }

    private Entry requireEntry(Object config) {
        Entry entry = findByInstance(config);
        if (entry == null) {
            throw new ConfigException("Not a config instance produced by this manager: "
                    + config.getClass().getName());
        }
        return entry;
    }

    private Entry findByInstance(Object config) {
        for (Entry entry : entries) {
            if (entry.instance() == config) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Reloads entries whose file changed on disk.
     * <p>
     * Every load/reload/save writes the file back out (that is how defaults
     * and newly added keys get persisted), which itself fires a change event.
     * Rather than guessing with timers, each entry remembers the bytes it last
     * wrote and an event whose contents match them is ignored — so our own
     * writes cannot loop, while a genuine edit always gets through even if it
     * lands milliseconds after one.
     */
    private final class ConfigWatcher extends Thread {

        private final java.nio.file.WatchService watchService;
        private final List<Path> watched = new CopyOnWriteArrayList<>();
        private volatile boolean running = true;

        private ConfigWatcher() throws IOException {
            super("CoffeeLib-config-watcher/" + ownerId);
            setDaemon(true);
            this.watchService = baseDirectory.getFileSystem().newWatchService();
        }

        private void watch(Path directory) {
            if (directory == null || watched.contains(directory)) {
                return;
            }
            try {
                Files.createDirectories(directory);
                directory.register(watchService,
                        java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                        java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
                watched.add(directory);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "[CoffeeLib/" + ownerId + "] Could not watch " + directory, e);
            }
        }

        @Override
        public void run() {
            while (running) {
                java.nio.file.WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                    return;
                }

                // Coalesce the burst an editor's save-and-truncate produces, and give a
                // slow writer time to finish before reading the file.
                key.pollEvents();
                key.reset();
                try {
                    Thread.sleep(SETTLE_MILLIS);
                } catch (InterruptedException e) {
                    return;
                }
                drainPending();

                if (!running) {
                    return;
                }
                reloadChanged();
            }
        }

        /** Throws away events that arrived while we were settling — they are about the same save. */
        private void drainPending() {
            java.nio.file.WatchKey pending;
            while ((pending = watchService.poll()) != null) {
                pending.pollEvents();
                pending.reset();
            }
        }

        private void reloadChanged() {
            for (Entry entry : entries) {
                if (!entry.contentsChanged()) {
                    continue;
                }
                try {
                    ManagedConfigService.this.reload(entry);
                    LOGGER.info("[CoffeeLib/" + ownerId + "] Hot-reloaded " + entry.fileName());
                } catch (RuntimeException e) {
                    // A half-saved or invalid file must not kill the watcher: keep the last
                    // good values in memory, say why, and pick the next change up.
                    LOGGER.log(Level.WARNING, "[CoffeeLib/" + ownerId + "] Hot reload of "
                            + entry.fileName() + " failed, keeping the previous values", e);
                    entry.rememberContents();
                }
            }
        }

        private void shutdown() {
            running = false;
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Closing the watch service failed", e);
            }
            interrupt();
        }
    }

    /** A single managed config: its file, the live instance, and the manager that speaks its format. */
    private static final class Entry {

        private final String fileName;
        private final Path file;
        private final Object instance;
        private final DefaultConfigManager manager;
        private volatile byte[] lastContents = new byte[0];

        private Entry(String fileName, Path file, Object instance, DefaultConfigManager manager) {
            this.fileName = fileName;
            this.file = file;
            this.instance = instance;
            this.manager = manager;
        }

        private String fileName() {
            return fileName;
        }

        private Path file() {
            return file;
        }

        private Object instance() {
            return instance;
        }

        private DefaultConfigManager manager() {
            return manager;
        }

        private void rememberContents() {
            lastContents = readContents();
        }

        private boolean contentsChanged() {
            return !Arrays.equals(lastContents, readContents());
        }

        private byte[] readContents() {
            try {
                return Files.exists(file) ? Files.readAllBytes(file) : new byte[0];
            } catch (IOException e) {
                return new byte[0];
            }
        }
    }
}
