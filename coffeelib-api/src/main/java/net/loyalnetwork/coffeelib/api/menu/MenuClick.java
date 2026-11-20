package net.loyalnetwork.coffeelib.api.menu;

/** An action from a read-only chest menu; callbacks always run on the server thread. */
public record MenuClick<P>(P player, int slot) { }
