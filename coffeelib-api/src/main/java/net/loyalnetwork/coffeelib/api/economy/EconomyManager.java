package net.loyalnetwork.coffeelib.api.economy;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The economy channel, for one owner (a mod or plugin): providing currencies,
 * and using anyone's.
 * <pre>{@code
 * // The mod that owns the coins
 * CoffeeLib.economy(container).register(new CoinsProvider(...));
 *
 * // Any mod that charges coins
 * Currency coins = CoffeeLib.economy(container).currency("coins");
 * coins.withdraw(player.getUUID(), new BigDecimal("50")).thenAccept(result -> ...);
 * }</pre>
 * CoffeeLib holds no balances: every call is answered by the provider.
 */
public interface EconomyManager {

    /**
     * Makes {@code provider} the source of its currency for every mod.
     *
     * @throws net.loyalnetwork.coffeelib.api.exception.CoffeeLibException when
     *         another owner already provides that currency, or the id is invalid
     */
    void register(CurrencyProvider provider);

    /** Withdraws this owner's provider of {@code currencyId}, if it is this owner's. */
    void unregister(String currencyId);

    /** Withdraws every currency this owner provides. Done for you when the owner shuts down. */
    void unregisterAll();

    /**
     * A handle to {@code currencyId}, whether or not it is provided yet - see
     * {@link Currency}. The same id always gives a working handle, so it is
     * fine to take it once, at startup.
     */
    Currency currency(String currencyId);

    /** The ids of the currencies provided right now, by anyone. */
    Set<String> currencies();

    /**
     * Calls {@code listener} with {@code currencyId}'s handle whenever a
     * provider for it registers - and right away if one already has.
     */
    void onAvailable(String currencyId, Consumer<Currency> listener);
}
