package net.loyalnetwork.coffeelib.api.permission.annotation;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a permission node with its default and description.
 * <p>
 * On a {@code static final String} field, it declares the node the field
 * holds, so the node is written once and referenced everywhere else by the
 * constant:
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
 * On a {@code @Command} method (or its class, for every method in it), it sets
 * the default of the command's {@code permission} node, which the command
 * manager declares by itself:
 * <pre>{@code
 * @Command(value = "list", permission = "crates.list")
 * @Permission(def = PermissionDefault.EVERYONE)
 * void list(CommandSource source) { ... }
 * }</pre>
 * Without it, a command's node defaults to what its op level implies:
 * operators, or everyone at level 0. A description left empty falls back to
 * the command's own.
 * <p>
 * Declaring is what gives the node a default and a description, and on Paper
 * registers it with the server so permission plugins can list it. The first
 * declaration of a node wins, so one made with
 * {@code PermissionManager.declare(...)} before the command is registered
 * keeps its own default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface Permission {

    /** Who has it when no permission plugin decides. */
    PermissionDefault def() default PermissionDefault.OPERATORS;

    String description() default "";
}
