package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which owners have commands under which roots, across every mod or plugin on
 * this CoffeeLib, so {@code @Command(replaces = true)} replaces only a command
 * that is not CoffeeLib's and two owners cannot fight over one root.
 */
public final class CommandRoots {

    private static final Map<String, Set<String>> OWNERS = new HashMap<>();
    private static final Map<String, String> REPLACERS = new HashMap<>();

    private CommandRoots() {
    }

    /**
     * Records {@code ownerId}'s roots, refusing - before anything is
     * registered - a root another owner replaces, or a replacement of a root
     * another owner already uses.
     */
    public static synchronized void claim(String ownerId, List<CommandDefinition> definitions) {
        for (CommandDefinition definition : definitions) {
            String root = definition.root();
            String replacer = REPLACERS.get(root);
            if (replacer != null && !replacer.equals(ownerId)) {
                throw new CoffeeLibException("'/" + root + "' of " + ownerId + " collides with " + replacer
                        + ", which replaces that command");
            }
            if (definition.replaces()) {
                for (String other : OWNERS.getOrDefault(root, Set.of())) {
                    if (!other.equals(ownerId)) {
                        throw new CoffeeLibException(ownerId + " replaces '/" + root + "', but " + other
                                + " already has a command there; only commands that are not CoffeeLib's are replaced");
                    }
                }
            }
        }
        for (CommandDefinition definition : definitions) {
            OWNERS.computeIfAbsent(definition.root(), root -> new HashSet<>()).add(ownerId);
            if (definition.replaces()) {
                REPLACERS.put(definition.root(), ownerId);
            }
        }
    }

    /**
     * Removes an owner's claims after its plugin is disabled. Without this,
     * a future plugin owner can be refused because a previous classloader's
     * replacement claim is still in the process-wide registry.
     */
    public static synchronized void releaseOwner(String ownerId) {
        OWNERS.values().forEach(owners -> owners.remove(ownerId));
        OWNERS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        REPLACERS.entrySet().removeIf(entry -> entry.getValue().equals(ownerId));
    }

    /** Whether some owner's command replaces whatever else is under {@code root}. */
    public static synchronized boolean isReplaced(String root) {
        return REPLACERS.containsKey(root);
    }

    /** Forgets every claim: for tests. */
    static synchronized void clear() {
        OWNERS.clear();
        REPLACERS.clear();
    }
}
