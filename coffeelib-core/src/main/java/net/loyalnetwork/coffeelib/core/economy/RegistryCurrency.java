package net.loyalnetwork.coffeelib.core.economy;

import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.economy.CurrencyProvider;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult.Status;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Currency} handle: looks its provider up on every call, so it
 * follows providers registering, leaving and coming back. Its own logic is
 * the channel's only - amount checks, and a transfer built from the
 * provider's withdraw and deposit; the balances are the provider's.
 */
final class RegistryCurrency implements Currency {

    private static final Logger LOGGER = Logger.getLogger(RegistryCurrency.class.getName());

    private final String id;
    private final EconomyRegistry registry;

    RegistryCurrency(String id, EconomyRegistry registry) {
        this.id = id;
        this.registry = registry;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return registry.registration(id).isPresent();
    }

    @Override
    public Optional<String> providerOwner() {
        return registry.registration(id).map(EconomyRegistry.Registration::ownerId);
    }

    @Override
    public String displayName() {
        return provider().map(CurrencyProvider::displayName).orElse(id);
    }

    @Override
    public int fractionalDigits() {
        return provider().map(CurrencyProvider::fractionalDigits).orElse(2);
    }

    @Override
    public String format(BigDecimal amount) {
        return provider().map(provider -> provider.format(amount)).orElse(amount.toPlainString() + " " + id);
    }

    @Override
    public CompletableFuture<Boolean> hasAccount(UUID account) {
        Objects.requireNonNull(account, "account");
        return query(provider -> provider.hasAccount(account));
    }

    @Override
    public CompletableFuture<BigDecimal> balance(UUID account) {
        Objects.requireNonNull(account, "account");
        return query(provider -> provider.balance(account));
    }

    @Override
    public CompletableFuture<Boolean> has(UUID account, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return balance(account).thenApply(balance -> balance.compareTo(amount) >= 0);
    }

    @Override
    public CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount) {
        return transact(account, amount, false, provider -> provider.deposit(account, amount));
    }

    @Override
    public CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount) {
        return transact(account, amount, false, provider -> provider.withdraw(account, amount));
    }

    @Override
    public CompletableFuture<TransactionResult> set(UUID account, BigDecimal amount) {
        return transact(account, amount, true, provider -> provider.set(account, amount));
    }

    @Override
    public CompletableFuture<TransactionResult> transfer(UUID from, UUID to, BigDecimal amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failure(Status.INVALID_AMOUNT, "Cannot transfer to the same account"));
        }
        return withdraw(from, amount).thenCompose(withdrawn -> {
            if (!withdrawn.succeeded()) {
                return CompletableFuture.completedFuture(withdrawn);
            }
            return deposit(to, amount).thenCompose(deposited -> {
                if (deposited.succeeded()) {
                    return CompletableFuture.completedFuture(withdrawn); // the payer's balance, as a payer expects
                }
                // Give it back, and report why the transfer did not happen.
                return deposit(from, amount).thenApply(refunded -> {
                    if (!refunded.succeeded()) {
                        LOGGER.severe("[CoffeeLib] Transfer of " + amount + " " + id + " from " + from + " to " + to
                                + " failed on the deposit, and refunding " + from + " failed too ("
                                + refunded.message() + "); the amount is lost");
                    }
                    return deposited;
                });
            });
        });
    }

    private Optional<CurrencyProvider> provider() {
        return registry.registration(id).map(EconomyRegistry.Registration::provider);
    }

    private <T> CompletableFuture<T> query(Function<CurrencyProvider, CompletableFuture<T>> call) {
        Optional<CurrencyProvider> provider = provider();
        if (provider.isEmpty()) {
            return CompletableFuture.failedFuture(noProvider());
        }
        return callProvider(provider.get(), call);
    }

    private CompletableFuture<TransactionResult> transact(UUID account, BigDecimal amount, boolean zeroAllowed,
                                                          Function<CurrencyProvider, CompletableFuture<TransactionResult>> call) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Optional<CurrencyProvider> provider = provider();
        if (provider.isEmpty()) {
            return CompletableFuture.completedFuture(TransactionResult.failure(Status.NO_PROVIDER, noProvider().getMessage()));
        }
        String invalid = invalidAmount(amount, provider.get().fractionalDigits(), zeroAllowed);
        if (invalid != null) {
            return CompletableFuture.completedFuture(TransactionResult.failure(Status.INVALID_AMOUNT, invalid));
        }
        return callProvider(provider.get(), call).exceptionally(error -> {
            Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            LOGGER.log(Level.WARNING, "[CoffeeLib] The provider of '" + id + "' failed", cause);
            return TransactionResult.failure(Status.FAILED, String.valueOf(cause.getMessage()));
        });
    }

    /** A provider that throws instead of failing its future still gives a failed future. */
    private <T> CompletableFuture<T> callProvider(CurrencyProvider provider,
                                                  Function<CurrencyProvider, CompletableFuture<T>> call) {
        try {
            CompletableFuture<T> future = call.apply(provider);
            return future != null ? future : CompletableFuture.failedFuture(
                    new CoffeeLibException("The provider of '" + id + "' returned no future"));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    static String invalidAmount(BigDecimal amount, int fractionalDigits, boolean zeroAllowed) {
        int sign = amount.signum();
        if (sign < 0 || (sign == 0 && !zeroAllowed)) {
            return "Amount must be " + (zeroAllowed ? "zero or more" : "more than zero") + ", not " + amount.toPlainString();
        }
        if (amount.stripTrailingZeros().scale() > Math.max(fractionalDigits, 0)) {
            return "Amount " + amount.toPlainString() + " has more than " + fractionalDigits + " decimal place(s)";
        }
        return null;
    }

    private CoffeeLibException noProvider() {
        return new CoffeeLibException("No mod provides currency '" + id + "'");
    }
}
