package net.loyalnetwork.coffeelib.neoforge.commands;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.loyalnetwork.coffeelib.neoforge.text.NeoForgeTextFormatter;
import net.minecraft.server.level.ServerPlayer;

/**
 * A {@link CommandSource} over NeoForge's {@link CommandSourceStack}.
 * <p>
 * Permission nodes go through {@link NeoForgePermissions}: LuckPerms when it is
 * installed, the node's declared default otherwise, with the command's op
 * level standing in for "operators" - the same check its Brigadier
 * {@code requires} made before the command could run.
 */
public final class NeoForgeCommandContext implements CommandSource {

    private final CommandSourceStack stack;
    private final int permissionLevel;

    public NeoForgeCommandContext(CommandSourceStack stack, int permissionLevel) {
        this.stack = stack;
        this.permissionLevel = permissionLevel;
    }

    @Override
    public String name() {
        return stack.getTextName();
    }

    @Override
    public void sendMessage(String message) {
        stack.sendSystemMessage(Component.literal(message));
    }

    private static final NeoForgeTextFormatter TEXT = new NeoForgeTextFormatter();

    @Override
    public void success(String message) { stack.sendSystemMessage(TEXT.parse("&a" + message)); }

    @Override
    public void info(String message) { stack.sendSystemMessage(TEXT.parse("&7" + message)); }

    @Override
    public void error(String message) { stack.sendSystemMessage(TEXT.parse("&c" + message)); }

    @Override
    public boolean hasPermission(String permission) {
        return NeoForgePermissions.check(stack, permission, permissionLevel);
    }

    @Override
    public Object player() {
        return stack.getPlayer();
    }

    @Override
    public Object unwrap() {
        return stack;
    }

    /** A player who left can no longer be told anything; every other source can. */
    @Override
    public boolean isAvailable() {
        ServerPlayer player = stack.getPlayer();
        return player == null || !player.hasDisconnected();
    }
}
