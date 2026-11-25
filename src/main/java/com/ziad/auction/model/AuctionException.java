package com.ziad.auction.model;

/** A business-rule violation shown to the user (bid too low, insufficient balance, wrong password...). */
public class AuctionException extends Exception {
    private static final long serialVersionUID = 1L;

    public AuctionException(String message) {
        super(message);
    }
}
