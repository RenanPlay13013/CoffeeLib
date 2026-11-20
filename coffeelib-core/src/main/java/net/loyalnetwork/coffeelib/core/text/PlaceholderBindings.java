package net.loyalnetwork.coffeelib.core.text;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable validated placeholder bindings, independent of message delivery. */
final class PlaceholderBindings {
    private final Map<String, String> values;

    private PlaceholderBindings(Map<String, String> values) { this.values = Map.copyOf(values); }

    static PlaceholderBindings of(Object... namesAndValues) {
        java.util.Objects.requireNonNull(namesAndValues, "namesAndValues");
        if ((namesAndValues.length & 1) != 0) {
            throw new IllegalArgumentException("Placeholders need name/value pairs");
        }
        return new PlaceholderBindings(fromPairs(namesAndValues));
    }

    Map<String, String> values() { return values; }

    static Map<String, String> fromPairs(Object[] pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (!(pairs[i] instanceof String name) || !name.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Placeholder names must be non-empty strings of letters, numbers, . _ -");
            }
            if (result.putIfAbsent(name, String.valueOf(pairs[i + 1])) != null) {
                throw new IllegalArgumentException("Duplicate placeholder: " + name);
            }
        }
        return result;
    }

}
