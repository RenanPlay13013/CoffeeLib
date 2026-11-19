package net.loyalnetwork.coffeelib.core.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.player.PlayerDirectory;
import net.loyalnetwork.coffeelib.api.player.PlayerVisibility;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one {@link PlayerDirectory} every owner on this CoffeeLib shares, so a
 * vanish mod's rule reaches every other mod's commands.
 */
public final class SharedPlayers implements PlayerDirectory {

    private static final Logger LOGGER = Logger.getLogger(SharedPlayers.class.getName());
    private static final SharedPlayers DIRECTORY = new SharedPlayers();

    private final List<Rule> rules = new CopyOnWriteArrayList<>();

    private SharedPlayers() {
    }

    public static PlayerDirectory directory() {
        return DIRECTORY;
    }

    @Override
    public EventSubscription visibility(PlayerVisibility rule) {
        Rule added = new Rule(Objects.requireNonNull(rule, "rule"));
        rules.add(added);
        return added;
    }

    @Override
    public boolean canSee(Object viewer, Object target) {
        if (viewer == null || viewer == target) {
            return true;
        }
        for (Rule rule : rules) {
            try {
                if (!rule.visibility.canSee(viewer, target)) {
                    return false;
                }
            } catch (RuntimeException e) {
                // A broken rule hides no one.
                LOGGER.log(Level.SEVERE, "[CoffeeLib] A player visibility rule failed; treating the player as visible", e);
            }
        }
        return true;
    }

    private final class Rule implements EventSubscription {
        final PlayerVisibility visibility;

        Rule(PlayerVisibility visibility) {
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
