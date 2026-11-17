package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderContext;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderResolver;
import net.loyalnetwork.coffeelib.api.placeholder.annotation.Placeholder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a handler's {@code @Placeholder} methods into resolvers, validating
 * the signatures up front so a mistake fails at registration.
 */
final class PlaceholderScanner {

    private PlaceholderScanner() {
    }

    static Map<String, PlaceholderResolver> scan(Object handler) {
        Map<String, PlaceholderResolver> resolvers = new LinkedHashMap<>();
        for (Method method : handler.getClass().getDeclaredMethods()) {
            Placeholder placeholder = method.getAnnotation(Placeholder.class);
            if (placeholder == null) {
                continue;
            }
            String where = handler.getClass().getName() + "#" + method.getName();
            if (Modifier.isStatic(method.getModifiers())) {
                throw new CoffeeLibException("@Placeholder method " + where + " must not be static");
            }
            if (method.getReturnType() == void.class) {
                throw new CoffeeLibException("@Placeholder method " + where + " must return its value");
            }
            method.setAccessible(true);
            if (resolvers.put(placeholder.value(), resolver(handler, method, where)) != null) {
                throw new CoffeeLibException("Two @Placeholder methods in " + handler.getClass().getName()
                        + " claim the key '" + placeholder.value() + "'");
            }
        }
        if (resolvers.isEmpty()) {
            throw new CoffeeLibException(handler.getClass().getName() + " was registered as a placeholder "
                    + "handler but declares no @Placeholder methods");
        }
        return resolvers;
    }

    /**
     * Each parameter is the context, the argument or the player, recognized
     * by type. Declaring a player type means "needs a player of this kind":
     * without one, the method is not called and the placeholder stays as
     * written, which is more honest than handing it a null to guess about.
     */
    private static PlaceholderResolver resolver(Object handler, Method method, String where) {
        Class<?>[] types = method.getParameterTypes();
        Class<?> playerType = null;
        boolean sawContext = false;
        boolean sawArgument = false;
        for (Class<?> type : types) {
            if (type == PlaceholderContext.class) {
                sawContext = requireOnce(sawContext, "PlaceholderContext", where);
            } else if (type == String.class) {
                sawArgument = requireOnce(sawArgument, "String argument", where);
            } else if (type.isPrimitive()) {
                throw new CoffeeLibException("@Placeholder method " + where + " cannot take a " + type.getName());
            } else if (playerType != null) {
                throw new CoffeeLibException("@Placeholder method " + where + " takes two player parameters");
            } else {
                playerType = type;
            }
        }

        Class<?> requiredPlayer = playerType;
        return context -> {
            Object[] arguments = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == PlaceholderContext.class) {
                    arguments[i] = context;
                } else if (types[i] == String.class) {
                    arguments[i] = context.argument();
                } else {
                    Object player = context.player();
                    if (player == null || !requiredPlayer.isInstance(player)) {
                        return null;
                    }
                    arguments[i] = player;
                }
            }
            try {
                Object value = method.invoke(handler, arguments);
                return value == null ? null : value.toString();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new CoffeeLibException("@Placeholder method " + where + " failed", cause);
            } catch (IllegalAccessException e) {
                throw new CoffeeLibException("Could not call @Placeholder method " + where, e);
            }
        };
    }

    private static boolean requireOnce(boolean seen, String what, String where) {
        if (seen) {
            throw new CoffeeLibException("@Placeholder method " + where + " takes more than one " + what);
        }
        return true;
    }
}
