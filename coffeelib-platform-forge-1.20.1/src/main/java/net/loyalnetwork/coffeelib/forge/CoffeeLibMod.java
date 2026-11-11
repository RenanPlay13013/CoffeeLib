package net.loyalnetwork.coffeelib.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes Forge's loader accept this jar as a mod, and registers the one piece
 * of behavior CoffeeLib runs itself: {@code /coffeelib config}.
 * <p>
 * That command is the operator side of the config formats CoffeeLib manages
 * itself (anything but
 * {@link net.loyalnetwork.coffeelib.api.config.ConfigFormat#NATIVE}) — manual
 * reload and start/stop of hot reload. It has to live here because Forge
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
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CoffeeLibCommands.register(event.getDispatcher());
    }
}
