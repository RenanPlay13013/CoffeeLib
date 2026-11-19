package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Runs and tab-completes commands from plain text: a label and the words
 * after it, which is all a platform without Brigadier hands over.
 * <p>
 * Paper uses it directly; the Forge-like platforms compile the same
 * {@link CommandDefinition}s to Brigadier instead, and the tests drive this
 * class to pin down the behavior both share. Matching is longest path first,
 * so {@code /crate give} reaches the {@code "crate give"} executor even when
 * a {@code "crate"} executor exists too.
 */
public final class TextCommandDispatcher {

    private final List<CommandDefinition> definitions = new CopyOnWriteArrayList<>();
    private final Executor mainThread;

    /** A dispatcher whose asynchronous commands reply from the thread their future completes on. */
    public TextCommandDispatcher() {
        this(Runnable::run);
    }

    /** @param mainThread where an asynchronous command's late reply is sent from */
    public TextCommandDispatcher(Executor mainThread) {
        this.mainThread = mainThread;
    }

    /** Adds definitions, refusing a path that is already taken. */
    public void addAll(List<CommandDefinition> added) {
        List<CommandDefinition> combined = new ArrayList<>(definitions);
        combined.addAll(added);
        CommandScanner.requireNoDuplicates(combined);
        definitions.addAll(added);
    }

    public List<CommandDefinition> definitions() {
        return List.copyOf(definitions);
    }

    /**
     * Runs {@code label args...}. Every failure meant for the sender - bad
     * input, missing permission, unknown subcommand, an executor's own
     * {@link CommandException} - is sent to them; this never throws for those.
     *
     * @return whether a command ran successfully
     */
    public boolean execute(CommandSource source, String label, String[] args) {
        List<String> tokens = tokens(label, args);
        CommandDefinition match = longestMatch(tokens);

        if (match == null) {
            sendSubcommands(source, tokens);
            return false;
        }

        List<String> rest = tokens.subList(match.path().size(), tokens.size());
        List<String> arguments = split(match.parameters(), rest);

        try {
            CompletionStage<?> pending = match.execute(source, arguments);
            if (pending != null) {
                AsyncCommands.watch(match, source, pending, mainThread, source::sendMessage);
            }
            return true;
        } catch (CommandException e) {
            source.sendMessage(e.getMessage());
            return false;
        }
    }

    /**
     * Completions for the last word of {@code args}, which may be partly typed
     * or empty: the next subcommand words, or the values the argument in that
     * position suggests. Only commands the sender may run are offered.
     */
    public List<String> complete(CommandSource source, String label, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        List<String> tokens = tokens(label, args);
        List<String> typed = tokens.subList(0, tokens.size() - 1);
        String partial = args[args.length - 1];
        String partialLower = partial.toLowerCase(Locale.ROOT);

        TreeSet<String> completions = new TreeSet<>();
        for (CommandDefinition definition : definitions) {
            if (!source.hasPermission(definition.permission()) || !agrees(typed, definition.path())) {
                continue;
            }

            int position = typed.size();
            List<String> path = definition.path();
            if (position < path.size()) {
                String next = path.get(position);
                if (next.startsWith(partialLower)) {
                    completions.add(next);
                }
                continue;
            }

            completions.addAll(completeArgument(source, definition, typed.subList(path.size(), typed.size()),
                    partial));
        }
        return new ArrayList<>(completions);
    }

    /**
     * Completions for the argument word being typed, given the argument words
     * before it - which say which parameter it belongs to, since one may take
     * several words or the rest of the line.
     */
    private static List<String> completeArgument(CommandSource source, CommandDefinition definition,
                                                 List<String> before, String partial) {
        List<ParameterDefinition> parameters = definition.parameters();
        int word = before.size();
        int start = 0;
        for (int i = 0; i < parameters.size(); i++) {
            ParameterDefinition parameter = parameters.get(i);
            int length = parameter.kind() == ArgumentKind.GREEDY ? Integer.MAX_VALUE : parameter.words();
            if (word - start < length) {
                List<String> own = new ArrayList<>(before.subList(start, word));
                own.add(partial);
                List<String> earlier = split(parameters.subList(0, i), before.subList(0, start));
                DefaultSuggestContext context = new DefaultSuggestContext(source, String.join(" ", own),
                        name -> parsedEarlier(source, parameters, earlier, name));
                // Bukkit replaces only the last word, so every suggestion stands for the token.
                return parameter.suggest(context, true);
            }
            start += length;
        }
        return List.of();
    }

