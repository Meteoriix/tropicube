package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyAmountTest {
    @Test
    void formatsThousandsMillionsAndBillions() {
        assertEquals("999.00 ⚙", EconomyAmount.format(999, "⚙"));
        assertEquals("1.0K ⚙", EconomyAmount.format(1_000, "⚙"));
        assertEquals("12.5M ⚙", EconomyAmount.format(12_500_000, "⚙"));
        assertEquals("1.0B ⚙", EconomyAmount.format(1_000_000_000, "⚙"));
        assertEquals("100.0B ⚙", EconomyAmount.format(100_000_000_000D, "⚙"));
    }

    @Test
    void clampsBalancesAtOneHundredBillion() {
        assertEquals(new BigDecimal("0.00"), EconomyAmount.clampBalance(new BigDecimal("-1.00")));
        assertEquals(new BigDecimal("99999999999.99"),
                EconomyAmount.clampBalance(new BigDecimal("99999999999.99")));
        assertEquals(EconomyAmount.MAXIMUM_BALANCE,
                EconomyAmount.clampBalance(new BigDecimal("100000000000.01")));
    }

    @Test
    void limitsRewardsToTheRemainingBalanceCapacity() {
        assertEquals(new BigDecimal("0.01"), EconomyAmount.creditWithinLimit(
                new BigDecimal("99999999999.99"), new BigDecimal("25.00")));
        assertEquals(new BigDecimal("0.00"), EconomyAmount.creditWithinLimit(
                EconomyAmount.MAXIMUM_BALANCE, new BigDecimal("25.00")));
    }
}
