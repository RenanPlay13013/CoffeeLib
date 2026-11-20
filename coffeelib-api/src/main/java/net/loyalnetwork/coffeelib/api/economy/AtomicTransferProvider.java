package net.loyalnetwork.coffeelib.api.economy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opt-in provider capability for a truly atomic transfer of one currency.
 * <p>
 * Implement this only if debiting {@code from} and crediting {@code to} are
 * one indivisible operation: either both balances are changed, or neither is.
 * For a database this requires a single transaction with concurrency control
 * (including rollback on any failure); for in-memory data use a common lock or
 * equivalent atomic state update. Locks in CoffeeLib cannot make separately
 * persisted provider operations crash-safe.
 * <p>
 * Providers must serialize conflicting account mutations, including standalone
 * deposits, withdrawals and balance-setting operations, against transfers;
 * balance reads must also use a compatible isolation mechanism.
 * If the destination is unavailable or funds are insufficient, complete with
 * a failure {@link TransactionResult} without changing either account.
 * Complete successfully only after both changes are committed. The successful
 * result's balance is the sender's balance after the transfer.
 * <p>
 * A failure to deliver the completion after a commit can make the outcome
 * uncertain for the caller; applications requiring exactly-once retries must
 * implement durable transaction IDs / idempotency at the provider level.
 * Do not implement this interface by calling withdraw then deposit.
 */
public interface AtomicTransferProvider extends CurrencyProvider {

    /**
     * Atomically moves a strictly positive, precision-validated amount.
     * Both UUIDs are non-null and distinct. The returned future must not be null.
     */
    CompletableFuture<TransactionResult> transfer(UUID from, UUID to, BigDecimal amount);
}
