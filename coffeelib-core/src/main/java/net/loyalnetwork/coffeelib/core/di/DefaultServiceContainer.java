package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.ServiceContainer;
import net.loyalnetwork.coffeelib.api.di.annotation.Provide;
import net.loyalnetwork.coffeelib.api.di.annotation.Receive;
import net.loyalnetwork.coffeelib.api.di.exception.AmbiguousProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.ServiceException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default {@link ServiceContainer}. Matches providers to receivers by exact
 * type — a provider returning {@code ArrayList} does not satisfy a field
 * declared {@code List}. Widening lookups add ambiguity (which supertype
 * "wins" when several providers are assignable?) for a problem this
 * container isn't trying to solve; declare the field as the exact type the
 * provider returns.
 */
public final class DefaultServiceContainer implements ServiceContainer {

    private final Map<Class<?>, List<ProviderEntry>> providers = new HashMap<>();

    @Override
    public void register(Object providerHost) {
        for (Method method : providerHost.getClass().getDeclaredMethods()) {
            Provide provide = method.getAnnotation(Provide.class);
            if (provide == null) {
                continue;
            }
            if (method.getParameterCount() != 0) {
                throw new ServiceException("@Provide method '" + method.getName() + "' on "
                        + providerHost.getClass().getName() + " must take no arguments");
            }
            if (method.getReturnType() == void.class) {
                throw new ServiceException("@Provide method '" + method.getName() + "' on "
                        + providerHost.getClass().getName() + " must return a value");
            }

            method.setAccessible(true);
            providers.computeIfAbsent(method.getReturnType(), type -> new ArrayList<>())
                    .add(new ProviderEntry(provide.value(), method, providerHost));
        }
    }

    @Override
    public void wire(Object receiverHost) {
        for (Field field : receiverHost.getClass().getDeclaredFields()) {
            Receive receive = field.getAnnotation(Receive.class);
            if (receive == null) {
                continue;
            }
            field.setAccessible(true);

            ProviderEntry entry = resolveEntry(field.getType(), receive.value());
            try {
                field.set(receiverHost, entry.resolve());
            } catch (IllegalAccessException e) {
                throw new ServiceException("Could not set field '" + field.getName() + "' on "
                        + receiverHost.getClass().getName(), e);
            }
        }
    }

    private ProviderEntry resolveEntry(Class<?> type, String name) {
        List<ProviderEntry> candidates = providers.get(type);
        if (candidates == null || candidates.isEmpty()) {
            String requested = name.isEmpty() ? null : name;
            throw new MissingProviderException("No provider registered for " + type.getName(), type, requested);
        }

        if (!name.isEmpty()) {
            for (ProviderEntry candidate : candidates) {
                if (candidate.getName().equals(name)) {
                    return candidate;
                }
            }
            throw new MissingProviderException(
                    "No provider named '" + name + "' registered for " + type.getName(), type, name);
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        List<String> names = candidates.stream()
                .map(candidate -> candidate.getName().isEmpty() ? "(unnamed)" : candidate.getName())
                .collect(Collectors.toList());
        throw new AmbiguousProviderException(
                "Multiple providers registered for " + type.getName() + ": " + names
                        + " — use @Receive(\"name\") to pick one", type, names);
    }
}
