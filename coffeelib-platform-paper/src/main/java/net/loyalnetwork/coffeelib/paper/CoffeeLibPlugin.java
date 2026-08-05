package net.loyalnetwork.coffeelib.paper;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Exists only so Paper accepts this jar as a plugin — CoffeeLib is a
 * library other plugins call into via {@link CoffeeLib#forPlugin}, not
 * something with its own behavior to run.
 */
public final class CoffeeLibPlugin extends JavaPlugin {
}
