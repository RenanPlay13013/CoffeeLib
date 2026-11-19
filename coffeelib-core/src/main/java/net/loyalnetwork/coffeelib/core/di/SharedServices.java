package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.ServiceContainer;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** One injector per owner, shared by that owner's commands and event listeners. */
public final class SharedServices {
    private static final ConcurrentMap<String, ServiceContainer> OWNERS = new ConcurrentHashMap<>();

    private SharedServices() { }

    /** Initializes default providers once per owner, then returns its existing container. */
    public static ServiceContainer forOwner(String ownerId, Supplier<?> platformProviders) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(platformProviders, "platformProviders");
        if (ownerId.isBlank()) throw new IllegalArgumentException("ownerId is blank");
        return OWNERS.computeIfAbsent(ownerId, id -> {
            DefaultServiceContainer container = new DefaultServiceContainer();
            container.register(Objects.requireNonNull(platformProviders.get(), "platformProviders returned null"));
            return container;
        });
    }

    /** Returns null if the owner has not initialized DI; existing direct constructors remain usable. */
    public static ServiceContainer existing(String ownerId) {
        return OWNERS.get(ownerId);
    }

    /** Drops provider hosts and their plugin class loaders after Paper disable. */
    public static void releaseOwner(String ownerId) {
        OWNERS.remove(ownerId);
    }

    /** Used when CoffeeLib itself is unloaded. */
    public static void clearAll() {
        OWNERS.clear();
    }
}
