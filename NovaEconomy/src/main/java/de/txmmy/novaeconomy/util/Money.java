package de.txmmy.novaeconomy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {
    }

    public static long parseMinor(String input) {
        String normalized = input.trim();
        if (normalized.contains(",") && !normalized.contains(".")) {
            normalized = normalized.replace(',', '.');
        }
        BigDecimal amount = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
        return amount.movePointRight(2).longValueExact();
    }

    public static long multiplyExact(long unitPriceMinor, int amount) {
        return Math.multiplyExact(unitPriceMinor, amount);
    }

    public static BigDecimal toMajor(long minor) {
        return BigDecimal.valueOf(minor, 2);
    }
}
