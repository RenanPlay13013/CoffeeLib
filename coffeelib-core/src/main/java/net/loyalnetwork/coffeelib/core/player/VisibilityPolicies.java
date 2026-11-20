package net.loyalnetwork.coffeelib.core.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.player.PlayerVisibility;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fail-closed visibility policy owned by registered plugins, not by the platform directory. */
final class VisibilityPolicies {
    private static final Logger LOG = Logger.getLogger(VisibilityPolicies.class.getName());
    private final List<Rule> rules = new CopyOnWriteArrayList<>();
    private volatile BiPredicate<Object, Object> nativeCheck = (viewer, target) -> true;
    private final AtomicBoolean nativeFailureLogged = new AtomicBoolean();

    void configure(BiPredicate<Object, Object> nativeVisibility) {
        nativeCheck = Objects.requireNonNull(nativeVisibility, "nativeVisibility");
        nativeFailureLogged.set(false);
    }

    EventSubscription register(String owner, PlayerVisibility visibility) {
        Rule rule = new Rule(owner, Objects.requireNonNull(visibility, "rule"));
        rules.add(rule);
        return rule;
    }

    void releaseOwner(String owner) {
        if (owner != null) rules.removeIf(rule -> owner.equals(rule.owner));
    }

    void clear() {
        rules.clear();
        // The platform predicate may capture an old plugin or server classloader.
        nativeCheck = (viewer, target) -> true;
        nativeFailureLogged.set(false);
    }

    boolean canSee(Object viewer, Object target) {
        // The console and player viewing themselves must remain visible.
        if (viewer == null || viewer == target) return true;
        if (!nativeAllows(viewer, target)) return false;
        for (Rule rule : rules) {
            if (!rule.allows(viewer, target)) return false;
        }
        return true;
    }

    private boolean nativeAllows(Object viewer, Object target) {
        try {
            return nativeCheck.test(viewer, target);
        } catch (RuntimeException failure) {
            if (nativeFailureLogged.compareAndSet(false, true)) {
                LOG.log(Level.SEVERE, "[CoffeeLib] Native player visibility check failed; hiding affected players", failure);
            }
            return false;
        }
    }

    private final class Rule implements EventSubscription {
        private final String owner;
        private final PlayerVisibility visibility;
        private final AtomicBoolean failureLogged = new AtomicBoolean();

        Rule(String owner, PlayerVisibility visibility) {
            this.owner = owner;
            this.visibility = visibility;
        }

        boolean allows(Object viewer, Object target) {
            try {
                return visibility.canSee(viewer, target);
            } catch (RuntimeException failure) {
                if (failureLogged.compareAndSet(false, true)) {
                    LOG.log(Level.SEVERE, "[CoffeeLib] Visibility rule for owner '" + owner
                            + "' failed; hiding affected players", failure);
                }
                return false;
            }
        }

        @Override public Class<?> eventType() { return PlayerVisibility.class; }
        @Override public boolean isActive() { return rules.contains(this); }
        @Override public void unsubscribe() { rules.remove(this); }
    }
}
