package net.loyalnetwork.coffeelib.core.permission;

import java.util.UUID;

/**
 * Asks LuckPerms, when it is installed, without CoffeeLib depending on it.
 * <p>
 * Nothing here names a LuckPerms type: the API is found by name and called
 * reflectively (see {@link Access} for why), so on a server without LuckPerms
 * nothing is loaded and nothing fails to link, and CoffeeLib needs no
 * LuckPerms dependency at all, not even at compile time.
 */
public final class LuckPermsBridge {

    private static final String API_CLASS = "net.luckperms.api.LuckPermsProvider";

    private static volatile Boolean present;

    private LuckPermsBridge() {
    }

    /**
     * Whether the LuckPerms API is visible. A positive answer is cached; a
     * negative one is not, because the first check can run before the thread
     * that can see LuckPerms - the server thread - is the one asking.
     */
    public static boolean isPresent() {
        if (Boolean.TRUE.equals(present)) {
            return true;
        }
        boolean found = locate() != null;
        if (found) {
            present = true;
        }
        return found;
    }

    /**
     * LuckPerms' answer for {@code player} and {@code node}: {@code true} or
     * {@code false} when it decides, {@code null} when it has no opinion - no
     * LuckPerms, LuckPerms not started yet, the user not loaded, or the node
     * neither granted nor denied.
     */
    public static Boolean check(UUID player, String node) {
        if (player == null || !isPresent()) {
            return null;
        }
        try {
            return Access.check(player, node);
        } catch (IllegalStateException e) {
            // LuckPermsProvider.get() before LuckPerms has enabled itself.
            return null;
        }
    }

    /**
     * Calls LuckPerms reflectively, through whichever class loader can see it.
     * <p>
     * Not linked statically, because on Forge-like platforms LuckPerms is a
     * separate mod in a module layer that CoffeeLib's classes do not always
     * read: in a NeoForge dev run CoffeeLib's core sits on the plain
     * classpath, and a static reference to {@code LuckPermsProvider} there
     * fails to resolve even though LuckPerms is loaded and is the active
     * permission handler. The server thread's context class loader is the
     * game layer's, which sees every mod, so looking LuckPerms up through it
     * works in a dev run and in production alike.
     */
    private static final class Access {

        private static Boolean check(UUID player, String node) {
            try {
                Class<?> provider = locate();
                if (provider == null) {
                    return null;
                }
                Object luckPerms = provider.getMethod("get").invoke(null);
                Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
                Object user = method(userManager, "getUser", UUID.class).invoke(userManager, player);
                if (user == null) {
                    return null;
                }
                Object cached = method(user, "getCachedData").invoke(user);
                Object permissions = method(cached, "getPermissionData").invoke(cached);
                Object tristate = method(permissions, "checkPermission", String.class).invoke(permissions, node);
                return switch (((Enum<?>) tristate).name()) {
                    case "TRUE" -> Boolean.TRUE;
                    case "FALSE" -> Boolean.FALSE;
                    default -> null;
                };
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof IllegalStateException notReady) {
                    throw notReady;
                }
                return null;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
                return null;
            }
        }

        /** Public interface methods, looked up on their interface so a non-public implementation class works. */
        private static java.lang.reflect.Method method(Object target, String name, Class<?>... parameters)
                throws NoSuchMethodException {
            for (Class<?> type : target.getClass().getInterfaces()) {
                try {
                    return type.getMethod(name, parameters);
                } catch (NoSuchMethodException ignored) {
                    // try the next interface
                }
            }
            java.lang.reflect.Method method = target.getClass().getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        }
    }

    /** {@code LuckPermsProvider}, through the context class loader first, then CoffeeLib's own. */
    private static Class<?> locate() {
        for (ClassLoader loader : new ClassLoader[]{Thread.currentThread().getContextClassLoader(),
                LuckPermsBridge.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(API_CLASS, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // not visible from this loader
            }
        }
        return null;
    }
}
