package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.ServiceContainer;
import net.loyalnetwork.coffeelib.api.di.annotation.Provide;
import net.loyalnetwork.coffeelib.api.di.annotation.Receive;
import net.loyalnetwork.coffeelib.api.di.exception.AmbiguousProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultServiceContainerTest {

    static final class Service {
    }

    static final class SingleProvider {
        @Provide
        Service service() {
            return new Service();
        }
    }

    static final class NamedProviders {
        @Provide("a")
        Service a() {
            return new Service();
        }

        @Provide("b")
        Service b() {
            return new Service();
        }
    }

    static final class UnnamedReceiver {
        @Receive
        Service service;
    }

    static final class NamedReceiver {
        @Receive("a")
        Service service;
    }

    static final class UnknownNameReceiver {
        @Receive("c")
        Service service;
    }

    static final class CountingProvider {
        int callCount = 0;

        @Provide
        Service service() {
            callCount++;
            return new Service();
        }
    }

    @Test
    void wiresTheOnlyProviderWithNoNameNeeded() {
        ServiceContainer container = new DefaultServiceContainer();
        container.register(new SingleProvider());

        UnnamedReceiver receiver = new UnnamedReceiver();
        container.wire(receiver);

        assertNotNull(receiver.service);
    }

    @Test
    void namedReceiverPicksItsMatchingProvider() {
        ServiceContainer container = new DefaultServiceContainer();
        container.register(new NamedProviders());

        NamedReceiver receiver = new NamedReceiver();
        container.wire(receiver);

        assertNotNull(receiver.service);
    }

    @Test
    void unnamedReceiverWithMultipleProvidersIsAmbiguous() {
        ServiceContainer container = new DefaultServiceContainer();
        container.register(new NamedProviders());

        AmbiguousProviderException exception = assertThrows(AmbiguousProviderException.class,
                () -> container.wire(new UnnamedReceiver()));

        assertEquals(Service.class, exception.getType());
        assertTrue(exception.getCandidateNames().containsAll(java.util.List.of("a", "b")));
    }

    @Test
    void wiringWithNoProviderRegisteredFailsAtWireTime() {
        ServiceContainer container = new DefaultServiceContainer();

        assertThrows(MissingProviderException.class, () -> container.wire(new UnnamedReceiver()));
    }

    @Test
    void namedReceiverWithNoMatchingProviderFails() {
        ServiceContainer container = new DefaultServiceContainer();
        container.register(new NamedProviders());

        MissingProviderException exception = assertThrows(MissingProviderException.class,
                () -> container.wire(new UnknownNameReceiver()));

        assertEquals("c", exception.getRequestedName());
    }

    @Test
    void wiringBeforeRegisteringFailsExplicitlyRatherThanDeferring() {
        ServiceContainer container = new DefaultServiceContainer();
        UnnamedReceiver receiver = new UnnamedReceiver();

        // wire() before the matching register() call fails immediately — there is no lazy or
        // deferred resolution that would let this succeed once the provider shows up later.
        assertThrows(MissingProviderException.class, () -> container.wire(receiver));
        assertNull(receiver.service, "the failed wire() call must not have set the field");

        // A later wire() call, made after registering, resolves normally — the earlier failure
        // wasn't cached against the receiver or the container.
        container.register(new SingleProvider());
        container.wire(receiver);
        assertNotNull(receiver.service);
    }

    @Test
    void providerRunsAtMostOnceAndResultIsShared() {
        ServiceContainer container = new DefaultServiceContainer();
        CountingProvider provider = new CountingProvider();
        container.register(provider);

        UnnamedReceiver first = new UnnamedReceiver();
        UnnamedReceiver second = new UnnamedReceiver();
        container.wire(first);
        container.wire(second);

        assertEquals(1, provider.callCount);
        assertSame(first.service, second.service);
    }
}
