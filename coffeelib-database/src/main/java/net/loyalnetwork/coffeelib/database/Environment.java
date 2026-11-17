package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.DatabaseException;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code ${NAME}} in a config value, replaced by the environment variable
 * {@code NAME} - so a password need not sit in a file (Docker, systemd and
 * hosting panels all set environment variables).
 */
final class Environment {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private Environment() {
    }

    static String resolve(String value, String key) {
        return resolve(value, key, System::getenv);
    }

    static String resolve(String value, String key, Function<String, String> variables) {
        if (value == null || value.indexOf("${") < 0) {
            return value;
        }
        Matcher matcher = REFERENCE.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = variables.apply(name);
            if (replacement == null) {
                throw new DatabaseException("database.yml: '" + key + "' refers to the environment variable " + name
                        + ", which is not set");
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
