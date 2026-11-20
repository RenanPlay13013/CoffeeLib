package net.loyalnetwork.coffeelib.api.economy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One currency, as the mod that owns it implements it.
 * <p>
 * CoffeeLib is only the channel: it holds no balances and decides nothing about
 * them. The provider keeps the accounts - in memory, a config, a database - and
 * answers every call. What CoffeeLib does before a call reaches the provider is
 * refuse amounts that cannot be right (negative, zero where a positive one is
 * needed, more decimals than {@link #fractionalDigits()}), so a provider only
 * ever sees valid amounts.
 * <p>
 * Every method returns a future, completed whenever the provider has the
 * answer - immediately for accounts held in memory, later for a database.
 * Calls can come from any thread, so a provider must be safe for that.
 * Providers capable of a database-atomic transfer should additionally implement
 * {@link AtomicTransferProvider}; otherwise {@link Currency#transfer} returns
 * {@link TransactionResult.Status#NOT_SUPPORTED} without debiting anyone.
 * Registered through {@code EconomyManager#register}; consumers reach it
 * through {@link Currency}, never directly.
 */
public interface CurrencyProvider {

    /**
     * The id consumers ask for, such as {@code "coins"}: lower case letters,
     * digits, {@code _} and {@code -}. Unique on the server.
     */
    String id();

    /** A name for people, such as {@code "Coins"}. */
    default String displayName() {
        return id();
    }

    /** Decimal places an amount may have: 0 for whole coins, 2 for cents. */
    default int fractionalDigits() {
        return 2;
    }

    /** {@code amount} written for a player, such as {@code "1,250 coins"}. */
    default String format(BigDecimal amount) {
        return amount.toPlainString() + " " + displayName();
    }

    /** Whether {@code account} has an account in this currency. */
    default CompletableFuture<Boolean> hasAccount(UUID account) {
        return CompletableFuture.completedFuture(true);
    }

    /** {@code account}'s balance. */
    CompletableFuture<BigDecimal> balance(UUID account);

    /** Adds {@code amount} (always positive) to {@code account}. */
    CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount);

    /**
     * Takes {@code amount} (always positive) from {@code account}, or fails
     * with {@link TransactionResult.Status#INSUFFICIENT_FUNDS} - whether a
     * balance may go negative is the provider's call.
     */
    CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount);

    /**
     * Sets {@code account}'s balance outright (never negative), for admin
     * commands. Providers that do not allow it keep this default, which
     * reports {@link TransactionResult.Status#NOT_SUPPORTED}.
     */
    default CompletableFuture<TransactionResult> set(UUID account, BigDecimal amount) {
        return CompletableFuture.completedFuture(TransactionResult.failure(TransactionResult.Status.NOT_SUPPORTED,
                id() + " does not allow setting a balance directly"));
    }
}
