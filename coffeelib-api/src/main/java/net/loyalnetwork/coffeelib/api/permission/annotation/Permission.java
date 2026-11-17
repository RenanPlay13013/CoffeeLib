package net.loyalnetwork.coffeelib.api.permission.annotation;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the node a {@code static final String} field holds, so the node is
 * written once and referenced everywhere else by the constant:
 * <pre>{@code
 * public final class CratePermissions {
 *
 *     @Permission(description = "Give crate keys to players")
 *     public static final String GIVE = "crates.give";
 *
 *     @Permission(def = PermissionDefault.EVERYONE, description = "Open crates")
 *     public static final String OPEN = "crates.open";
 * }
 *
 * CoffeeLib.permissions(container).declare(CratePermissions.class);
 * }</pre>
 * Declaring is what gives the node a default and a description, and on Paper
 * registers it with the server so permission plugins can list it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Permission {

    /** Who has it when no permission plugin decides. */
    PermissionDefault def() default PermissionDefault.OPERATORS;

    String description() default "";
}
