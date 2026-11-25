package com.ziad.auction.model;

import java.math.BigDecimal;

/** Exact money handling: whole cents in a {@code long}. */
public final class Money {
    private Money() {
    }

    public static long parse(String text) throws AuctionException {
        try {
            BigDecimal v = new BigDecimal(text.trim());
            if (v.scale() > 2 || v.signum() < 0) {
                throw new AuctionException("Enter a positive amount with at most two decimals");
            }
            return v.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new AuctionException("Not a valid amount: " + text);
        }
    }

    public static String format(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
