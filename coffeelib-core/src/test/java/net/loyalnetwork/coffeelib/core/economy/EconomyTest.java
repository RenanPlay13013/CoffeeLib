package net.loyalnetwork.coffeelib.core.economy;

import net.loyalnetwork.coffeelib.api.economy.AtomicTransferProvider;
import net.loyalnetwork.coffeelib.api.economy.Currency;
import net.loyalnetwork.coffeelib.api.economy.CurrencyProvider;
import net.loyalnetwork.coffeelib.api.economy.EconomyManager;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult;
import net.loyalnetwork.coffeelib.api.economy.TransactionResult.Status;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The channel between providers and consumers. The provider here is an
 * in-memory stand-in for the owning mod: the balances live in it, never in
 * the registry.
 */
class EconomyTest {

    /** A provider keeping balances in a map, with whole units and an optional broken deposit. */
    static class MapProvider implements AtomicTransferProvider {
        final String id;
        final int digits;
        final Map<UUID, BigDecimal> accounts = new ConcurrentHashMap<>();
        final List<BigDecimal> seen = new ArrayList<>();
        UUID refuseDepositsTo;

        MapProvider(String id, int digits) {
            this.id = id;
            this.digits = digits;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int fractionalDigits() {
            return digits;
        }

        @Override
        public synchronized CompletableFuture<BigDecimal> balance(UUID account) {
            return CompletableFuture.completedFuture(accounts.getOrDefault(account, BigDecimal.ZERO));
        }

        @Override
        public synchronized CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount) {
            seen.add(amount);
            if (account.equals(refuseDepositsTo)) {
                return CompletableFuture.completedFuture(TransactionResult.failure(Status.NO_ACCOUNT, "closed account"));
            }
            return CompletableFuture.completedFuture(TransactionResult.success(accounts.merge(account, amount, BigDecimal::add)));
        }

        @Override
        public synchronized CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount) {
            seen.add(amount);
            BigDecimal balance = accounts.getOrDefault(account, BigDecimal.ZERO);
            if (balance.compareTo(amount) < 0) {
                return CompletableFuture.completedFuture(TransactionResult.failure(Status.INSUFFICIENT_FUNDS, "too poor"));
            }
            BigDecimal left = balance.subtract(amount);
            accounts.put(account, left);
            return CompletableFuture.completedFuture(TransactionResult.success(left));
        }

