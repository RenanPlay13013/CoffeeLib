package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Argument types that need no platform: {@code Duration} and {@code UUID}. */
final class CoreArguments {

    private CoreArguments() {
    }

    static void registerInto(ArgumentTypes types) {
        types.register(Duration.class, new DurationArgument());
        types.register(UUID.class, new UuidArgument());
    }

    /**
     * {@code 1d2h30m10s}, any of the parts, in that order: days, hours,
     * minutes, seconds. A bare number is seconds.
     */
    static final class DurationArgument implements CommandArgument<Duration> {

        private static final Pattern FORMAT = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

        @Override
        public Duration parse(CommandSource source, String input) {
            String text = input.toLowerCase(Locale.ROOT);
            try {
                if (!text.isEmpty() && text.chars().allMatch(Character::isDigit)) {
                    return Duration.ofSeconds(Long.parseLong(text));
                }
                Matcher matcher = FORMAT.matcher(text);
                if (!text.isEmpty() && matcher.matches()) {
                    return Duration.ofDays(part(matcher, 1))
                            .plusHours(part(matcher, 2))
                            .plusMinutes(part(matcher, 3))
                            .plusSeconds(part(matcher, 4));
                }
            } catch (NumberFormatException | ArithmeticException e) {
                // Too large to be a duration: told as malformed, below.
            }
            throw new CommandException("'" + input + "' is not a duration, such as 30s, 10m, 2h or 1d12h.");
        }

        private static long part(Matcher matcher, int group) {
            String value = matcher.group(group);
            return value == null ? 0 : Long.parseLong(value);
        }

        @Override
        public Collection<String> suggest(SuggestContext context) {
            return List.of("30s", "5m", "1h", "1d");
        }
    }

    static final class UuidArgument implements CommandArgument<UUID> {
        @Override
        public UUID parse(CommandSource source, String input) {
            try {
                return UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                throw new CommandException("'" + input + "' is not a UUID.");
            }
        }
    }
}
