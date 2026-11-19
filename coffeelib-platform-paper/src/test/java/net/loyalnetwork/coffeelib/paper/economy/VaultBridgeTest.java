package net.loyalnetwork.coffeelib.paper.economy;

import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.economy.CurrencyProvider;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult.Status;
import net.loyalnetwork.coffeelib.core.economy.SharedEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Both directions of the Vault bridge, with Vault and the server played by mocks. */
class VaultBridgeTest {

    /** A CoffeeLib provider of whole coins, kept in a map. */
    static final class Coins implements CurrencyProvider {
        final Map<UUID, BigDecimal> accounts = new ConcurrentHashMap<>();

        @Override
        public String id() {
            return "vaulttest-coins";
        }

        @Override
        public int fractionalDigits() {
            return 0;
        }

        @Override
        public CompletableFuture<BigDecimal> balance(UUID account) {
            return CompletableFuture.completedFuture(accounts.getOrDefault(account, BigDecimal.ZERO));
        }

        @Override
        public CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount) {
            return CompletableFuture.completedFuture(TransactionResult.success(accounts.merge(account, amount, BigDecimal::add)));
        }

        @Override
        public CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount) {
            BigDecimal balance = accounts.getOrDefault(account, BigDecimal.ZERO);
            if (balance.compareTo(amount) < 0) {
                return CompletableFuture.completedFuture(TransactionResult.failure(Status.INSUFFICIENT_FUNDS, "too poor"));
            }
            accounts.put(account, balance.subtract(amount));
            return CompletableFuture.completedFuture(TransactionResult.success(balance.subtract(amount)));
        }
    }

    private final UUID steve = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        SharedEconomy.cleanUp("essentials");
        SharedEconomy.cleanUp("EssentialsX");
    }

    private OfflinePlayer player(UUID id) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    @Test
    void aCoffeeLibCurrencyServesVaultPlugins() {
        Coins coins = new Coins();
        SharedEconomy.forOwner("essentials").register(coins);
        Currency handle = SharedEconomy.registry().currency("vaulttest-coins");
        VaultExport vault = new VaultExport(handle, name -> name.equals("Steve") ? steve : UUID.randomUUID());
        OfflinePlayer player = player(steve);

        assertTrue(vault.isEnabled());
        assertTrue(vault.depositPlayer(player, 100).transactionSuccess());
        assertEquals(100.0, vault.getBalance("Steve"), "name-based calls reach the same account");
        assertTrue(vault.has(player, 100));

        EconomyResponse tooMuch = vault.withdrawPlayer(player, 500);
        assertEquals(ResponseType.FAILURE, tooMuch.type);
        assertEquals("too poor", tooMuch.errorMessage);

        EconomyResponse taken = vault.withdrawPlayer(player, 30);
        assertEquals(ResponseType.SUCCESS, taken.type);
        assertEquals(70.0, taken.balance);
        assertEquals(new BigDecimal("70"), coins.accounts.get(steve), "the balance lives in the provider");
        assertEquals(ResponseType.NOT_IMPLEMENTED, vault.bankBalance("guild").type);
    }

    @Test
    void vaultDoublesAreRoundedToTheCurrencysPrecision() {
        SharedEconomy.forOwner("essentials").register(new Coins());
        VaultExport vault = new VaultExport(SharedEconomy.registry().currency("vaulttest-coins"), name -> steve);

        assertTrue(vault.depositPlayer(player(steve), 0.1 + 0.2 + 9.7).transactionSuccess(), "10.000000000000002 is 10");
        assertEquals(10.0, vault.getBalance(player(steve)));
    }

    @Test
    void withoutAProviderVaultSeesADisabledEconomyThatRefusesPolitely() {
        VaultExport vault = new VaultExport(SharedEconomy.registry().currency("vaulttest-nothing"), name -> steve);

        assertFalse(vault.isEnabled());
        assertEquals(ResponseType.FAILURE, vault.depositPlayer(player(steve), 5).type);
        assertEquals(0.0, vault.getBalance(player(steve)));
    }

    @Test
    void aVaultEconomyServesCoffeeLibOnTheServerThread() {
        Economy essentialsX = mock(Economy.class);
        OfflinePlayer player = player(steve);
        when(essentialsX.currencyNamePlural()).thenReturn("Dollars");
        when(essentialsX.fractionalDigits()).thenReturn(2);
        when(essentialsX.getBalance(player)).thenReturn(12.5);
        when(essentialsX.has(player, 5.0)).thenReturn(true);
        when(essentialsX.has(player, 50.0)).thenReturn(false);
        when(essentialsX.withdrawPlayer(player, 5.0)).thenReturn(new EconomyResponse(5, 7.5, ResponseType.SUCCESS, null));
        when(essentialsX.depositPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(0, 0, ResponseType.FAILURE, "frozen account"));
        boolean[] ranOnServerThread = {false};
        VaultImport provider = new VaultImport("vaulttest-dollars", essentialsX,
                task -> { ranOnServerThread[0] = true; task.run(); }, id -> player);
        SharedEconomy.forOwner("EssentialsX").register(provider);
        Currency dollars = SharedEconomy.registry().currency("vaulttest-dollars");

        assertEquals(new BigDecimal("12.5"), dollars.balance(steve).join());
        assertTrue(ranOnServerThread[0], "Vault was called through the server-thread executor");
        assertEquals("Dollars", dollars.displayName());

        TransactionResult taken = dollars.withdraw(steve, new BigDecimal("5")).join();
        assertTrue(taken.succeeded());
        assertEquals(new BigDecimal("7.5"), taken.balance());

        assertEquals(Status.INSUFFICIENT_FUNDS, dollars.withdraw(steve, new BigDecimal("50")).join().status());
        verify(essentialsX, never()).withdrawPlayer(player, 50.0);

        TransactionResult refused = dollars.deposit(steve, BigDecimal.ONE).join();
        assertEquals(Status.FAILED, refused.status());
        assertEquals("frozen account", refused.message());
        assertEquals(Status.NOT_SUPPORTED, dollars.set(steve, BigDecimal.TEN).join().status(), "Vault cannot set");
        assertEquals("EssentialsX", dollars.providerOwner().orElseThrow(), "owned by the plugin behind it");
    }
}
