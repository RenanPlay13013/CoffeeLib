package net.loyalnetwork.coffeelib.core.economy;

import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.economy.CurrencyProvider;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Which owner provides which currency, shared by every mod and plugin on the
 * library. It stores providers and listeners, never balances.
 */
public final class EconomyRegistry {

    private static final Logger LOGGER = Logger.getLogger(EconomyRegistry.class.getName());
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");

    /** A provider and the owner that registered it. */
    record Registration(String ownerId, CurrencyProvider provider) {
    }

    private record Listener(String ownerId, Consumer<Currency> callback) {
    }

    private final Map<String, Registration> providers = new ConcurrentHashMap<>();
    private final Map<String, RegistryCurrency> handles = new ConcurrentHashMap<>();
    private final Map<String, List<Listener>> listeners = new ConcurrentHashMap<>();

    public void register(String ownerId, CurrencyProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = requireValid(provider.id());
        Registration registration = new Registration(ownerId, provider);
        Registration existing = providers.putIfAbsent(id, registration);
        if (existing != null && !existing.ownerId().equals(ownerId)) {
            throw new CoffeeLibException("Currency '" + id + "' is already provided by " + existing.ownerId()
                    + "; " + ownerId + " cannot provide it too");
        }
        if (existing != null) {
            providers.put(id, registration); // the same owner replacing its own provider
        }
        Currency handle = currency(id);
        for (Listener listener : listeners.getOrDefault(id, List.of())) {
            notify(listener, handle);
        }
    }

    public void unregister(String ownerId, String currencyId) {
        providers.computeIfPresent(normalize(currencyId),
                (id, registration) -> registration.ownerId().equals(ownerId) ? null : registration);
    }

    /** Everything {@code ownerId} left here: its providers and its listeners. */
    public void removeOwner(String ownerId) {
        providers.values().removeIf(registration -> registration.ownerId().equals(ownerId));
        listeners.values().forEach(list -> list.removeIf(listener -> listener.ownerId().equals(ownerId)));
    }

    /** The handle for {@code currencyId}: one per id, valid whether or not anyone provides it. */
    public Currency currency(String currencyId) {
        String id = requireValid(currencyId);
        return handles.computeIfAbsent(id, key -> new RegistryCurrency(key, this));
    }

    public Set<String> currencies() {
        return new TreeSet<>(providers.keySet());
    }

    public void onAvailable(String ownerId, String currencyId, Consumer<Currency> callback) {
        String id = requireValid(currencyId);
        Listener listener = new Listener(ownerId, Objects.requireNonNull(callback, "callback"));
        listeners.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(listener);
        if (providers.containsKey(id)) {
            notify(listener, currency(id));
        }
    }

    Optional<Registration> registration(String currencyId) {
        return Optional.ofNullable(providers.get(currencyId));
    }

    private static void notify(Listener listener, Currency handle) {
        try {
            listener.callback().accept(handle);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] A listener of " + listener.ownerId() + " failed for currency '"
                    + handle.id() + "'", e);
        }
    }

    static String normalize(String id) {
        return id == null ? null : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireValid(String currencyId) {
        String id = normalize(currencyId);
        if (id == null || !ID.matcher(id).matches()) {
            throw new CoffeeLibException("Currency id '" + currencyId + "' must be lower case letters, digits, "
                    + "'_' or '-', such as 'coins'");
        }
        return id;
    }
}
