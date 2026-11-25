package com.ziad.auction.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One listing: an item for sale with a starting price, a minimum increment and an end time. */
public final class Auction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { OPEN, SOLD, UNSOLD }

    private final long id;
    private final String seller;
    private final String title;
    private final String category;
    private final String condition;
    private final long startingCents;
    private final long incrementCents;
    private final Instant endsAt;
    private final ArrayList<Bid> bids = new ArrayList<>();
    private Status status = Status.OPEN;

    public Auction(long id, String seller, String title, String category, String condition, long startingCents,
                   long incrementCents, Instant endsAt) {
        this.id = id;
        this.seller = seller;
        this.title = title;
        this.category = category;
        this.condition = condition;
        this.startingCents = startingCents;
        this.incrementCents = incrementCents;
        this.endsAt = endsAt;
    }

    public long id() { return id; }
    public String seller() { return seller; }
    public String title() { return title; }
    public String category() { return category; }
    public String condition() { return condition; }
    public long startingCents() { return startingCents; }
    public long incrementCents() { return incrementCents; }
    public Instant endsAt() { return endsAt; }
    public Status status() { return status; }
    public List<Bid> bids() { return List.copyOf(bids); }

    public Optional<Bid> leadingBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.get(bids.size() - 1));
    }

    /** Lowest acceptable next bid: the starting price, or the current bid plus the increment. */
    public long minimumNextBidCents() {
        return leadingBid().map(b -> b.amountCents() + incrementCents).orElse(startingCents);
    }

    void addBid(Bid bid) { bids.add(bid); }
    void close(Status result) { status = result; }
}
