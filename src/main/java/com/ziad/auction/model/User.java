package com.ziad.auction.model;

import java.io.Serializable;

/** A registered user. Balances are whole cents; only a salted password hash is stored. */
public final class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String passwordHash;
    private long balanceCents;
    private long heldCents;  // reserved by the user's current leading bids

    public User(String username, String passwordHash, long balanceCents) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.balanceCents = balanceCents;
    }

    public String username() { return username; }
    public String passwordHash() { return passwordHash; }
    public long balanceCents() { return balanceCents; }
    public long heldCents() { return heldCents; }

    /** Money the user can still commit to new bids. */
    public long availableCents() { return balanceCents - heldCents; }

    void hold(long cents) { heldCents += cents; }
    void release(long cents) { heldCents -= cents; }
    void debit(long cents) { balanceCents -= cents; }
    void credit(long cents) { balanceCents += cents; }

    public void deposit(long cents) throws AuctionException {
        if (cents <= 0) {
            throw new AuctionException("Deposit must be positive");
        }
        balanceCents += cents;
    }
}
