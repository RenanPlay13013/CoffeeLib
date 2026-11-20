package net.loyalnetwork.coffeelib.api.placeholder.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a {@code @Placeholder} method parameter: the value comes from the text's
 * {@link net.loyalnetwork.coffeelib.api.placeholder.TextContext}, by the
 * parameter's type.
 * <pre>{@code
 * @Placeholder("kit_remaining")                     // %ravenessentials_kit_remaining%
 * String remaining(ServerPlayer player, @Context Kit kit) { ... }
 * }</pre>
 * Without a {@code Kit} in the context - or with two values that are both a
 * {@code Kit} - the method is not called and the placeholder stays as written,
 * the same rule as for the player. Several {@code @Context} parameters of
 * different types are fine; two of the same type, or one on a {@code String},
 * a primitive, {@code PlaceholderContext} or a player type, fail at
 * registration.
 * <p>
 * <b>PlaceholderAPI has no context.</b> On Paper, a placeholder reached through
 * its published PlaceholderAPI expansion - another plugin's text, a scoreboard
 * plugin - is never given one, so a {@code @Context} placeholder never resolves
 * that way. Neither does it in {@code /coffeelib placeholder parse}, which says
 * so when one is left. It resolves where the text is built with a context:
 * {@code TextFormatter#parse(text, player, context)},
 * {@code PlaceholderManager#apply(player, text, context)},
 * {@code Messages#send(recipient, context, ...)} and {@code Menu#context(...)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Context {
}
