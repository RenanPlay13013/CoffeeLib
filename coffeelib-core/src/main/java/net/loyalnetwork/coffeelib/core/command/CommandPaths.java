package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Value-oriented command path rules: aliases, normalized segments and collision checks. */
final class CommandPaths {
    private CommandPaths() { }

    /** The main path and every alias, each split into lower-case words. */
    static List<List<String>> paths(Command command, String where) {
        List<List<String>> paths = new ArrayList<>();
        paths.add(words(command.value(), where));
        for (String alias : command.aliases()) {
            List<String> words = words(alias, where);
            if (words.isEmpty()) {
                throw new CoffeeLibException("@Command alias on " + where + " must not be empty");
            }
            paths.add(words);
        }
        return paths;
    }

    private static List<String> words(String path, String where) {
        List<String> words = new ArrayList<>();
        for (String word : path.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.startsWith("/")) {
                throw new CoffeeLibException("@Command path on " + where + " must not start with '/': '" + path + "'");
            }
            words.add(word.toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /**
     * Two executors for the same path would make one of them unreachable on
     * Paper and silently replace the other on Brigadier; refuse instead.
     */
    static void requireNoDuplicates(List<CommandDefinition> definitions) {
        Set<String> seen = new HashSet<>();
        for (CommandDefinition definition : definitions) {
            String key = String.join(" ", definition.path());
            if (!seen.add(key)) {
                throw new CoffeeLibException("More than one @Command method is registered for '/" + key + "'");
            }
        }
    }
}
