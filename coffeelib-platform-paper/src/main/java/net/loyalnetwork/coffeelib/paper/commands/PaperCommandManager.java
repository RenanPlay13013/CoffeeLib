package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import net.loyalnetwork.coffeelib.core.command.ArgumentTypes;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.CommandRoots;
import net.loyalnetwork.coffeelib.core.command.CommandScanner;
import net.loyalnetwork.coffeelib.core.command.TextCommandDispatcher;
import net.loyalnetwork.coffeelib.paper.permission.PaperPermissionManager;
import net.loyalnetwork.coffeelib.paper.scheduler.PaperScheduler;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final TextCommandDispatcher dispatcher;
    private final ArgumentTypes types = ArgumentTypes.withDefaults();
    private final Set<String> registeredRoots = new HashSet<>();

    public PaperCommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // An asynchronous command's late reply is sent from the main thread.
        this.dispatcher = new TextCommandDispatcher(new PaperScheduler(plugin).main());
        PaperArguments.registerInto(types);
    }

    @Override
    public synchronized void register(Object handler) {
        List<CommandDefinition> definitions = CommandScanner.scan(plugin.getName(), handler, types);
        dispatcher.addAll(definitions);
        CommandRoots.claim(plugin.getName(), definitions);

        // Known to Bukkit - and so to LuckPerms' editor - with its @Permission default or the one
        // the op level implies.
        for (CommandDefinition definition : definitions) {
            if (!definition.permission().isEmpty()) {
                PaperPermissionManager.declareCommandNode(plugin, definition.permission(),
                        definition.permissionDefault(), definition.permissionDescription());
            }
            for (String others : definition.othersPermissions()) {
                PaperPermissionManager.declareCommandNode(plugin, others, PermissionDefault.OPERATORS,
                        definition.usage() + " on other players");
            }
        }

        CommandMap commandMap = commandMap(plugin.getServer());
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        for (CommandDefinition definition : definitions) {
            if (CommandRoots.isReplaced(definition.root())) {
                removeForeign(commandMap, definition.root());
            }
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

    @Override
    public <T> void argument(Class<T> type, CommandArgument<T> argument) {
        types.register(type, argument);
    }

    /**
     * {@code @Command(replaces = true)}: takes the label from a command that is
     * not CoffeeLib's - every key it is known by, namespaced ones included - so
     * ours is registered under the plain label instead of a fallback.
     */
    private void removeForeign(CommandMap commandMap, String root) {
        Map<String, Command> known = knownCommands(commandMap);
        Command existing = known.get(root);
        if (existing == null || existing instanceof PaperCommand) {
            return;
        }
        known.values().removeIf(command -> command == existing);
        existing.unregister(commandMap);
        plugin.getLogger().info("Replaced the existing '/" + root + "' command");
    }

    /** The command map's label table: public from 1.13 on, a protected field before. */
    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandMap commandMap) {
        try {
            Method getter = commandMap.getClass().getMethod("getKnownCommands");
            return (Map<String, Command>) getter.invoke(commandMap);
        } catch (ReflectiveOperationException ignored) {
            // An older server: read the field instead.
        }
        for (Class<?> type = commandMap.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("knownCommands");
                field.setAccessible(true);
                return (Map<String, Command>) field.get(commandMap);
            } catch (NoSuchFieldException e) {
                // Keep looking up the hierarchy.
            } catch (IllegalAccessException e) {
                break;
            }
        }
        throw new CoffeeLibException("Could not reach the server's known commands to replace one");
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
