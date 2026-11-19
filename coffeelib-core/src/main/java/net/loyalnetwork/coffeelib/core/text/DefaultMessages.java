package net.loyalnetwork.coffeelib.core.text;

import net.loyalnetwork.coffeelib.api.text.Messages;
import net.loyalnetwork.coffeelib.api.config.ConfigReloadService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Owns a catalog and the delivery policy, leaving formatting to the platform adapter. */
public final class DefaultMessages<T> implements Messages<T> {
    private final T catalog;
    // Keeps the reload service alive while the catalog is in use.
    private final ConfigReloadService reloadService;
    private final BiConsumer<Object, String> delivery;

    public DefaultMessages(T catalog, ConfigReloadService reloadService, BiConsumer<Object, String> delivery) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.reloadService = Objects.requireNonNull(reloadService, "reloadService");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
    }

    @Override
    public String render(Function<T, String> template, Object... namesAndValues) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(namesAndValues, "namesAndValues");
        if ((namesAndValues.length & 1) != 0) {
            throw new IllegalArgumentException("Placeholders need name/value pairs");
        }
        Map<String, String> replacements = replacements(namesAndValues);
        String message = Objects.requireNonNull(template.apply(catalog), "message template");
        return fill(message, replacements);
    }

    @Override
    public boolean reload() {
        return reloadService.reloadAll() > 0;
    }

    @Override
    public void send(Object recipient, Function<T, String> template, Object... namesAndValues) {
        Objects.requireNonNull(recipient, "recipient");
        delivery.accept(recipient, render(template, namesAndValues));
    }

    private static Map<String, String> replacements(Object[] pairs) {
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

    /** One pass: replacement values are never interpreted as further templates. */
    private static String fill(String message, Map<String, String> replacements) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            if (message.charAt(i) == '\\' && i + 1 < message.length() && message.charAt(i + 1) == '{') {
                result.append('{');
                i++;
                continue;
            }
            if (message.charAt(i) != '{') {
                result.append(message.charAt(i));
                continue;
            }
            int closing = message.indexOf('}', i + 1);
            if (closing < 0) {
                result.append('{');
                continue;
            }
            String name = message.substring(i + 1, closing);
            if (!replacements.containsKey(name)) {
                result.append('{').append(name).append('}');
            } else {
                result.append(replacements.get(name));
            }
            i = closing;
        }
        return result.toString();
    }
}
