package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A player argument that may be left out to mean the sender:
 * <pre>{@code
 * @Command(value = "heal", permission = "crates.heal")
 * void heal(CommandSource source, @OrSelf(others = "crates.heal.others") ServerPlayer target) { ... }
 * }</pre>
 * {@code /heal} heals the sender; {@code /heal Steve} heals Steve, which takes
 * {@link #others()} when Steve is someone else. The console has to name a
 * player. The parameter is optional by nature - it goes last, with no
 * {@code @Default} - and its type must be a player type of the platform
 * ({@code ServerPlayer} on Forge/NeoForge, {@code Player} on Paper).
 * <p>
 * The {@code others} node is declared on {@code register}, for operators by
 * default, and player names are suggested only to senders who hold it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OrSelf {

    /** The node needed to name another player; empty lets anyone do it. */
    String others() default "";
}
