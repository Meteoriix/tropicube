package fr.tropicube.core.managers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Defines the network-wide precision, display and upper bound of TropiCoin amounts. */
public final class EconomyAmount {
    public static final BigDecimal MAXIMUM_BALANCE = new BigDecimal("100000000000.00");

    private EconomyAmount() {
    }

    /** Converts a finite amount to the two-decimal precision stored by the economy tables. */
    public static BigDecimal money(double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Le montant doit être un nombre fini");
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Restricts a balance to the inclusive range from zero to 100 billion TropiCoins. */
    public static BigDecimal clampBalance(BigDecimal balance) {
        if (balance.signum() < 0) return BigDecimal.ZERO.setScale(2);
        return balance.min(MAXIMUM_BALANCE).setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns the part of a positive reward that still fits below the balance limit. */
    public static BigDecimal creditWithinLimit(BigDecimal balance, BigDecimal requested) {
        BigDecimal capacity = MAXIMUM_BALANCE.subtract(balance).max(BigDecimal.ZERO);
        return requested.min(capacity).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Formats a balance with K, M and B suffixes using stable dot decimal separators. */
    public static String format(double amount, String currencySymbol) {
        if (amount >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fB %s", amount / 1_000_000_000, currencySymbol);
        }
        if (amount >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM %s", amount / 1_000_000, currencySymbol);
        }
        if (amount >= 1_000) {
            return String.format(Locale.ROOT, "%.1fK %s", amount / 1_000, currencySymbol);
        }
        return String.format(Locale.ROOT, "%.2f %s", amount, currencySymbol);
    }
}
