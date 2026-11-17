package net.loyalnetwork.coffeelib.neoforge.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
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
            dispatcher.register(NeoForgeCommandCompiler.compile(definition));
        }
    }
}
