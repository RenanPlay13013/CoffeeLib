package net.loyalnetwork.coffeelib.neoforge.commands;

import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.CommandScanner;
import net.loyalnetwork.coffeelib.core.command.TextCommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NeoForge's {@link CommandManager}: compiles annotated handlers to Brigadier
 * and adds them every time the server builds its command tree.
 * <p>
 * Brigadier commands are global - there is no {@code modid:} prefix for them,
 * the same as vanilla's - so a command registered here is simply
 * {@code /crate}. It still belongs to the mod that registered it: failures are
 * logged under that mod's id, and two of its handlers cannot claim the same
 * path.
 * <p>
 * Registering while a server is already running works too: the commands are
 * added to the live tree and every online player gets the updated tree, so
 * their client offers the new command straight away.
 */
public final class NeoForgeCommandManager implements CommandManager {

    private final String ownerId;
    private final List<NeoForgeCommandRegistration> registrations = new CopyOnWriteArrayList<>();
    private final TextCommandDispatcher paths = new TextCommandDispatcher();

    public NeoForgeCommandManager(String ownerId) {
        this.ownerId = ownerId;
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
    }

    @Override
    public void register(Object handler) {
        List<CommandDefinition> definitions = CommandScanner.scan(ownerId, handler);
        // Duplicate paths are refused here, across this mod's handlers.
        paths.addAll(definitions);

        NeoForgeCommandRegistration registration = new NeoForgeCommandRegistration(ownerId, handler, definitions);
        registrations.add(registration);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                registration.registerInto(server.getCommands().getDispatcher());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    server.getCommands().sendCommands(player);
                }
            });
        }
    }

    @Override
    public Set<String> registeredRoots() {
        return Set.copyOf(paths.roots());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        for (NeoForgeCommandRegistration registration : registrations) {
            registration.registerInto(event.getDispatcher());
        }
    }
}
