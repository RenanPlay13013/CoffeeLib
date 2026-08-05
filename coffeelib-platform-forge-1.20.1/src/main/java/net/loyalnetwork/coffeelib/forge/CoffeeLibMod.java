package net.loyalnetwork.coffeelib.forge;

import net.minecraftforge.fml.common.Mod;

/**
 * Exists only so Forge's loader accepts this jar as a mod — CoffeeLib is a
 * library other mods call into via {@link CoffeeLib#forMod}, not something
 * with its own config or lifecycle to run.
 */
@Mod("coffeelib")
public final class CoffeeLibMod {

    public CoffeeLibMod() {
    }
}
