package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import net.loyalnetwork.coffeelib.api.permission.PermissionManager;
import net.loyalnetwork.coffeelib.api.permission.annotation.Permission;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.loyalnetwork.coffeelib.api.event.EventSubscription;

/**
 * One owner's {@link PermissionManager} over the shared node registry and a
 * platform's resolver. Platforms that also have to tell their own server
 * about a node - Paper - override {@link #onDeclared}.
 */
public class DefaultPermissionManager implements PermissionManager {

    private final String ownerId;
    private final PermissionResolver resolver;
    private final java.util.function.BiFunction<Object, String, Integer> numericProvider;

    public DefaultPermissionManager(String ownerId, PermissionResolver resolver) {
        this(ownerId, resolver, (subject, prefix) -> null);
    }

    public DefaultPermissionManager(String ownerId, PermissionResolver resolver,
                                    java.util.function.BiFunction<Object, String, Integer> numericProvider) {
        this.ownerId = java.util.Objects.requireNonNull(ownerId, "ownerId");
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
        this.numericProvider = java.util.Objects.requireNonNull(numericProvider, "numericProvider");
    }

    @Override
    public void declare(String node, PermissionDefault def, String description) {
        resolver.nodes().declare(ownerId, node, def, description);
        onDeclared(node, def, description == null ? "" : description);
    }

    @Override
    public void declare(Class<?> holder) {
        // Read every field before declaring any, so one bad field leaves nothing half-declared.
        Map<String, Permission> found = new LinkedHashMap<>();
        for (Field field : holder.getDeclaredFields()) {
            Permission permission = field.getAnnotation(Permission.class);
            if (permission == null) {
                continue;
            }
            String where = holder.getName() + "." + field.getName();
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || field.getType() != String.class) {
                throw new CoffeeLibException("@Permission field " + where + " must be a static String holding the node");
            }
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                throw new CoffeeLibException("Could not read @Permission field " + where, e);
            }
            if (!(value instanceof String node) || node.isEmpty()) {
                throw new CoffeeLibException("@Permission field " + where + " holds no node");
            }
            PermissionNodes.requireValid(node);
            found.put(node, permission);
        }
        if (found.isEmpty()) {
            throw new CoffeeLibException(holder.getName() + " declares no @Permission fields");
        }
        found.forEach((node, permission) -> declare(node, permission.def(), permission.description()));
    }

    @Override
    public boolean has(Object subject, String node) {
        return resolver.has(subject, node);
    }

    @Override
    public EventSubscription onChange(Consumer<UUID> listener) {
        return SharedPermissionChanges.subscribe(ownerId, listener);
    }

    @Override
    public void declareRange(String prefix, int first, int last) {
        PermissionNumbers.requirePrefix(prefix);
        if (first < 0 || last < first || (long) last - first >= 10_000L) {
            throw new CoffeeLibException("Numeric permission range must contain 1..10000 nonnegative values");
        }
        for (long i = first; i <= last; i++) {
            declare(prefix + "." + i, PermissionDefault.NOBODY, "Numeric permission level " + i);
        }
    }

    @Override
    public int number(Object subject, String prefix, int fallback) {
        PermissionNumbers.requirePrefix(prefix);
        Integer external = numericProvider.apply(subject, prefix);
        if (external != null) return external;
        return PermissionNumbers.highestDeclared(prefix, declared(), node -> has(subject, node), fallback);
    }

    @Override
    public Set<String> declared() {
        return resolver.nodes().declaredBy(ownerId);
    }

    /** Hook for platforms that register nodes with the server too. Does nothing here. */
    protected void onDeclared(String node, PermissionDefault def, String description) {
    }
}
