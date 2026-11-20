package net.loyalnetwork.coffeelib.core.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.player.PlayerDirectory;
import net.loyalnetwork.coffeelib.api.player.PlayerVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** Shared directory facade; enumeration is an adapter concern, visibility a policy concern. */
public final class SharedPlayers implements PlayerDirectory {
    private static final SharedPlayers DIRECTORY = new SharedPlayers();
    private final VisibilityPolicies policies = new VisibilityPolicies();
    private volatile Supplier<? extends Collection<?>> onlinePlayers;

    private SharedPlayers() { }

    public static PlayerDirectory directory() { return DIRECTORY; }

    public static void configureOnlinePlayers(Supplier<? extends Collection<?>> online,
                                              BiPredicate<Object, Object> nativeVisibility) {
        DIRECTORY.onlinePlayers = Objects.requireNonNull(online, "online");
        DIRECTORY.policies.configure(nativeVisibility);
    }

    @Override public EventSubscription visibility(PlayerVisibility rule) {
        return policies.register(null, rule);
    }

    @Override public EventSubscription visibility(String ownerId, PlayerVisibility rule) {
        if (Objects.requireNonNull(ownerId, "ownerId").isBlank()) {
            throw new IllegalArgumentException("Visibility owner id cannot be blank");
        }
        return policies.register(ownerId, rule);
    }

    public static void releaseOwner(String ownerId) { DIRECTORY.policies.releaseOwner(ownerId); }
    public static void clearAll() {
        DIRECTORY.policies.clear();
        // Release the platform enumerator too: it can capture plugin/server objects.
        DIRECTORY.onlinePlayers = null;
    }

    @Override public boolean canSee(Object viewer, Object target) { return policies.canSee(viewer, target); }

    @Override public List<Object> visibleTo(Object viewer) {
        Supplier<? extends Collection<?>> source = onlinePlayers;
        if (source == null) {
            throw new IllegalStateException("CoffeeLib player directory has not been initialized by the platform");
        }
        Collection<?> online = Objects.requireNonNull(source.get(), "online players collection");
        List<Object> visible = new ArrayList<>();
        for (Object player : online) {
            if (player != null && canSee(viewer, player)) visible.add(player);
        }
        return List.copyOf(visible);
    }
}
