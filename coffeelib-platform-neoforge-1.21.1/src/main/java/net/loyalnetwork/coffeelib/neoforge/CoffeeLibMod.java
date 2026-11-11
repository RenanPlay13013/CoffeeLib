package net.loyalnetwork.coffeelib.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Makes NeoForge's loader accept this jar as a mod, and registers the one
 * piece of behavior CoffeeLib runs itself: {@code /coffeelib config}.
 * <p>
 * That command is the operator side of the config formats CoffeeLib manages
 * itself (anything but
 * {@link net.loyalnetwork.coffeelib.api.config.ConfigFormat#NATIVE}) — manual
 * reload and start/stop of hot reload. It has to live here because NeoForge
 * registers commands through a game-bus event, and because the mods whose
 * configs it acts on hand CoffeeLib nothing; they are found through
 * {@link net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry}.
 * Everything else is still library code other mods call into via
 * {@link CoffeeLib#forMod}.
 */
@Mod("coffeelib")
public final class CoffeeLibMod {

    public CoffeeLibMod() {
        // The game bus, not the mod bus: RegisterCommandsEvent fires per server start.
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CoffeeLibCommands.register(event.getDispatcher());
    }
}
