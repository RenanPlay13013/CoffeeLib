package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** A {@link CommandSource} over a Bukkit {@link CommandSender}; permissions are Bukkit nodes. */
public final class PaperCommandSource implements CommandSource {

    private final CommandSender sender;

    public PaperCommandSource(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return permission.isEmpty() || sender.hasPermission(permission);
    }

    @Override
    public Object player() {
        return sender instanceof Player ? sender : null;
    }

    @Override
    public Object unwrap() {
        return sender;
    }
}