        @Override
        public synchronized CompletableFuture<TransactionResult> transfer(UUID from, UUID to, BigDecimal amount) {
            // This test provider holds both account mutations under the same
            // lock. Real providers must do the equivalent in one DB transaction.
            seen.add(amount);
            if (to.equals(refuseDepositsTo)) {
                return CompletableFuture.completedFuture(TransactionResult.failure(Status.NO_ACCOUNT, "closed account"));
            }
            BigDecimal balance = accounts.getOrDefault(from, BigDecimal.ZERO);
            if (balance.compareTo(amount) < 0) {
                return CompletableFuture.completedFuture(TransactionResult.failure(Status.INSUFFICIENT_FUNDS, "too poor"));
            }
            BigDecimal left = balance.subtract(amount);
            accounts.put(from, left);
            accounts.merge(to, amount, BigDecimal::add);
            return CompletableFuture.completedFuture(TransactionResult.success(left));
        }
    }

    private final EconomyRegistry registry = new EconomyRegistry();
    private final EconomyManager essentials = new SharedEconomy.OwnerEconomy(registry, "essentials");
    private final EconomyManager shopMod = new SharedEconomy.OwnerEconomy(registry, "shopmod");
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @Test
    void consumersReachEachCurrencyThroughItsProvider() {
        MapProvider coins = new MapProvider("coins", 0);
        MapProvider cash = new MapProvider("cash", 2);
        essentials.register(coins);
        essentials.register(cash);

        Currency shopCoins = shopMod.currency("coins");
        Currency questCash = new SharedEconomy.OwnerEconomy(registry, "questmod").currency("cash");

        assertTrue(shopCoins.deposit(steve, new BigDecimal("100")).join().succeeded());
        assertTrue(questCash.deposit(steve, new BigDecimal("2.50")).join().succeeded());

        assertEquals(new BigDecimal("100"), coins.accounts.get(steve), "the balance lives in the provider");
        assertEquals(new BigDecimal("2.50"), cash.accounts.get(steve));
        assertEquals(new BigDecimal("100"), shopCoins.balance(steve).join());
        assertEquals(java.util.Set.of("cash", "coins"), shopMod.currencies());
        assertEquals("essentials", shopCoins.providerOwner().orElseThrow());
    }

    @Test
    void aHandleTakenBeforeItsProviderWorksOnceTheProviderArrives() {
        Currency coins = shopMod.currency("coins");
        assertFalse(coins.isAvailable());
        assertEquals(Status.NO_PROVIDER, coins.deposit(steve, BigDecimal.ONE).join().status());
        CompletionException noBalance = assertThrows(CompletionException.class, () -> coins.balance(steve).join());
        assertTrue(noBalance.getCause() instanceof CoffeeLibException);

        essentials.register(new MapProvider("coins", 0));

        assertTrue(coins.isAvailable());
        assertTrue(coins.deposit(steve, BigDecimal.ONE).join().succeeded());
        assertSame(coins, shopMod.currency("COINS"), "one handle per id, ids case-insensitive");
    }

    @Test
    void listenersHearAProviderArrivingOrAlreadyThere() {
        List<String> heard = new ArrayList<>();
        shopMod.onAvailable("coins", currency -> heard.add("shop:" + currency.id()));
        essentials.register(new MapProvider("coins", 0));
        shopMod.onAvailable("coins", currency -> heard.add("late:" + currency.id()));

        assertEquals(List.of("shop:coins", "late:coins"), heard);
    }

    @Test
    void invalidAmountsNeverReachTheProvider() {
        MapProvider coins = new MapProvider("coins", 0);
        essentials.register(coins);
        Currency handle = shopMod.currency("coins");

        assertEquals(Status.INVALID_AMOUNT, handle.deposit(steve, new BigDecimal("-5")).join().status());
        assertEquals(Status.INVALID_AMOUNT, handle.withdraw(steve, BigDecimal.ZERO).join().status());
        assertEquals(Status.INVALID_AMOUNT, handle.deposit(steve, new BigDecimal("1.5")).join().status(),
                "coins are whole");
        assertTrue(handle.deposit(steve, new BigDecimal("2.00")).join().succeeded(), "2.00 is a whole 2");
        assertEquals(List.of(new BigDecimal("2.00")), coins.seen, "only the valid amount reached the provider");
    }

    @Test
    void theProviderDecidesFundsAndHasFollowsItsBalance() {
        essentials.register(new MapProvider("coins", 0));
        Currency coins = shopMod.currency("coins");
        coins.deposit(steve, new BigDecimal("30")).join();

        assertTrue(coins.has(steve, new BigDecimal("30")).join());
        assertFalse(coins.has(steve, new BigDecimal("31")).join());
        assertEquals(Status.INSUFFICIENT_FUNDS, coins.withdraw(steve, new BigDecimal("31")).join().status());
        assertEquals(new BigDecimal("10"), coins.withdraw(steve, new BigDecimal("20")).join().balance());
    }

    @Test
    void anAtomicTransferMovesBothBalancesOrNeither() {
        MapProvider coins = new MapProvider("coins", 0);
        essentials.register(coins);
        Currency handle = shopMod.currency("coins");
        handle.deposit(steve, new BigDecimal("100")).join();

        assertTrue(handle.transfer(steve, alex, new BigDecimal("40")).join().succeeded());
        assertEquals(new BigDecimal("60"), coins.accounts.get(steve));
        assertEquals(new BigDecimal("40"), coins.accounts.get(alex));

        assertEquals(Status.INSUFFICIENT_FUNDS, handle.transfer(steve, alex, new BigDecimal("500")).join().status());

        coins.refuseDepositsTo = alex;
        TransactionResult refused = handle.transfer(steve, alex, new BigDecimal("10")).join();
        assertEquals(Status.NO_ACCOUNT, refused.status(), "the deposit's failure is the transfer's");
        assertEquals(new BigDecimal("60"), coins.accounts.get(steve), "failed transfers do not debit either account");
        assertEquals(new BigDecimal("40"), coins.accounts.get(alex));
    }

    @Test
    void providerWithoutAtomicTransferIsRejectedWithoutAnyDebit() {
        MapProvider coins = new MapProvider("coins", 0);
        essentials.register(coins);
        Currency handle = shopMod.currency("coins");
        handle.deposit(steve, new BigDecimal("100")).join();
        // A legacy provider can withdraw money without guaranteeing the recipient
        // deposit or compensation. Reject before either callback runs.
        CurrencyProvider failingRefund = new CurrencyProvider() {
            public String id() { return "coins"; }
            public int fractionalDigits() { return 0; }
            public CompletableFuture<BigDecimal> balance(UUID id) { return coins.balance(id); }
            public CompletableFuture<TransactionResult> withdraw(UUID id, BigDecimal amt) {
                return coins.withdraw(id, amt);
            }
            public CompletableFuture<TransactionResult> deposit(UUID id, BigDecimal amt) {
                return CompletableFuture.completedFuture(TransactionResult.failure(Status.FAILED, "storage offline"));
            }
        };
        essentials.register(failingRefund); // same owner may replace its provider

        TransactionResult result = handle.transfer(steve, alex, BigDecimal.TEN).join();
        assertEquals(Status.NOT_SUPPORTED, result.status());
        assertTrue(result.message().contains("atomic"));
        assertEquals(new BigDecimal("100"), coins.accounts.get(steve));
        assertEquals(BigDecimal.ZERO, coins.accounts.getOrDefault(alex, BigDecimal.ZERO));
    }

    @Test
    void atomicTransferRejectsInvalidAmountsWithoutDispatching() {
        MapProvider provider = new MapProvider("coins", 0);
        essentials.register(provider);
        Currency handle = shopMod.currency("coins");

        assertEquals(Status.INVALID_AMOUNT, handle.transfer(steve, steve, BigDecimal.ONE).join().status());
        assertEquals(Status.INVALID_AMOUNT, handle.transfer(steve, alex, BigDecimal.ZERO).join().status());
        assertEquals(Status.INVALID_AMOUNT, handle.transfer(steve, alex, new BigDecimal("1.5")).join().status());
        assertTrue(provider.seen.isEmpty(), "invalid transfers must never reach the provider");
    }

    @Test
    void providerExceptionOrNullResultFailsWithoutCompensatingOperations() {
        MapProvider accounts = new MapProvider("coins", 0);
        essentials.register(accounts);
        accounts.deposit(steve, BigDecimal.TEN).join();
        java.util.concurrent.atomic.AtomicInteger transferCalls = new java.util.concurrent.atomic.AtomicInteger();
        AtomicTransferProvider broken = new AtomicTransferProvider() {
            public String id() { return "coins"; }
            public int fractionalDigits() { return 0; }
            public CompletableFuture<BigDecimal> balance(UUID account) { return accounts.balance(account); }
            public CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount) {
                throw new AssertionError("The library must never attempt compensation");
            }
            public CompletableFuture<TransactionResult> withdraw(UUID account, BigDecimal amount) {
                throw new AssertionError("The library must never attempt a standalone debit");
            }
            public CompletableFuture<TransactionResult> transfer(UUID from, UUID to, BigDecimal amount) {
                return switch (transferCalls.incrementAndGet()) {
                    case 1 -> CompletableFuture.failedFuture(new IllegalStateException("database error"));
                    default -> CompletableFuture.completedFuture(null);
                };
            }
        };
        essentials.register(broken);
        Currency handle = shopMod.currency("coins");
        assertEquals(Status.FAILED, handle.transfer(steve, alex, BigDecimal.ONE).join().status());
        assertEquals(Status.FAILED, handle.transfer(steve, alex, BigDecimal.ONE).join().status());
        assertEquals(2, transferCalls.get());
        assertEquals(BigDecimal.TEN, accounts.accounts.get(steve));
    }

    @Test
    void concurrentTransfersMaintainTheTotalWithARealAtomicProvider() {
        MapProvider accounts = new MapProvider("coins", 0);
        essentials.register(accounts);
        accounts.deposit(steve, new BigDecimal("100")).join();
        Currency handle = shopMod.currency("coins");

        CompletableFuture<?>[] operations = new CompletableFuture<?>[60];
        for (int i = 0; i < operations.length; i++) {
            operations[i] = CompletableFuture.supplyAsync(() -> handle.transfer(steve, alex, BigDecimal.ONE).join())
                    .thenAccept(result -> assertTrue(result.succeeded()));
        }
        CompletableFuture.allOf(operations).join();
        assertEquals(new BigDecimal("40"), accounts.accounts.get(steve));
        assertEquals(new BigDecimal("60"), accounts.accounts.get(alex));
    }

    @Test
    void setIsTheProvidersChoice() {
        essentials.register(new MapProvider("coins", 0));
        assertEquals(Status.NOT_SUPPORTED, shopMod.currency("coins").set(steve, BigDecimal.TEN).join().status());
    }

    @Test
    void aBrokenProviderFailsTheTransactionInsteadOfThrowing() {
        essentials.register(new MapProvider("coins", 0) {
            @Override
            public CompletableFuture<TransactionResult> deposit(UUID account, BigDecimal amount) {
                throw new IllegalStateException("database down");
            }
        });

        TransactionResult result = shopMod.currency("coins").deposit(steve, BigDecimal.ONE).join();
        assertEquals(Status.FAILED, result.status());
        assertEquals("database down", result.message());
    }

    @Test
    void aCurrencyHasOneProviderAndOnlyItsOwnerRemovesIt() {
        essentials.register(new MapProvider("coins", 0));

        CoffeeLibException taken = assertThrows(CoffeeLibException.class,
                () -> shopMod.register(new MapProvider("coins", 0)));
        assertTrue(taken.getMessage().contains("essentials"), taken.getMessage());

        shopMod.unregister("coins");
        assertTrue(shopMod.currency("coins").isAvailable(), "not shopmod's to remove");

        essentials.unregisterAll();
        assertFalse(shopMod.currency("coins").isAvailable());
        assertThrows(CoffeeLibException.class, () -> shopMod.currency("bad id!"));
    }

    @Test
    void anOwnerLeavingTakesItsProvidersAndListenersWithIt() {
        List<String> heard = new ArrayList<>();
        shopMod.onAvailable("coins", currency -> heard.add("shop"));
        essentials.register(new MapProvider("coins", 0));
        registry.removeOwner("essentials");
        registry.removeOwner("shopmod");

        essentials.register(new MapProvider("coins", 0));
        assertEquals(List.of("shop"), heard, "shopmod's listener left with it");
    }
}
