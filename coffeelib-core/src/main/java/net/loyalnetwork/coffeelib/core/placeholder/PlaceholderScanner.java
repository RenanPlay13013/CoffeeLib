package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderContext;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderResolver;
import net.loyalnetwork.coffeelib.api.placeholder.TextContext;
import net.loyalnetwork.coffeelib.api.placeholder.annotation.Context;
import net.loyalnetwork.coffeelib.api.placeholder.annotation.Placeholder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Turns a handler's {@code @Placeholder} methods into resolvers, validating
 * the signatures up front so a mistake fails at registration.
 */
final class PlaceholderScanner {

    private static final Logger LOGGER = Logger.getLogger(PlaceholderScanner.class.getName());

    /** {@code namespace_key}s already warned about an ambiguous context, so the log says it once. */
    private static final Set<String> WARNED_AMBIGUOUS = ConcurrentHashMap.newKeySet();

    private PlaceholderScanner() {
    }

    /** What a placeholder from {@code @Placeholder} needs: the context types it cannot resolve without. */
    interface Requirements {
        List<Class<?>> contextTypes();
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

    /** How one parameter is filled. */
    private enum Kind { CONTEXT, ARGUMENT, PLAYER, TEXT_CONTEXT_VALUE }

    /**
     * Each parameter is the {@code PlaceholderContext}, the argument, the
     * player, or - marked {@code @Context} - a value of the text's context,
     * recognized by type. Declaring a player or a context value means "needs
     * it": without one, the method is not called and the placeholder stays as
     * written, which is more honest than handing it a null to guess about.
     */
    private static PlaceholderResolver resolver(Object handler, Method method, String where) {
        Parameter[] parameters = method.getParameters();
        Kind[] kinds = new Kind[parameters.length];
        Class<?> playerType = null;
        boolean sawContext = false;
        boolean sawArgument = false;
        List<Class<?>> contextTypes = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            if (parameters[i].isAnnotationPresent(Context.class)) {
                requireContextType(type, where, contextTypes);
                contextTypes.add(type);
                kinds[i] = Kind.TEXT_CONTEXT_VALUE;
            } else if (type == PlaceholderContext.class) {
                sawContext = requireOnce(sawContext, "PlaceholderContext", where);
                kinds[i] = Kind.CONTEXT;
            } else if (type == String.class) {
                sawArgument = requireOnce(sawArgument, "String argument", where);
                kinds[i] = Kind.ARGUMENT;
            } else if (type.isPrimitive()) {
                throw new CoffeeLibException("@Placeholder method " + where + " cannot take a " + type.getName());
            } else if (playerType != null) {
                throw new CoffeeLibException("@Placeholder method " + where + " takes two player parameters");
            } else {
                playerType = type;
                kinds[i] = Kind.PLAYER;
            }
        }
        for (Class<?> contextType : contextTypes) {
            if (playerType != null && (playerType.isAssignableFrom(contextType) || contextType.isAssignableFrom(playerType))) {
                throw new CoffeeLibException("@Placeholder method " + where + " takes its player as a @Context "
                        + "value too (" + contextType.getName() + "); the player is a parameter of its own");
            }
        }

        Class<?> requiredPlayer = playerType;
        List<Class<?>> required = List.copyOf(contextTypes);
        return new ScannedResolver(required, context -> {
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                switch (kinds[i]) {
                    case CONTEXT -> arguments[i] = context;
                    case ARGUMENT -> arguments[i] = context.argument();
                    case PLAYER -> {
                        Object player = context.player();
                        if (player == null || !requiredPlayer.isInstance(player)) {
                            return null;
                        }
                        arguments[i] = player;
                    }
                    case TEXT_CONTEXT_VALUE -> {
                        Class<?> type = parameters[i].getType();
                        TextContext text = context.text();
                        if (text.isAmbiguous(type)) {
                            warnAmbiguous(context, type, where);
                            return null;
                        }
                        Optional<?> value = text.get(type);
                        if (value.isEmpty()) {
                            return null;
                        }
                        arguments[i] = value.get();
                    }
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
        });
    }

    /** Refuses what a {@code @Context} parameter can never be: the error goes to whoever registers. */
    private static void requireContextType(Class<?> type, String where, List<Class<?>> seen) {
        if (type == String.class || type.isPrimitive() || type == PlaceholderContext.class || type == TextContext.class) {
            throw new CoffeeLibException("@Placeholder method " + where + " has a @Context " + type.getName()
                    + "; @Context takes a value of the mod's own type (a Kit, a Crate...)");
        }
        for (Class<?> playerType : SharedPlaceholders.playerTypes()) {
            if (playerType.isAssignableFrom(type)) {
                throw new CoffeeLibException("@Placeholder method " + where + " has a @Context " + type.getName()
                        + "; the player is a plain parameter, not a @Context value");
            }
        }
        if (seen.contains(type)) {
            throw new CoffeeLibException("@Placeholder method " + where + " takes two @Context " + type.getName()
                    + " parameters; a text context holds one value per type");
        }
    }

    private static void warnAmbiguous(PlaceholderContext context, Class<?> type, String where) {
        String key = context.namespace().isEmpty() ? where : context.namespace() + "_" + context.key();
        if (WARNED_AMBIGUOUS.add(key)) {
            LOGGER.warning("[CoffeeLib] Placeholder %" + key + "% wants a @Context " + type.getName()
                    + ", and the text's context holds more than one; it is left unresolved rather than guessing");
        }
    }

    private static boolean requireOnce(boolean seen, String what, String where) {
        if (seen) {
            throw new CoffeeLibException("@Placeholder method " + where + " takes more than one " + what);
        }
        return true;
    }

    /** A {@code @Placeholder} method's resolver, knowing which context types it needs. */
    private record ScannedResolver(List<Class<?>> contextTypes, PlaceholderResolver delegate)
            implements PlaceholderResolver, Requirements {

        @Override
        public String resolve(PlaceholderContext context) {
            return delegate.resolve(context);
        }
    }
}
