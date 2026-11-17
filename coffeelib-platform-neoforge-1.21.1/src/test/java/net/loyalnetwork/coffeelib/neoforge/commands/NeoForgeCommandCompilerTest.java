package net.loyalnetwork.coffeelib.neoforge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.annotation.Arg;
import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.command.annotation.Default;
import net.loyalnetwork.coffeelib.api.command.annotation.Greedy;
import net.loyalnetwork.coffeelib.api.command.annotation.Suggest;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.CommandScanner;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The compiled Brigadier tree, run through a real {@link CommandDispatcher}:
 * the part of the command system that cannot be checked by the core tests,
 * because it is where optional arguments, permission requirements and merged
 * subcommands become Brigadier's own nodes.
 */
class NeoForgeCommandCompilerTest {

    enum Rarity { COMMON, RARE }

    @Command("crate")
    static final class CrateCommands {
        final List<String> calls = new ArrayList<>();

        @Command("give")
        void give(CommandSource source, @Arg("player") @Suggest({"Steve", "Alex"}) String player,
                  @Arg("amount") @Default("1") int amount, @Arg("rarity") @Default("common") Rarity rarity) {
            calls.add("give " + player + " " + amount + " " + rarity);
        }

        @Command(value = "reset", permissionLevel = 4)
        void reset(CommandSourceStack stack) {
            calls.add("reset");
        }

        @Command("say")
        void say(@Arg("text") @Greedy String text) {
            calls.add("say " + text);
        }
    }

    private static CommandDispatcher<CommandSourceStack> compile(Object handler) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (CommandDefinition definition : CommandScanner.scan("testmod", handler)) {
            dispatcher.register(NeoForgeCommandCompiler.compile(definition));
        }
        return dispatcher;
    }

    private static CommandSourceStack source(int level) {
        CommandSourceStack stack = mock(CommandSourceStack.class);
        when(stack.hasPermission(anyInt())).thenAnswer(call -> call.<Integer>getArgument(0) <= level);
        when(stack.getTextName()).thenReturn("Steve");
        return stack;
    }

    @Test
    void everyArityOfOptionalArgumentsIsExecutable() throws CommandSyntaxException {
        CrateCommands commands = new CrateCommands();
        CommandDispatcher<CommandSourceStack> dispatcher = compile(commands);
        CommandSourceStack player = source(0);

        dispatcher.execute("crate give Steve", player);
        dispatcher.execute("crate give Steve 5", player);
        dispatcher.execute("crate give Steve 5 rare", player);

        assertEquals(List.of("give Steve 1 COMMON", "give Steve 5 COMMON", "give Steve 5 RARE"), commands.calls);
    }

    @Test
    void aMissingRequiredArgumentIsNotExecutable() {
        CommandDispatcher<CommandSourceStack> dispatcher = compile(new CrateCommands());
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("crate give", source(0)));
    }

    @Test
    void anIntegerNodeRejectsTextBeforeTheExecutorRuns() {
        CrateCommands commands = new CrateCommands();
        CommandDispatcher<CommandSourceStack> dispatcher = compile(commands);

        // Brigadier's own IntegerArgumentType - the same thing that lets the client flag it red.
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("crate give Steve lots", source(0)));
        assertTrue(commands.calls.isEmpty());
    }

    @Test
    void thePermissionLevelHidesTheSubcommandButNotItsSiblings() throws CommandSyntaxException {
        CrateCommands commands = new CrateCommands();
        CommandDispatcher<CommandSourceStack> dispatcher = compile(commands);

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("crate reset", source(2)));
        dispatcher.execute("crate give Alex", source(2));
        dispatcher.execute("crate reset", source(4));

        assertEquals(List.of("give Alex 1 COMMON", "reset"), commands.calls);
    }

    @Test
    void aNativeSenderParameterReceivesTheStack() throws CommandSyntaxException {
        CrateCommands commands = new CrateCommands();
        compile(commands).execute("crate reset", source(4));
        assertEquals(List.of("reset"), commands.calls);
    }

    @Test
    void aGreedyArgumentTakesTheRest() throws CommandSyntaxException {
        CrateCommands commands = new CrateCommands();
        compile(commands).execute("crate say double drops tonight", source(0));
        assertEquals(List.of("say double drops tonight"), commands.calls);
    }

    @Test
    void subcommandsFromDifferentMethodsMergeUnderOneRoot() {
        CommandDispatcher<CommandSourceStack> dispatcher = compile(new CrateCommands());
        assertEquals(1, dispatcher.getRoot().getChildren().size(), "one /crate root");
        assertEquals(3, dispatcher.getRoot().getChild("crate").getChildren().size());
    }

    @Test
    void argumentsAndSubcommandsTabComplete() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = compile(new CrateCommands());
        CommandSourceStack player = source(0);

        assertEquals(List.of("give", "reset", "say"), suggestions(dispatcher, "crate ", player));
        // Brigadier's completion does not consult requires() itself: in game the server sends
        // each player only the nodes canUse() lets through (Commands#sendCommands), and the
        // client completes from that. So the requirement on the node is what has to be right.
        assertTrue(!dispatcher.getRoot().getChild("crate").getChild("reset").canUse(player),
                "a player is not sent the level-4 subcommand");
        assertTrue(dispatcher.getRoot().getChild("crate").getChild("reset").canUse(source(4)));
        assertTrue(dispatcher.getRoot().getChild("crate").canUse(player), "the shared root stays visible");

        assertEquals(List.of("Alex", "Steve"), suggestions(dispatcher, "crate give ", player));
        assertEquals(List.of("Steve"), suggestions(dispatcher, "crate give st", player));
        assertEquals(List.of("common", "rare"), suggestions(dispatcher, "crate give Steve 2 ", player));
    }

    private static List<String> suggestions(CommandDispatcher<CommandSourceStack> dispatcher, String input,
                                            CommandSourceStack source) throws Exception {
        ParseResults<CommandSourceStack> parse = dispatcher.parse(input, source);
        List<String> texts = new ArrayList<>();
        for (Suggestion suggestion : dispatcher.getCompletionSuggestions(parse).get().getList()) {
            texts.add(suggestion.getText());
        }
        texts.sort(null);
        return texts;
    }
}
