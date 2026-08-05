package net.loyalnetwork.coffeelib.neoforge;

import net.neoforged.fml.common.Mod;

/**
 * Exists only so NeoForge's loader accepts this jar as a mod — CoffeeLib is
 * a library other mods call into via {@link CoffeeLib#forMod}, not
 * something with its own config or lifecycle to run.
 */
@Mod("coffeelib")
public final class CoffeeLibMod {

    public CoffeeLibMod() {
    }
}
