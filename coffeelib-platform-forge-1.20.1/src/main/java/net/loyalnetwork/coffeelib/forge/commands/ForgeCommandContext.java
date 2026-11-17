package net.loyalnetwork.coffeelib.forge.commands;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * A {@link CommandSource} over Forge's {@link CommandSourceStack}.
 * <p>
 * Forge has operator levels rather than permission nodes, so a node passes
 * when the source has the level its command derived for it - the same check
 * Brigadier's {@code requires} already made before the command could run.
 */
public final class ForgeCommandContext implements CommandSource {

    private final CommandSourceStack stack;
    private final int permissionLevel;

    public ForgeCommandContext(CommandSourceStack stack, int permissionLevel) {
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

    @Override
    public boolean hasPermission(String permission) {
        return permission.isEmpty() || stack.hasPermission(permissionLevel);
    }

    @Override
    public Object player() {
        return stack.getPlayer();
    }

    @Override
    public Object unwrap() {
        return stack;
    }
}
