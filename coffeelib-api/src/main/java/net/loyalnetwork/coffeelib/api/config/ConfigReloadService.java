package net.loyalnetwork.coffeelib.api.config;

import java.util.List;
import java.util.function.Consumer;

/**
 * Operator-facing control over the configs an owner has loaded in a format
 * CoffeeLib manages itself (anything but {@link ConfigFormat#NATIVE}).
 * <p>
 * Exists because a CoffeeLib-managed format deliberately does <em>not</em>
 * hand reloading off to the platform: on Forge-like platforms the loader
 * only re-reads a config when it feels like it (on its own
 * {@code ModConfigEvent}, after mod construction), and never at all for a
 * file it doesn't know about. A managed config instead offers both halves
 * explicitly — {@linkplain #reloadAll() pull} on demand, or
 * {@linkplain #startHotReload() push} from a file watcher — and the platform
 * module wires them to {@code /coffeelib config reload} and
 * {@code /coffeelib config hotreload start|stop} so an operator can use them
 * without a server restart.
 * <p>
 * {@link ConfigFormat#NATIVE} configs are not covered: the platform owns
 * them, and the methods here simply ignore them.
 * <p>
 * Implementations are thread-safe — hot reload runs on its own thread, while
 * commands arrive on the server thread.
 */
public interface ConfigReloadService {

    /**
     * Names of the config files this service manages (file name plus
     * extension, e.g. {@code "server.yml"}), in load order. Native configs
     * are not listed.
     */
    List<String> managedFiles();

    /**
     * Re-reads one managed file by the name {@link #managedFiles()} reports,
     * updating the live config instance in place.
     *
     * @return {@code false} if no managed config goes by that name
     * @throws net.loyalnetwork.coffeelib.api.config.exception.ConfigException
     *         if the file is now unparseable or fails validation — the
     *         instance keeps whatever it held before the attempt for every
     *         field not yet applied
     */
    boolean reload(String fileName);

    /**
     * Re-reads every managed file. A file that fails does not stop the rest;
     * the count returned is how many reloaded cleanly.
     *
     * @return how many files reloaded successfully
     */
    int reloadAll();

    /**
     * Starts watching the managed files and reloads each one as it changes
     * on disk. Idempotent — calling it while already running is a no-op.
     *
     * @return {@code true} if this call started the watcher, {@code false} if
     *         it was already running or there is nothing managed to watch
     */
    boolean startHotReload();

    /**
     * Stops the watcher started by {@link #startHotReload()}. Managed
     * configs stay loaded and {@link #reload(String)} keeps working.
     *
     * @return {@code true} if this call stopped a running watcher
     */
    boolean stopHotReload();

    /** Whether the file watcher is currently running. */
    boolean isHotReloadRunning();

    /**
     * Registers a callback invoked with the config instance after it is
     * successfully re-read, by either a manual reload or the watcher. Lets a
     * mod react to changed values (re-open a connection, re-register a
     * listener) instead of only seeing them on next read.
     * <p>
     * Hot-reload callbacks run on the watcher thread, not the server thread —
     * schedule onto the platform's thread yourself if what you do needs it.
     */
    void addReloadListener(Consumer<Object> listener);
}
