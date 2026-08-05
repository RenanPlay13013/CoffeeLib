package net.loyalnetwork.coffeelib.api.exception;

import java.util.List;

/**
 * Raised when a loaded value fails a constraint declared on the field (e.g.
 * {@link net.loyalnetwork.coffeelib.api.annotation.Range},
 * {@link net.loyalnetwork.coffeelib.api.annotation.OneOf}).
 * <p>
 * Carries the offending field/value plus, when the validator can produce
 * one, a ranked list of suggestions (closest match first) — so callers can
 * build their own "did you mean X?" messaging instead of parsing
 * {@link #getMessage()}.
 */
public class ConfigValidationException extends ConfigException {

    private final String fieldName;
    private final Object invalidValue;
    private final List<String> suggestions;

    public ConfigValidationException(String message, String fieldName, Object invalidValue) {
        this(message, fieldName, invalidValue, List.of());
    }

    public ConfigValidationException(String message, String fieldName, Object invalidValue, List<String> suggestions) {
        super(message);
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
        this.suggestions = suggestions;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }

    /** Closest-match suggestions, ranked best-first. Empty when the validator has none to offer. */
    public List<String> getSuggestions() {
        return suggestions;
    }
}
