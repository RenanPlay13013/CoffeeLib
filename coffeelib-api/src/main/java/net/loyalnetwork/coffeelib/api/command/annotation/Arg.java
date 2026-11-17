package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names an argument, for usage messages ({@code <amount>}) and for the
 * Brigadier node on Forge-like platforms.
 * <p>
 * Not needed when the code is compiled with {@code -parameters}, in which
 * case the parameter's own name is used. Without that flag Java only knows
 * {@code arg0}, {@code arg1}, which is why this exists.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Arg {

    String value();
}
