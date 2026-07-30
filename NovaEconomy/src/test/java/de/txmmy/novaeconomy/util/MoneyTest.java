package de.txmmy.novaeconomy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test
    void parsesDotAndCommaWithTwoDecimals() {
        assertEquals(250L, Money.parseMinor("2.50"));
        assertEquals(250L, Money.parseMinor("2,50"));
        assertEquals(200L, Money.parseMinor("2"));
    }

    @Test
    void rejectsFractionsSmallerThanOneCent() {
        assertThrows(ArithmeticException.class, () -> Money.parseMinor("1.001"));
    }

    @Test
    void multipliesExactly() {
        assertEquals(250_000L, Money.multiplyExact(250L, 1000));
    }

    @Test
    void detectsOverflow() {
        assertThrows(ArithmeticException.class, () -> Money.multiplyExact(Long.MAX_VALUE, 2));
    }
}
