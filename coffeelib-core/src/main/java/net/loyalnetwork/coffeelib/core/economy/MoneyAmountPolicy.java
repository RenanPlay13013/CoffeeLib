package net.loyalnetwork.coffeelib.core.economy;

import java.math.BigDecimal;
import java.util.Objects;

/** Currency-local amount invariants, independent of provider dispatch or persistence. */
record MoneyAmountPolicy(int fractionalDigits, boolean zeroAllowed) {
    /** Returns null when an amount respects the currency's decimal scale and positivity rules. */
    String invalidReason(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0 || (!zeroAllowed && amount.signum() == 0)) {
            return "Amount must be " + (zeroAllowed ? "zero or more" : "more than zero")
                    + ", not " + amount.toPlainString();
        }
        if (amount.stripTrailingZeros().scale() > Math.max(fractionalDigits, 0)) {
            return "Amount " + amount.toPlainString() + " has more than " + fractionalDigits + " decimal place(s)";
        }
        return null;
    }
}
