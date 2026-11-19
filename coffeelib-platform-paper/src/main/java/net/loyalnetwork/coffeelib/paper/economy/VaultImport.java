package net.loyalnetwork.coffeelib.paper.economy;

import net.loyalnetwork.coffeelib.api.economy.CurrencyProvider;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult.Status;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Another plugin's Vault {@link Economy} - EssentialsX, CMI... - provided to
 * CoffeeLib as a currency, so mods and plugins on CoffeeLib reach it like any
 * other.
 * <p>
 * Vault economies expect the server thread, so every call runs there (at
 * once when already on it, on the next tick otherwise) and the future
 * completes with its answer. Vault has no way to set a balance, so
 * {@code set} keeps reporting NOT_SUPPORTED.
 */
final class VaultImport implements CurrencyProvider {

    private final String id;
    private final Economy economy;
    private final Executor serverThread;
    private final Function<UUID, OfflinePlayer> players;

    VaultImport(String id, Economy economy, Executor serverThread, Function<UUID, OfflinePlayer> players) {
        this.id = id;
        this.economy = economy;
        this.serverThread = serverThread;
        this.players = players;
    }

    Economy economy() {
        return economy;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return economy.currencyNamePlural();
    }

    @Override
    public int fractionalDigits() {
        int digits = economy.fractionalDigits();
        return digits < 0 ? 2 : digits; // Vault: -1 means "no limit"
    }

    @Override
    public String format(BigDecimal amount) {
        return economy.format(amount.doubleValue());
    }

    @Override
    public CompletableFuture<Boolean> hasAccount(UUID account) {
        return onServerThread(() -> economy.hasAccount(players.apply(account)));
    }

    @Override
    public CompletableFuture<BigDecimal> balance(UUID account) {
        return onServerThread(() -> BigDecimal.valueOf(economy.getBalance(players.apply(account))));
    }

    @Override
    public CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount) {
        return onServerThread(() -> result(economy.depositPlayer(players.apply(account), amount.doubleValue())));
    }

    @Override
    public CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount) {
        return onServerThread(() -> {
            OfflinePlayer player = players.apply(account);
            // Vault leaves "not enough" to each economy's error message; ask first, so the
            // status says it.
            if (!economy.has(player, amount.doubleValue())) {
                return TransactionResult.failure(Status.INSUFFICIENT_FUNDS, "Not enough " + economy.currencyNamePlural());
            }
            return result(economy.withdrawPlayer(player, amount.doubleValue()));
        });
    }

    private static TransactionResult result(EconomyResponse response) {
        if (response == null) {
            return TransactionResult.failure(Status.FAILED, "The Vault economy gave no response");
        }
        return switch (response.type) {
            case SUCCESS -> TransactionResult.success(BigDecimal.valueOf(response.balance));
            case NOT_IMPLEMENTED -> TransactionResult.failure(Status.NOT_SUPPORTED, response.errorMessage);
            default -> TransactionResult.failure(Status.FAILED, response.errorMessage);
        };
    }

    private <T> CompletableFuture<T> onServerThread(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        serverThread.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
