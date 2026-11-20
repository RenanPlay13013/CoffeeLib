package net.loyalnetwork.coffeelib.core.economy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MoneyAmountPolicyTest {
    @Test void wholeCurrencyAllowsTrailingZerosButNotFractions() {
        MoneyAmountPolicy policy = new MoneyAmountPolicy(0, false);
        assertNull(policy.invalidReason(new BigDecimal("10.000")));
        assertNotNull(policy.invalidReason(new BigDecimal("10.001")));
        assertNotNull(policy.invalidReason(BigDecimal.ZERO));
    }

    @Test void setBalanceAllowsZeroButNotNegatives() {
        MoneyAmountPolicy policy = new MoneyAmountPolicy(2, true);
        assertNull(policy.invalidReason(BigDecimal.ZERO));
        assertNull(policy.invalidReason(new BigDecimal("10.01")));
        assertNotNull(policy.invalidReason(new BigDecimal("-0.01")));
    }
}
