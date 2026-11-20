package net.loyalnetwork.coffeelib.core.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.player.PlayerDirectory;
import net.loyalnetwork.coffeelib.api.player.PlayerVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared visibility registry. Platform-specific online-player enumeration and
 * built-in visibility checks are injected by the platform bootstrap; Minecraft
 * classes never cross into core. Registration by owner supports plugin unload.
 */
public final class SharedPlayers implements PlayerDirectory {

    private static final Logger LOGGER = Logger.getLogger(SharedPlayers.class.getName());
    private static final SharedPlayers DIRECTORY = new SharedPlayers();

    private final List<Rule> rules = new CopyOnWriteArrayList<>();
    private volatile Supplier<? extends Collection<?>> onlinePlayers;
    private volatile BiPredicate<Object, Object> platformVisibility = (viewer, target) -> true;
    private final AtomicBoolean nativeFailureLogged = new AtomicBoolean();

    private SharedPlayers() {
    }

    public static PlayerDirectory directory() {
        return DIRECTORY;
    }

    /**
     * Called once by each platform bootstrap. Supplier must enumerate players
     * at call time rather than capture the current server/world instance.
     * Paper's predicate must also respect Player.canSee.
     */
    public static void configureOnlinePlayers(Supplier<? extends Collection<?>> online,
                                              BiPredicate<Object, Object> nativeVisibility) {
        DIRECTORY.onlinePlayers = Objects.requireNonNull(online, "online");
        DIRECTORY.platformVisibility = Objects.requireNonNull(nativeVisibility, "nativeVisibility");
        DIRECTORY.nativeFailureLogged.set(false);
    }

    @Override
    public EventSubscription visibility(PlayerVisibility rule) {
        return add(null, rule);
    }

    @Override
    public EventSubscription visibility(String ownerId, PlayerVisibility rule) {
        if (Objects.requireNonNull(ownerId, "ownerId").isBlank()) {
            throw new IllegalArgumentException("Visibility owner id cannot be blank");
        }
        return add(ownerId, rule);
    }

    private EventSubscription add(String owner, PlayerVisibility visibility) {
        Rule rule = new Rule(owner, Objects.requireNonNull(visibility, "rule"));
        rules.add(rule);
        return rule;
    }

    /** Discards only this owner's subscriptions, including captured plugin instances. */
    public static void releaseOwner(String ownerId) {
        DIRECTORY.rules.removeIf(rule -> Objects.equals(rule.owner, ownerId) && ownerId != null);
    }

    /** Called on CoffeeLib itself being disabled so its static registry cannot pin old plugins. */
    public static void clearAll() {
        DIRECTORY.rules.clear();
    }

    @Override
    public boolean canSee(Object viewer, Object target) {
        if (viewer == null || viewer == target) {
            return true;
        }
        // Privacy-sensitive queries must fail closed. Otherwise an exception in
        // a vanish provider exposes hidden players in autocomplete and commands.
        try {
            if (!platformVisibility.test(viewer, target)) {
                return false;
            }
        } catch (RuntimeException e) {
            if (nativeFailureLogged.compareAndSet(false, true)) {
                LOGGER.log(Level.SEVERE, "[CoffeeLib] Native player visibility check failed; hiding affected players", e);
            }
            return false;
        }
        for (Rule rule : rules) {
            try {
                if (!rule.visibility.canSee(viewer, target)) {
                    return false;
                }
            } catch (RuntimeException e) {
                if (rule.failureLogged.compareAndSet(false, true)) {
                    LOGGER.log(Level.SEVERE, "[CoffeeLib] Visibility rule for owner '"
                            + rule.owner + "' failed; hiding affected players", e);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Object> visibleTo(Object viewer) {
        Supplier<? extends Collection<?>> source = onlinePlayers;
        if (source == null) {
            throw new IllegalStateException("CoffeeLib player directory has not been initialized by the platform");
        }
        Collection<?> online = Objects.requireNonNull(source.get(), "online players collection");
        List<Object> result = new ArrayList<>();
        for (Object player : online) {
            if (player != null && canSee(viewer, player)) {
                result.add(player);
            }
        }
        return List.copyOf(result);
    }

    private final class Rule implements EventSubscription {
        final String owner;
        final PlayerVisibility visibility;
        final AtomicBoolean failureLogged = new AtomicBoolean();

        Rule(String owner, PlayerVisibility visibility) {
            this.owner = owner;
            this.visibility = visibility;
        }

        @Override
        public Class<?> eventType() {
            return PlayerVisibility.class;
        }

        @Override
        public boolean isActive() {
            return rules.contains(this);
        }

        @Override
        public void unsubscribe() {
            rules.remove(this);
        }
    }
}
