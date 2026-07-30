package de.txmmy.novaeconomy.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormatter {
    private final Locale locale;
    private final String symbol;

    public MoneyFormatter(String languageTag, String symbol) {
        this.locale = Locale.forLanguageTag(languageTag);
        this.symbol = symbol;
    }

    public String format(long minor) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setGroupingUsed(true);
        return numberFormat.format(Money.toMajor(minor)) + " " + symbol;
    }
}
