package net.loyalnetwork.coffeelib.api.di.exception;

/**
 * Raised when {@code ServiceContainer#wire} can't find a provider matching
 * a {@code @Receive} field — whether because none was ever registered for
 * that type, the requested name doesn't match any of them, or the matching
 * provider simply hasn't been {@code register}-ed yet. Resolution is never
 * deferred, so all three look the same from here: nothing to inject, right
 * now.
 */
public class MissingProviderException extends ServiceException {

    private final Class<?> type;
    private final String requestedName;

    public MissingProviderException(String message, Class<?> type, String requestedName) {
        super(message);
        this.type = type;
        this.requestedName = requestedName;
    }

    public Class<?> getType() {
        return type;
    }

    /** The name from {@code @Receive}, or {@code null} if the field didn't specify one. */
    public String getRequestedName() {
        return requestedName;
    }
}
