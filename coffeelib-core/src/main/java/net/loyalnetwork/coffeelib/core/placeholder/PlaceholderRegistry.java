package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderContext;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderResolver;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Every registered placeholder, by namespace and key, and the parser that
 * fills them in.
 * <p>
 * One instance per process ({@link SharedPlaceholders}) is what lets a mod
 * resolve another mod's placeholders. Parsing follows PlaceholderAPI, so text
 * written for Paper works unchanged: {@code %namespace_key%} and
 * {@code %namespace_key_argument%}, unknown placeholders left as written.
 * Two deliberate differences, both because mod ids are not plugin names:
 * <ul>
 *   <li>The namespace is matched against the registered ones, longest first,
 *       instead of being cut at the first {@code _} - a mod id such as
 *       {@code my_mod} would otherwise never resolve.</li>
 *   <li>A token containing whitespace is never a placeholder, so
 *       "50% off, 20% more" is left alone instead of being searched for a
 *       namespace called {@code " off, 20"}.</li>
 * </ul>
 */
public final class PlaceholderRegistry {

    private static final Logger LOGGER = Logger.getLogger(PlaceholderRegistry.class.getName());

    /** Lower case letters, digits, {@code _} and {@code -}, starting with a letter or digit. */
    static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    private final Map<String, Map<String, Entry>> namespaces = new ConcurrentHashMap<>();

    void register(String ownerId, String namespace, String key, PlaceholderResolver resolver) {
        requireName(namespace, "namespace");
        requireName(key, "key");
        Map<String, Entry> keys = namespaces.computeIfAbsent(namespace, ns -> new ConcurrentHashMap<>());
        Entry existing = keys.putIfAbsent(key, new Entry(ownerId, resolver));
        if (existing != null) {
            throw new CoffeeLibException("Placeholder %" + namespace + "_" + key + "% is already registered by "
                    + existing.ownerId());
        }
    }

    void unregisterAll(String ownerId, String namespace) {
        Map<String, Entry> keys = namespaces.get(namespace);
        if (keys == null) {
            return;
        }
        keys.values().removeIf(entry -> entry.ownerId().equals(ownerId));
        if (keys.isEmpty()) {
            namespaces.remove(namespace, keys);
        }
    }

    /** Drops every placeholder {@code ownerId} registered, in any namespace - for an owner going away. */
    public void unregisterOwner(String ownerId) {
        for (String namespace : namespaces.keySet()) {
            unregisterAll(ownerId, namespace);
        }
    }

    Set<String> keys(String namespace) {
        Map<String, Entry> keys = namespaces.get(namespace);
        return keys == null ? Set.of() : Set.copyOf(new TreeSet<>(keys.keySet()));
    }

    /** The keys registered in {@code namespace}, sorted. */
    public Set<String> keysOf(String namespace) {
        return keys(namespace);
    }

    /** Every namespace with at least one placeholder, sorted. */
    public Set<String> namespaces() {
        return Set.copyOf(new TreeSet<>(namespaces.keySet()));
    }

    /** Fills in every placeholder in {@code text} that some namespace can answer. */
    public String apply(Object player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }

        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('%', index);
            if (open < 0) {
                out.append(text, index, text.length());
                break;
            }
            int close = text.indexOf('%', open + 1);
            if (close < 0) {
                out.append(text, index, text.length());
                break;
            }

            String token = text.substring(open + 1, close);
            String value = token.isEmpty() || containsWhitespace(token) ? null : resolveToken(player, token);
            out.append(text, index, open);
            if (value != null) {
                out.append(value);
                index = close + 1;
            } else {
                // Not ours to replace. Keep the opening % and look again from the closing one,
                // which may itself open the next placeholder ("100%%crates_total%").
                out.append('%');
                index = open + 1;
            }
        }
        return out.toString();
    }

    /** {@code namespace_rest} with the namespace matched longest-first against the registered ones. */
    private String resolveToken(Object player, String token) {
        String best = null;
        for (String namespace : namespaces.keySet()) {
            if (token.startsWith(namespace + "_") && (best == null || namespace.length() > best.length())) {
                best = namespace;
            }
        }
        return best == null ? null : resolve(best, token.substring(best.length() + 1), player);
    }

    /**
     * Resolves {@code params} within {@code namespace} - the PlaceholderAPI
     * bridge calls this directly, since PlaceholderAPI has already split off
     * the namespace. Matches the longest registered key that is either all of
     * {@code params} or followed by {@code _}; the rest is the argument.
     */
    public String resolve(String namespace, String params, Object player) {
        Map<String, Entry> keys = namespaces.get(namespace);
        if (keys == null) {
            return null;
        }

        String key = null;
        for (String candidate : keys.keySet()) {
            boolean matches = params.equals(candidate) || params.startsWith(candidate + "_");
            if (matches && (key == null || candidate.length() > key.length())) {
                key = candidate;
            }
        }
        if (key == null) {
            return null;
        }

        Entry entry = keys.get(key);
        String argument = params.length() > key.length() ? params.substring(key.length() + 1) : "";
        try {
            return entry.resolver().resolve(new Context(player, key, argument));
        } catch (RuntimeException | LinkageError e) {
            // Same isolation as the event bus: one owner's broken placeholder costs that
            // placeholder, not the whole line of text or the caller.
            LOGGER.log(Level.WARNING, "[CoffeeLib/" + entry.ownerId() + "] Placeholder %" + namespace + "_"
                    + params + "% failed", e);
            return null;
        }
    }

    private static boolean containsWhitespace(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void requireName(String name, String what) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new CoffeeLibException("Placeholder " + what + " '" + name + "' must be lower case letters, "
                    + "digits, '_' or '-', starting with a letter or digit");
        }
    }

    private record Entry(String ownerId, PlaceholderResolver resolver) {
    }

    private record Context(Object player, String key, String argument) implements PlaceholderContext {
    }
}