    /** An earlier argument, parsed, for {@code SuggestContext#previous}. */
    private static Object parsedEarlier(CommandSource source, List<ParameterDefinition> parameters,
                                        List<String> earlier, String name) {
        for (int i = 0; i < earlier.size() && i < parameters.size(); i++) {
            if (parameters.get(i).name().equals(name)) {
                return parameters.get(i).parse(source, earlier.get(i));
            }
        }
        return null;
    }

    /**
     * The words after a command's path as one string per argument: a
     * multi-word argument's words joined, a greedy one taking the rest. Words
     * left over are kept, one each, for the usage error they cause.
     */
    static List<String> split(List<ParameterDefinition> parameters, List<String> words) {
        List<String> arguments = new ArrayList<>();
        int at = 0;
        for (ParameterDefinition parameter : parameters) {
            if (at >= words.size()) {
                return arguments;
            }
            if (parameter.kind() == ArgumentKind.GREEDY) {
                arguments.add(String.join(" ", words.subList(at, words.size())));
                return arguments;
            }
            int end = Math.min(words.size(), at + parameter.words());
            arguments.add(String.join(" ", words.subList(at, end)));
            at = end;
        }
        arguments.addAll(words.subList(at, words.size()));
        return arguments;
    }

    /** The root labels known here - what a platform registers as top-level commands. */
    public TreeSet<String> roots() {
        TreeSet<String> roots = new TreeSet<>();
        for (CommandDefinition definition : definitions) {
            roots.add(definition.root());
        }
        return roots;
    }

    private CommandDefinition longestMatch(List<String> tokens) {
        CommandDefinition best = null;
        for (CommandDefinition definition : definitions) {
            List<String> path = definition.path();
            if (path.size() <= tokens.size() && startsWith(tokens, path)
                    && (best == null || path.size() > best.path().size())) {
                best = definition;
            }
        }
        return best;
    }

    /**
     * For {@code /crate} when only {@code /crate give} and {@code /crate open}
     * exist: list what the sender can run under that prefix, instead of an
     * unhelpful "unknown command".
     */
    private void sendSubcommands(CommandSource source, List<String> tokens) {
        List<String> usages = new ArrayList<>();
        for (CommandDefinition definition : definitions) {
            if (startsWith(definition.path(), tokens) && source.hasPermission(definition.permission())) {
                usages.add(definition.usage());
            }
        }
        if (usages.isEmpty()) {
            source.sendMessage("Unknown command. Usage: " + String.join(" | ", roots()));
            return;
        }
        usages.sort(null);
        source.sendMessage("Usage:");
        for (String usage : usages) {
            source.sendMessage("  " + usage);
        }
    }

    private static List<String> tokens(String label, String[] args) {
        List<String> tokens = new ArrayList<>();
        tokens.add(stripNamespace(label).toLowerCase(Locale.ROOT));
        tokens.addAll(Arrays.asList(args));
        // A literal compares case-insensitively; arguments keep the sender's casing.
        return tokens;
    }

    /** Paper hands over {@code myplugin:crate} when the namespaced form is used. */
    private static String stripNamespace(String label) {
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    /**
     * Whether what was typed and a command path agree as far as both go: true
     * for {@code [crate]} against {@code crate give} (still completing the
     * path) and for {@code [crate, give, Steve]} (past it, into arguments).
     */
    private static boolean agrees(List<String> typed, List<String> path) {
        int common = Math.min(typed.size(), path.size());
        return startsWith(typed.subList(0, common), path.subList(0, common));
    }

    /** Whether {@code whole} begins with {@code prefix}, comparing words case-insensitively. */
    private static boolean startsWith(List<String> whole, List<String> prefix) {
        if (prefix.size() > whole.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!whole.get(i).equalsIgnoreCase(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }
}
