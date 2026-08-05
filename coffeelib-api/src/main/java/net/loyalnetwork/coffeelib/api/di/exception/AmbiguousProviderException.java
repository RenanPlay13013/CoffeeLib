package net.loyalnetwork.coffeelib.api.di.exception;

import java.util.List;

/**
 * Raised when a {@code @Receive} field names no provider, but more than one
 * is registered for its type — the container refuses to guess. Carries the
 * registered names so the caller (or the error message) can point the dev
 * at exactly which {@code @Receive("name")} to add.
 */
public class AmbiguousProviderException extends ServiceException {

    private final Class<?> type;
    private final List<String> candidateNames;

    public AmbiguousProviderException(String message, Class<?> type, List<String> candidateNames) {
        super(message);
        this.type = type;
        this.candidateNames = candidateNames;
    }

    public Class<?> getType() {
        return type;
    }

    public List<String> getCandidateNames() {
        return candidateNames;
    }
}
