package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.CommandScanner;
import net.loyalnetwork.coffeelib.core.command.TextCommandDispatcher;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Paper's {@link CommandManager}: registers each root label as a Bukkit
 * command at runtime, so a plugin needs no {@code commands:} section in its
 * {@code plugin.yml}.
 * <p>
 * Commands go under the <b>owning plugin's</b> namespace -
 * {@code /theirplugin:crate} next to plain {@code /crate} - which is the prefix
 * Bukkit itself would give a command declared in that plugin's
 * {@code plugin.yml}. CoffeeLib's name never appears.
 */
public final class PaperCommandManager implements CommandManager {

    private final JavaPlugin plugin;
    private final TextCommandDispatcher dispatcher = new TextCommandDispatcher();
    private final Set<String> registeredRoots = new HashSet<>();

    public PaperCommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public synchronized void register(Object handler) {
        List<CommandDefinition> definitions = CommandScanner.scan(plugin.getName(), handler);
        dispatcher.addAll(definitions);

        CommandMap commandMap = commandMap(plugin.getServer());
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        for (CommandDefinition definition : definitions) {
            if (registeredRoots.add(definition.root())) {
                commandMap.register(namespace,
                        new PaperCommand(definition.root(), plugin, dispatcher, definition.description()));
            }
        }
        refreshCommandTrees(plugin.getServer());
    }

    @Override
    public synchronized Set<String> registeredRoots() {
        return Set.copyOf(registeredRoots);
    }

    /**
     * {@code Server#getCommandMap()} only became API in later versions, but
     * every CraftBukkit-based server has had the method, so it is reached
     * reflectively to keep one jar working from 1.8 upwards.
     */
    private static CommandMap commandMap(Server server) {
        try {
            Method getter = server.getClass().getMethod("getCommandMap");
            return (CommandMap) getter.invoke(server);
        } catch (ReflectiveOperationException e) {
            throw new CoffeeLibException("Could not reach the server's command map to register commands", e);
        }
    }

    /**
     * From 1.13 the client keeps its own copy of the command tree; without a
     * resend, a command registered after a player joined would work but not
     * tab-complete for them. {@code Player#updateCommands()} does not exist
     * before that, so it is looked up rather than called directly.
     */
    private static void refreshCommandTrees(Server server) {
        Method update;
        try {
            update = Player.class.getMethod("updateCommands");
        } catch (NoSuchMethodException e) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            try {
                update.invoke(player);
            } catch (ReflectiveOperationException ignored) {
                // Only costs that player completion until they rejoin.
            }
        }
    }
}
