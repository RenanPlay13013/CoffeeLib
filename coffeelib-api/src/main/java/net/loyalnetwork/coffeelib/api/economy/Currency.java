package net.loyalnetwork.coffeelib.api.economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A currency, as a consumer sees it: a handle to whichever mod provides it.
 * <p>
 * A handle can be taken before its provider registers - mods load in any order
 * - and keeps working across a provider coming, going and coming back. While no
 * mod provides it, {@link #isAvailable()} is {@code false}, balances fail with a
 * {@link net.loyalnetwork.coffeelib.api.exception.CoffeeLibException} and
 * transactions report {@link TransactionResult.Status#NO_PROVIDER}.
 * <p>
 * Amounts are checked before they reach the provider: a negative or zero
 * amount, or one with more decimals than {@link #fractionalDigits()}, comes
 * back as {@link TransactionResult.Status#INVALID_AMOUNT}.
 * <p>
 * Futures complete on whatever thread the provider answers from. Get back to
 * the server thread with the platform's scheduler before touching the world.
 */
public interface Currency {

    String id();

    /** Whether a mod provides this currency right now. */
    boolean isAvailable();

    /** The owner (mod or plugin id) of the current provider, if any. */
    Optional<String> providerOwner();

    String displayName();

    int fractionalDigits();

    String format(BigDecimal amount);

    CompletableFuture<Boolean> hasAccount(UUID account);

    CompletableFuture<BigDecimal> balance(UUID account);

    /** Whether {@code account} has at least {@code amount}. */
    CompletableFuture<Boolean> has(UUID account, BigDecimal amount);

    CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount);

    CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount);

    CompletableFuture<TransactionResult> set(UUID account, BigDecimal amount);

    /**
     * Moves {@code amount} from one account to another: a withdrawal, then a
     * deposit. If the deposit fails, the withdrawn amount is deposited back and
     * the result is the deposit's failure. Not atomic in the database sense -
     * the provider sees two operations - so a server stopping between the two
     * can lose the amount in flight.
     */
    CompletableFuture<TransactionResult> transfer(UUID from, UUID to, BigDecimal amount);
}
