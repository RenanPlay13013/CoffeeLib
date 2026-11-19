package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Which registry a {@code Holder<T>} argument is read from, on Forge and
 * NeoForge: the sender types an id ({@code minecraft:sharpness}, or just
 * {@code sharpness}) and the executor gets the registry entry.
 * <pre>{@code
 * void enchant(ServerPlayer player, @Arg("enchantment") @Registry("enchantment") Holder<Enchantment> enchantment)
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Registry {

    /** The registry's id: {@code "enchantment"}, {@code "mob_effect"}, {@code "minecraft:item"}... */
    String value();
}
