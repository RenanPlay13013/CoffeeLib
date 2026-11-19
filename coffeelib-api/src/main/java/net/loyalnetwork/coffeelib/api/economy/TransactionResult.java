package net.loyalnetwork.coffeelib.api.economy;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * How a deposit, withdrawal, set or transfer went.
 *
 * @param status  what happened
 * @param balance the account's balance afterwards, when the provider reports
 *                it; {@code null} otherwise (and on most failures)
 * @param message why it failed, for logs and players; empty on success
 */
public record TransactionResult(Status status, BigDecimal balance, String message) {

    public enum Status {
        SUCCESS,
        /** The account does not have that much. */
        INSUFFICIENT_FUNDS,
        /** Negative, zero, or with more decimals than the currency has. Refused before reaching the provider. */
        INVALID_AMOUNT,
        /** No such account in this currency. */
        NO_ACCOUNT,
        /** No mod provides this currency right now. */
        NO_PROVIDER,
        /** The provider does not do this ({@code set}, say). */
        NOT_SUPPORTED,
        /** Anything else: the provider failed, or refused for a reason of its own. */
        FAILED
    }

    public TransactionResult {
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
    }

    public static TransactionResult success(BigDecimal balance) {
        return new TransactionResult(Status.SUCCESS, balance, "");
    }

    public static TransactionResult failure(Status status, String message) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("A failure needs a failing status");
        }
        return new TransactionResult(status, null, message);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }
}
