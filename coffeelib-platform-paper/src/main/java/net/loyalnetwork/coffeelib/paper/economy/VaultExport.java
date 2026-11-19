package net.loyalnetwork.coffeelib.paper.economy;

import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A CoffeeLib currency offered to Vault plugins as the server's
 * {@link Economy}.
 * <p>
 * Vault is synchronous and CoffeeLib is not, so each call waits for the
 * provider's answer - at most {@link #TIMEOUT_SECONDS}. A provider that answers
 * from memory costs nothing; one that goes to a database blocks the calling
 * thread (often the server's) for that long, which is the price of Vault's API.
 * Banks are not part of CoffeeLib's model and report NOT_IMPLEMENTED; a
 * world argument is ignored.
 */
final class VaultExport implements Economy {

    private static final Logger LOGGER = Logger.getLogger(VaultExport.class.getName());
    static final long TIMEOUT_SECONDS = 5;

    private final Currency currency;
    private final Function<String, UUID> nameToId;

    /** @param nameToId a player name's UUID, for Vault's name-based methods */
    VaultExport(Currency currency, Function<String, UUID> nameToId) {
        this.currency = currency;
        this.nameToId = nameToId;
    }

    Currency currency() {
        return currency;
    }

    @Override
    public boolean isEnabled() {
        return currency.isAvailable();
    }

    @Override
    public String getName() {
        return "CoffeeLib:" + currency.id();
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return currency.fractionalDigits();
    }

    @Override
    public String format(double amount) {
        return currency.format(BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural() {
        return currency.displayName();
    }

    @Override
    public String currencyNameSingular() {
        return currency.displayName();
    }

    // --- accounts and balances

    @Override
    public boolean hasAccount(String player) {
        return hasAccount(nameToId.apply(player));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    private boolean hasAccount(UUID account) {
        Boolean has = await(currency.hasAccount(account), "hasAccount");
        return has != null && has;
    }

    @Override
    public double getBalance(String player) {
        return balance(nameToId.apply(player));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balance(player.getUniqueId());
    }

    @Override
    public double getBalance(String player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    private double balance(UUID account) {
        BigDecimal balance = await(currency.balance(account), "getBalance");
        return balance == null ? 0 : balance.doubleValue();
    }

    @Override
    public boolean has(String player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    // --- transactions

    @Override
    public EconomyResponse withdrawPlayer(String player, double amount) {
        return transaction(amount, value -> currency.withdraw(nameToId.apply(player), value));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return transaction(amount, value -> currency.withdraw(player.getUniqueId(), value));
    }

    @Override
    public EconomyResponse withdrawPlayer(String player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String player, double amount) {
        return transaction(amount, value -> currency.deposit(nameToId.apply(player), value));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return transaction(amount, value -> currency.deposit(player.getUniqueId(), value));
    }

    @Override
    public EconomyResponse depositPlayer(String player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse transaction(double amount, Function<BigDecimal, CompletableFuture<TransactionResult>> call) {
        // Vault's doubles, at the currency's precision: 0.1 + 0.2 arrives as 0.30000000000000004.
        BigDecimal value = BigDecimal.valueOf(amount).setScale(Math.max(currency.fractionalDigits(), 0), RoundingMode.HALF_EVEN);
        TransactionResult result = await(call.apply(value), "transaction");
        if (result == null) {
            return new EconomyResponse(amount, 0, ResponseType.FAILURE, "The economy did not answer in time");
        }
        double balance = result.balance() == null ? 0 : result.balance().doubleValue();
        return switch (result.status()) {
            case SUCCESS -> new EconomyResponse(value.doubleValue(), balance, ResponseType.SUCCESS, null);
            case NOT_SUPPORTED -> new EconomyResponse(amount, balance, ResponseType.NOT_IMPLEMENTED, result.message());
            default -> new EconomyResponse(amount, balance, ResponseType.FAILURE, result.message());
        };
    }

    private <T> T await(CompletableFuture<T> future, String what) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] Vault " + what + " on currency '" + currency.id() + "' failed",
                    e instanceof ExecutionException ? e.getCause() : e);
            return null;
        }
    }

    // --- banks: not part of CoffeeLib's model

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "CoffeeLib currencies have no banks");
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    // --- account creation: the provider opens accounts as it sees fit

    @Override
    public boolean createPlayerAccount(String player) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }
}
