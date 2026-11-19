package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.loyalnetwork.coffeelib.core.text.StyledText;

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
    public void success(String message) { sender.sendMessage(StyledText.parse("&a" + message).legacy()); }

    @Override
    public void info(String message) { sender.sendMessage(StyledText.parse("&7" + message).legacy()); }

    @Override
    public void error(String message) { sender.sendMessage(StyledText.parse("&c" + message).legacy()); }

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

    /** A player who left can no longer be told anything; the console and other senders can. */
    @Override
    public boolean isAvailable() {
        return !(sender instanceof Player player) || player.isOnline();
    }
}
