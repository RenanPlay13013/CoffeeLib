package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.ServiceContainer;

/**
 * An explicit owner-local DI facade. Provider discovery and receiver wiring are
 * separate responsibilities; neither requires Minecraft/platform types.
 */
public final class DefaultServiceContainer implements ServiceContainer {
    private final ProviderCatalog providers = new ProviderCatalog();

    @Override
    public synchronized void register(Object providerHost) {
        java.util.Objects.requireNonNull(providerHost, "providerHost");
        providers.register(providerHost);
    }

    @Override
    public synchronized void wire(Object receiverHost) {
        InjectionPlan.prepare(receiverHost, providers).apply();
    }
}
