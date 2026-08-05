package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigValidationException;

import java.util.List;

/** Applies {@code @Range}/{@code @OneOf} to a decoded field value. */
final class ConfigValidator {

    private ConfigValidator() {
    }

    static void validate(FieldMetadata metadata, Object value) {
        if (value == null) {
            return;
        }

        Range range = metadata.getRange();
        if (range != null) {
            validateRange(metadata, range, value);
        }

        OneOf oneOf = metadata.getOneOf();
        if (oneOf != null) {
            validateOneOf(metadata, oneOf, value);
        }
    }

    private static void validateRange(FieldMetadata metadata, Range range, Object value) {
        if (!(value instanceof Number number)) {
            throw new ConfigValidationException(
                    "@Range on non-numeric field '" + metadata.getKey() + "'", metadata.getKey(), value);
        }

        double asDouble = number.doubleValue();
        if (asDouble < range.min() || asDouble > range.max()) {
            String message = "Value " + value + " for '" + metadata.getKey() + "' is outside range ["
                    + range.min() + ", " + range.max() + "]";
            throw new ConfigValidationException(message, metadata.getKey(), value);
        }
    }

    private static void validateOneOf(FieldMetadata metadata, OneOf oneOf, Object value) {
        List<String> allowed = List.of(oneOf.value());
        String stringValue = String.valueOf(value);
        if (allowed.contains(stringValue)) {
            return;
        }

        List<String> suggestions = Suggestions.closest(stringValue, allowed, 3);
        String message = "Value '" + stringValue + "' for '" + metadata.getKey() + "' must be one of " + allowed
                + (suggestions.isEmpty() ? "" : " — did you mean '" + suggestions.get(0) + "'?");
        throw new ConfigValidationException(message, metadata.getKey(), value, suggestions);
    }
}
