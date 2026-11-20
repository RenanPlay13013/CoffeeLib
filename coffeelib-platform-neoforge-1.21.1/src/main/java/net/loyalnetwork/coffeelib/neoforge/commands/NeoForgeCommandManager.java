package net.loyalnetwork.coffeelib.neoforge.commands;

import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.core.command.CommandServices;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import net.loyalnetwork.coffeelib.core.command.ArgumentTypes;
import net.loyalnetwork.coffeelib.core.command.CommandRoots;
import net.loyalnetwork.coffeelib.core.command.CommandScanner;
import net.loyalnetwork.coffeelib.core.command.TextCommandDispatcher;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
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
    private final ArgumentTypes types = ArgumentTypes.withDefaults();

    public NeoForgeCommandManager(String ownerId) {
        this.ownerId = ownerId;
        NeoForgeArguments.registerInto(types);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
    }

    @Override
    public synchronized void register(Object handler) {
        CommandServices.wire(ownerId, handler);
        List<CommandDefinition> definitions = CommandScanner.scan(ownerId, handler, types);
        // Compiled once now so a tree Brigadier cannot build fails here, not at server start.
        for (CommandDefinition definition : definitions) {
            NeoForgeCommandCompiler.compile(definition);
        }
        // Duplicate paths are refused here, across this mod's handlers; roots across every owner.
        paths.validateAddAll(definitions);
        CommandRoots.claim(ownerId, definitions);
        paths.addAll(definitions);

        // A command's node is known to the permission system - and so to LuckPerms' editor -
        // with its @Permission default or the one its op level implies, unless its owner
        // declared it on purpose first.
        for (CommandDefinition definition : definitions) {
            if (!definition.permission().isEmpty()) {
                SharedPermissions.nodes().declareIfAbsent(ownerId, definition.permission(),
                        definition.permissionDefault(), Math.max(definition.permissionLevel(), 0),
                        definition.permissionDescription());
            }
            for (String others : definition.othersPermissions()) {
                SharedPermissions.nodes().declareIfAbsent(ownerId, others, PermissionDefault.OPERATORS,
                        Math.max(definition.permissionLevel(), 2), definition.usage() + " on other players");
            }
        }

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

    @Override
    public <T> void argument(Class<T> type, CommandArgument<T> argument) {
        types.register(type, argument);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        for (NeoForgeCommandRegistration registration : registrations) {
            registration.registerInto(event.getDispatcher());
        }
    }
}
