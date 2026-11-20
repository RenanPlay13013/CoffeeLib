package net.loyalnetwork.coffeelib.api.menu;

/** Platform boundary: opens one independent inventory for one player. */
@FunctionalInterface
public interface MenuOpener<S, P> {
    void open(MenuSnapshot<S, P> snapshot, P player);
}
