package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.ServiceContainer;
import net.loyalnetwork.coffeelib.api.di.annotation.Provide;
import net.loyalnetwork.coffeelib.api.di.annotation.Receive;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.ServiceException;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.event.annotation.Subscribe;
import net.loyalnetwork.coffeelib.core.event.SharedEventBus;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SharedServicesTest {
    static class Provider {
        final String value;
        Provider(String value) { this.value = value; }
        @Provide String value() { return value; }
    }
    static class Listener {
        @Receive String value;
        final List<String> results = new ArrayList<>();
        @Subscribe void event(Integer i) { results.add(value + i); }
    }
    static class BrokenListener {
        @Receive String value;
        @Receive Long missing;
        @Subscribe void event(Integer i) { }
    }
    static class MissingFirst {
        @Receive Long missing;
        @Receive String value;
    }
    static class BadProviders {
        @Provide String good() { return "ok"; }
        @Provide String bad(int n) { return "bad"; }
    }
    static class InvalidField {
        @Receive final String value = "init";
    }

    @Test void sameOwnerSharesProvidersAndWiresEvents() {
        String owner = "coffeelib-di-owner";
        try {
            ServiceContainer services = SharedServices.forOwner(owner, () -> new Provider("wired:"));
            assertSame(services, SharedServices.forOwner(owner, () -> { throw new AssertionError("initialized twice"); }));
            Listener listener = new Listener();
            EventBus bus = SharedEventBus.forOwner(owner);
            bus.register(listener);
            bus.post(15);
            assertEquals(List.of("wired:15"), listener.results);
        } finally {
            SharedEventBus.unregisterAll(owner);
            SharedServices.releaseOwner(owner);
        }
    }

    @Test void failedListenerRegistrationDoesNotPublishEventListenerOrPartiallyWire() {
        String owner = "coffeelib-di-broken";
        try {
            SharedServices.forOwner(owner, () -> new Provider("ready"));
            BrokenListener broken = new BrokenListener();
            assertThrows(MissingProviderException.class, () -> SharedEventBus.forOwner(owner).register(broken));
            assertNull(broken.value);
            assertEquals(0, SharedEventBus.handlerCount(owner));
            MissingFirst receiver = new MissingFirst();
            assertThrows(MissingProviderException.class, () -> SharedServices.existing(owner).wire(receiver));
            assertNull(receiver.value);
        } finally {
            SharedEventBus.unregisterAll(owner);
            SharedServices.releaseOwner(owner);
        }
    }

    @Test void releaseOwnerMakesReinitializationUseFreshProviders() {
        String owner = "coffeelib-di-reloaded";
        try {
            ServiceContainer original = SharedServices.forOwner(owner, () -> new Provider("old"));
            SharedServices.releaseOwner(owner);
            ServiceContainer replacement = SharedServices.forOwner(owner, () -> new Provider("new"));
            assertTrue(original != replacement);
            Listener listener = new Listener();
            replacement.wire(listener);
            assertEquals("new", listener.value);
        } finally {
            SharedServices.releaseOwner(owner);
        }
    }

    @Test void invalidProvideMethodDoesNotPartiallyRegister() {
        ServiceContainer services = new DefaultServiceContainer();
        assertThrows(ServiceException.class, () -> services.register(new BadProviders()));
        assertThrows(MissingProviderException.class, () -> services.wire(new Listener()));
    }

    @Test void finalFieldsCannotBeInjectedSilently() {
        ServiceContainer services = new DefaultServiceContainer();
        services.register(new Provider("text"));
        assertThrows(ServiceException.class, () -> services.wire(new InvalidField()));
    }
    static class NullProviders {
        @Provide String missing() { return null; }
    }
    static class CommandHandler {
        @Receive Long missing;
        @net.loyalnetwork.coffeelib.api.command.annotation.Command("demo")
        void demo() { }
    }

    @Test void aNullProviderFailsInsteadOfInjectingNull() {
        ServiceContainer services = new DefaultServiceContainer();
        services.register(new NullProviders());
        assertThrows(ServiceException.class, () -> services.wire(new Listener()));
    }

    @Test void commandInjectionErrorsNameTheCommandMethod() {
        String owner = "coffeelib-di-command";
        try {
            SharedServices.forOwner(owner, () -> new Provider("ok"));
            MissingProviderException error = assertThrows(MissingProviderException.class,
                    () -> net.loyalnetwork.coffeelib.core.command.CommandServices.wire(owner, new CommandHandler()));
            assertTrue(error.getMessage().contains("#demo"));
            assertTrue(error.getMessage().contains("missing"));
        } finally {
            SharedServices.releaseOwner(owner);
        }
    }

}
