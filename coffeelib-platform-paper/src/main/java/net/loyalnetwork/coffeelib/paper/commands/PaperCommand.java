package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.core.command.TextCommandDispatcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * One root label, registered with Bukkit as a real command.
 * <p>
 * Implements {@link PluginIdentifiableCommand} with the plugin that registered
 * it - not CoffeeLib - so the server attributes it correctly: {@code /help},
 * {@code /plugins}, and the {@code /theirplugin:crate} namespaced form all
 * name the owning plugin.
 */
final class PaperCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin owner;
    private final TextCommandDispatcher dispatcher;

    PaperCommand(String root, Plugin owner, TextCommandDispatcher dispatcher, String description) {
        super(root, description, "/" + root, List.of());
        this.owner = owner;
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // getName(), not label: label may be the namespaced form or an alias Bukkit resolved.
        dispatcher.execute(new PaperCommandSource(sender), getName(), args);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return dispatcher.complete(new PaperCommandSource(sender), getName(), args);
    }

    @Override
    public Plugin getPlugin() {
        return owner;
    }
}
