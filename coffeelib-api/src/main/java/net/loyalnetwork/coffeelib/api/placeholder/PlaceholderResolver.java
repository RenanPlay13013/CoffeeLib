package net.loyalnetwork.coffeelib.api.placeholder;

/**
 * Produces a placeholder's value.
 * <p>
 * Returning {@code null} means "cannot answer this one" - no player when one
 * is needed, an unknown argument - and leaves the placeholder in the text as
 * written, the way PlaceholderAPI does, rather than replacing it with
 * something misleading. A resolver that throws is treated the same way, and
 * the failure is logged against its owner.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    String resolve(PlaceholderContext context);
}
