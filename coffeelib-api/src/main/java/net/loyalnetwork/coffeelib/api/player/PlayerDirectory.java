package net.loyalnetwork.coffeelib.api.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;

import java.util.List;

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
 * Every rule must allow it for a player to be visible. If a rule (or the
 * native visibility check) throws, CoffeeLib logs the first failure and
 * **denies** that visibility query rather than revealing a hidden player.
 * Console and self-visibility retain their documented bypass behavior.
 */
public interface PlayerDirectory {

    /**
     * Adds a rule. The subscription's {@code eventType()} is
     * {@code PlayerVisibility.class}; {@code unsubscribe()} removes the rule.
     */
    EventSubscription visibility(PlayerVisibility rule);

    /**
     * Registers a visibility rule belonging to an owner (plugin/mod id). This
     * overload allows the platform to release the rule at the owner's unload.
     * Use this for all new plugins; the old overload remains for compatibility.
     *
     * <p>Default throws for alternative PlayerDirectory implementations rather
     * than silently losing the owner lifecycle guarantee.
     */
    default EventSubscription visibility(String ownerId, PlayerVisibility rule) {
        throw new UnsupportedOperationException("Owner-bound visibility rules are not implemented here");
    }

    /**
     * Online players visible to {@code viewer}. The console (null) sees all.
     * Must be called on the server thread because platform player directories
     * and visibility providers can access live game state.
     *
     * <p>Returned list is a snapshot, not a live view. This default keeps
     * existing third-party PlayerDirectory implementations binary compatible.
     */
    default List<Object> visibleTo(Object viewer) {
        throw new UnsupportedOperationException("Online player enumeration is not implemented here");
    }

    /**
     * Whether {@code viewer} may see {@code target}. A {@code null} viewer is
     * the console, which sees everyone; so does a player looking at themselves.
     * A failing visibility rule or platform check is treated as a denial.
     * Call on the platform's server thread.
     */
    boolean canSee(Object viewer, Object target);
}
