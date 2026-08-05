package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.exception.ServiceException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A single registered {@code @Provide} method. {@code name} is {@code ""}
 * for an unnamed provider — never {@code null}, so lookups can compare
 * without a null check.
 * <p>
 * Resolution is lazy and cached: the provider method isn't invoked until
 * something actually needs it, and after that first call the result is
 * reused for every later {@link #resolve()} — matching the singleton
 * contract {@code @Provide}'s Javadoc promises.
 */
final class ProviderEntry {

    private final String name;
    private final Method method;
    private final Object owner;

    private boolean resolved;
    private Object value;

    ProviderEntry(String name, Method method, Object owner) {
        this.name = name;
        this.method = method;
        this.owner = owner;
    }

    String getName() {
        return name;
    }

    Object resolve() {
        if (!resolved) {
            try {
                value = method.invoke(owner);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new ServiceException("Provider method '" + method.getName() + "' on "
                        + owner.getClass().getName() + " threw while resolving", e);
            }
            resolved = true;
        }
        return value;
    }
}
