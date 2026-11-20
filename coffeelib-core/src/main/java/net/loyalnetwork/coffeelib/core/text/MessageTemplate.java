package net.loyalnetwork.coffeelib.core.text;

import java.util.Map;

/** Stateless renderer: one-pass interpolation, with escaping but no recursive evaluation. */
final class MessageTemplate {
    private MessageTemplate() { }

    /** One pass: replacement values are never interpreted as further templates. */
    static String render(String message, Map<String, String> replacements) {
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
