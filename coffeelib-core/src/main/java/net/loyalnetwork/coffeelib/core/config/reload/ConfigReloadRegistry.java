package net.loyalnetwork.coffeelib.core.config.reload;

import net.loyalnetwork.coffeelib.api.config.ConfigReloadService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets the loader-side {@code /coffeelib config} command find the reload
 * services of mods it has no reference to.
 * <p>
 * This is the one process-wide piece in CoffeeLib, and it exists for exactly
 * that reason: a command runs inside the CoffeeLib jar, while the services it
 * must act on were created by other mods that never hand CoffeeLib anything.
 * It stores no config state and shares nothing between owners — values are
 * {@link WeakReference}s keyed by owner id, so a mod's service is collectable
 * the moment the mod drops it, and one owner can never reach another's
 * configs through it.
 * <p>
 * {@link ConfigReloadService} implementations are thread-safe, and so is
 * this registry.
 */
public final class ConfigReloadRegistry {

    private static final Map<String, List<WeakReference<ConfigReloadService>>> SERVICES = new ConcurrentHashMap<>();

    private ConfigReloadRegistry() {
    }

    /** Registers {@code service} under {@code ownerId} (a plugin or mod id). */
    public static void register(String ownerId, ConfigReloadService service) {
        // compute(), not computeIfAbsent() + add(): the list is also mutated by the
        // readers below while they prune dead references, and only the map's own
        // per-key lock makes "find or create, then append" a single step.
        SERVICES.compute(ownerId, (id, registered) -> {
            List<WeakReference<ConfigReloadService>> target =
                    registered != null ? registered : new ArrayList<>();
            synchronized (target) {
                target.add(new WeakReference<>(service));
            }
            return target;
        });
    }

    /** Drops {@code service}, for an owner shutting down before the GC gets to it. */
    public static void unregister(String ownerId, ConfigReloadService service) {
        List<WeakReference<ConfigReloadService>> registered = SERVICES.get(ownerId);
        if (registered == null) {
            return;
        }
        synchronized (registered) {
            registered.removeIf(reference -> {
                ConfigReloadService held = reference.get();
                return held == null || held == service;
            });
        }
    }

    /** Owner ids with at least one live service, sorted for stable command suggestions. */
    public static Set<String> owners() {
        return all().keySet();
    }

    /** Live services registered under {@code ownerId}, or an empty list. */
    public static List<ConfigReloadService> of(String ownerId) {
        List<WeakReference<ConfigReloadService>> registered = SERVICES.get(ownerId);
        if (registered == null) {
            return List.of();
        }
        return live(registered);
    }

    /** Snapshot of every live service by owner, dead references pruned as it is built. */
    public static Map<String, List<ConfigReloadService>> all() {
        Map<String, List<ConfigReloadService>> snapshot = new TreeMap<>();
        for (Map.Entry<String, List<WeakReference<ConfigReloadService>>> entry : SERVICES.entrySet()) {
            List<ConfigReloadService> services = live(entry.getValue());
            if (!services.isEmpty()) {
                snapshot.put(entry.getKey(), services);
            }
        }
        return new LinkedHashMap<>(snapshot);
    }

    /** Live services in {@code registered}, dropping references the GC has already cleared. */
    private static List<ConfigReloadService> live(List<WeakReference<ConfigReloadService>> registered) {
        List<ConfigReloadService> services = new ArrayList<>(registered.size());
        synchronized (registered) {
            Iterator<WeakReference<ConfigReloadService>> iterator = registered.iterator();
            while (iterator.hasNext()) {
                ConfigReloadService service = iterator.next().get();
                if (service == null) {
                    iterator.remove();
                } else {
                    services.add(service);
                }
            }
        }
        return List.copyOf(services);
    }
}
