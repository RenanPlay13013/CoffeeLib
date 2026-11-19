package net.loyalnetwork.coffeelib.api.player;

/**
 * A rule for who can see whom, registered with {@link PlayerDirectory#visibility}
 * - a vanish mod's, typically.
 */
@FunctionalInterface
public interface PlayerVisibility {

    /**
     * Whether {@code viewer} may see {@code target}. Both are the platform's
     * player type ({@code ServerPlayer}, {@code Player}). Never called for the
     * console, which sees everyone, nor for a player looking at themselves.
     */
    boolean canSee(Object viewer, Object target);
}
