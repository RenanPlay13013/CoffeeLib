package net.loyalnetwork.coffeelib.core.text;

import net.loyalnetwork.coffeelib.api.placeholder.TextContext;

/**
 * How a platform delivers a rendered message: to whom, the text with its
 * {@code {name}} pairs already filled in, and the context its
 * {@code %namespace_key%} placeholders are resolved with when the platform
 * formats it.
 */
@FunctionalInterface
public interface MessageDelivery {

    void deliver(Object recipient, String text, TextContext context);
}
