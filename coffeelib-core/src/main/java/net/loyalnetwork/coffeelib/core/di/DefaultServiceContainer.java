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
    public synchronized void register(Object providerHost) {
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

    @Override
    public synchronized void wire(Object receiverHost) {
        java.util.Objects.requireNonNull(receiverHost, "receiverHost");
        // Resolve all @Receive fields before touching the object; failure at register
        // time must not leave a half-wired handler behind.
        Map<Field, Object> resolved = new java.util.LinkedHashMap<>();
        for (Class<?> type = receiverHost.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Receive receive = field.getAnnotation(Receive.class);
                if (receive == null) continue;
                if (java.lang.reflect.Modifier.isFinal(field.getModifiers())
                        || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    throw new ServiceException("@Receive field '" + field.getName() + "' on "
                            + receiverHost.getClass().getName() + " cannot be static or final");
                }
                field.setAccessible(true);
                ProviderEntry entry;
                try {
                    entry = resolveEntry(field.getType(), receive.value());
                } catch (MissingProviderException e) {
                    throw new MissingProviderException("@Receive field '" + field.getName() + "' on "
                            + receiverHost.getClass().getName() + ": " + e.getMessage(),
                            e.getType(), e.getRequestedName());
                }
                resolved.put(field, entry.resolve());
            }
        }
        for (Map.Entry<Field, Object> field : resolved.entrySet()) {
            try {
                field.getKey().set(receiverHost, field.getValue());
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new ServiceException("Could not set @Receive field '" + field.getKey().getName() + "' on "
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
