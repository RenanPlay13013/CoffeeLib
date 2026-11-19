package net.loyalnetwork.coffeelib.neoforge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.CommandRoots;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;

/**
 * One handler object's commands, compiled into whichever dispatcher the
 * server is building. Kept per handler so a server that rebuilds its command
 * tree - every {@code /reload} - gets every command back.
 */
public record NeoForgeCommandRegistration(String ownerId, Object handler, List<CommandDefinition> definitions) {

    public NeoForgeCommandRegistration {
        definitions = List.copyOf(definitions);
    }

    public void registerInto(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (CommandDefinition definition : definitions) {
            String root = definition.root();
            boolean replaced = CommandRoots.isReplaced(root);
            if (replaced) {
                ReplacedRoots.removeForeign(dispatcher, root, ownerId);
            }
            LiteralCommandNode<CommandSourceStack> node = dispatcher.register(NeoForgeCommandCompiler.compile(definition));
            if (replaced) {
                ReplacedRoots.markOwn(node);
            }
        }
    }
}
