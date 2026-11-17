package net.loyalnetwork.coffeelib.forge.commands;

import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.CommandScanner;
import net.loyalnetwork.coffeelib.core.command.TextCommandDispatcher;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Forge's {@link CommandManager}: compiles annotated handlers to Brigadier
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
public final class ForgeCommandManager implements CommandManager {

    private final String ownerId;
    private final List<ForgeCommandRegistration> registrations = new CopyOnWriteArrayList<>();
    private final TextCommandDispatcher paths = new TextCommandDispatcher();

    public ForgeCommandManager(String ownerId) {
        this.ownerId = ownerId;
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    @Override
    public void register(Object handler) {
        List<CommandDefinition> definitions = CommandScanner.scan(ownerId, handler);
        // Duplicate paths are refused here, across this mod's handlers.
        paths.addAll(definitions);

        // A command's node is known to the permission system - and so to LuckPerms' editor -
        // with the default its op level implies, unless its owner declared it on purpose.
        for (CommandDefinition definition : definitions) {
            if (!definition.permission().isEmpty()) {
                SharedPermissions.nodes().declareIfAbsent(ownerId, definition.permission(),
                        SharedPermissions.defaultForLevel(definition.permissionLevel()),
                        Math.max(definition.permissionLevel(), 0), definition.description());
            }
        }

        ForgeCommandRegistration registration = new ForgeCommandRegistration(ownerId, handler, definitions);
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
        for (ForgeCommandRegistration registration : registrations) {
            registration.registerInto(event.getDispatcher());
        }
    }
}
