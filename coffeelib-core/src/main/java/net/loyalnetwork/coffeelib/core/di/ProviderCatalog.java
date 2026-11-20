package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.annotation.Provide;
import net.loyalnetwork.coffeelib.api.di.exception.AmbiguousProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.ServiceException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Owner-local catalog: validates an entire provider host before publishing anything. */
final class ProviderCatalog {
    private final Map<Class<?>, List<ProviderEntry>> providers = new HashMap<>();

    void register(Object providerHost) {
        java.util.Objects.requireNonNull(providerHost, "providerHost");
        // Validate every provider before publishing any: a malformed method must not
        // leave an incomplete registration visible to a later wire().
        Map<Class<?>, List<ProviderEntry>> staged = new HashMap<>();
        for (Method method : providerHost.getClass().getDeclaredMethods()) {
            Provide provide = method.getAnnotation(Provide.class);
            if (provide == null) continue;
            if (method.getParameterCount() != 0) {
                throw new ServiceException("@Provide method '" + method.getName() + "' on "
                        + providerHost.getClass().getName() + " must take no arguments");
            }
            if (method.getReturnType() == void.class || method.getReturnType().isPrimitive()) {
                throw new ServiceException("@Provide method '" + method.getName() + "' on "
                        + providerHost.getClass().getName() + " must return a reference value");
            }
            method.setAccessible(true);
            staged.computeIfAbsent(method.getReturnType(), type -> new ArrayList<>())
                    .add(new ProviderEntry(provide.value(), method, providerHost));
        }
        staged.forEach((type, entries) -> providers.computeIfAbsent(type, key -> new ArrayList<>()).addAll(entries));
    }

    ProviderEntry resolveEntry(Class<?> type, String name) {
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
