package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.util.Map;
import java.util.function.Predicate;

/** Pure domain rules for numeric permission grants. No platform types or I/O. */
public final class PermissionNumbers {
    private PermissionNumbers() { }

    public static String requirePrefix(String prefix) {
        PermissionNodes.requireValid(prefix);
        if (prefix.contains("*")) {
            throw new CoffeeLibException("Numeric permission prefix must not contain wildcards: " + prefix);
        }
        return prefix;
    }

    /** Returns null for a missing, negative, malformed or overflowing number. */
    public static Integer parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        for (int index = 0; index < raw.length(); index++) {
            if (raw.charAt(index) < '0' || raw.charAt(index) > '9') return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException overflowing) {
            return null;
        }
    }

    public static Integer suffix(String prefix, String candidate) {
        String stem = prefix + ".";
        return candidate != null && candidate.startsWith(stem)
                ? parse(candidate.substring(stem.length())) : null;
    }

    /** Ignores negated grants, unrelated nodes and invalid suffixes. */
    public static Integer highestGranted(String prefix, Map<String, Boolean> grants) {
        requirePrefix(prefix);
        Integer result = null;
        for (var entry : grants.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            Integer value = suffix(prefix, entry.getKey());
            if (value != null && (result == null || value > result)) result = value;
        }
        return result;
    }

    /** Only declared range nodes are queried; a denied node never contributes. */
    public static int highestDeclared(String prefix, Iterable<String> declared,
                                      Predicate<String> granted, int fallback) {
        requirePrefix(prefix);
        int best = fallback;
        boolean found = false;
        for (String node : declared) {
            Integer value = suffix(prefix, node);
            if (value == null || !granted.test(node)) continue;
            if (!found || value > best) best = value;
            found = true;
        }
        return found ? best : fallback;
    }
}
