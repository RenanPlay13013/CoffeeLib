package net.loyalnetwork.coffeelib.core.economy;

import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.economy.CurrencyProvider;
import net.loyalnetwork.coffeelib.api.economy.EconomyManager;

import java.util.Set;
import java.util.function.Consumer;

/** The economy registry every owner shares, and each owner's {@link EconomyManager} on it. */
public final class SharedEconomy {

    private static final EconomyRegistry REGISTRY = new EconomyRegistry();

    private SharedEconomy() {
    }

    public static EconomyRegistry registry() {
        return REGISTRY;
    }

    public static EconomyManager forOwner(String ownerId) {
        return new OwnerEconomy(REGISTRY, ownerId);
    }

    /** Drops what {@code ownerId} registered. Called by the platform when that owner shuts down. */
    public static void cleanUp(String ownerId) {
        REGISTRY.removeOwner(ownerId);
    }

    record OwnerEconomy(EconomyRegistry registry, String ownerId) implements EconomyManager {

        @Override
        public void register(CurrencyProvider provider) {
            registry.register(ownerId, provider);
        }

        @Override
        public void unregister(String currencyId) {
            registry.unregister(ownerId, currencyId);
        }

        @Override
        public void unregisterAll() {
            registry.removeOwner(ownerId);
        }

        @Override
        public Currency currency(String currencyId) {
            return registry.currency(currencyId);
        }

        @Override
        public Set<String> currencies() {
            return registry.currencies();
        }

        @Override
        public void onAvailable(String currencyId, Consumer<Currency> listener) {
            registry.onAvailable(ownerId, currencyId, listener);
        }
    }
}
