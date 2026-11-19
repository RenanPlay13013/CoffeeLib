package net.loyalnetwork.coffeelib.api.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;

/**
 * Who can see whom, shared by every mod and plugin on CoffeeLib.
 * <p>
 * A vanish mod registers its rule once, and every CoffeeLib command that takes
 * a player respects it: a hidden player is left out of tab completion and,
 * typed by name, reads as "not found" - the same message as a name nobody has,
 * so the reply gives nothing away.
 * <pre>{@code
 * CoffeeLib.players().visibility((viewer, target) ->
 *         !Vanish.isVanished((ServerPlayer) target) || permissions.has(viewer, "vanish.see"));
 * }</pre>
 * Every rule must allow it for a player to be visible. A rule that throws is
 * logged and counts as allowing, so a bug in one mod hides no one.
 */
public interface PlayerDirectory {

    /**
     * Adds a rule. The subscription's {@code eventType()} is
     * {@code PlayerVisibility.class}; {@code unsubscribe()} removes the rule.
     */
    EventSubscription visibility(PlayerVisibility rule);

    /**
     * Whether {@code viewer} may see {@code target}. A {@code null} viewer is
     * the console, which sees everyone; so does a player looking at themselves.
     */
    boolean canSee(Object viewer, Object target);
}
