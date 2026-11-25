package com.ziad.auction.model;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * All business rules live here (the Swing UI only calls these methods), which keeps them testable.
 *
 * <ul>
 *   <li>A bid must be at least the starting price, or the current bid plus the increment.</li>
 *   <li>Sellers cannot bid on their own items, and nobody can bid after the end time.</li>
 *   <li>The leading bidder's amount is <em>held</em> from their balance; when they are outbid the hold is released.</li>
 *   <li>When an auction closes, the winner pays the seller (held money is settled) and the item is marked sold.</li>
 * </ul>
 */
public final class AuctionHouse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<String, User> users = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Auction> auctions = new LinkedHashMap<>();
    private long nextId = 1;
    private transient Clock clock = Clock.systemUTC();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private Instant now() {
        if (clock == null) {
            clock = Clock.systemUTC();  // restored after deserialisation
        }
        return clock.instant();
    }

    // ---- accounts ---------------------------------------------------------------------------------------

    public synchronized User register(String username, char[] password, long startingBalanceCents) throws AuctionException {
        String name = username == null ? "" : username.trim();
        if (!name.matches("[A-Za-z0-9_.-]{3,20}")) {
            throw new AuctionException("Username must be 3-20 letters, digits, dots, dashes or underscores");
        }
        if (password == null || password.length < 6) {
            throw new AuctionException("Password must be at least 6 characters");
        }
        if (users.keySet().stream().anyMatch(u -> u.equalsIgnoreCase(name))) {
            throw new AuctionException("Username already taken");
        }
        if (startingBalanceCents < 0) {
            throw new AuctionException("Balance cannot be negative");
        }
        User user = new User(name, Passwords.hash(password), startingBalanceCents);
        users.put(name, user);
        return user;
    }

    public synchronized User login(String username, char[] password) throws AuctionException {
        User user = users.get(username == null ? "" : username.trim());
        if (user == null || !Passwords.matches(password, user.passwordHash())) {
            throw new AuctionException("Wrong username or password");  // same message: no user enumeration
        }
        return user;
    }

    public synchronized User user(String username) throws AuctionException {
        User u = users.get(username);
        if (u == null) {
            throw new AuctionException("Unknown user: " + username);
        }
        return u;
    }

    // ---- listings and bids -----------------------------------------------------------------------------

    public synchronized Auction list(String seller, String title, String category, String condition,
                                     long startingCents, long incrementCents, Duration duration) throws AuctionException {
        user(seller);
        if (title == null || title.isBlank()) {
            throw new AuctionException("Title is required");
        }
        if (startingCents <= 0 || incrementCents <= 0) {
            throw new AuctionException("Starting price and increment must be positive");
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new AuctionException("Duration must be positive");
        }
        Auction a = new Auction(nextId++, seller, title.trim(), category == null ? "" : category.trim(),
                condition == null ? "" : condition.trim(), startingCents, incrementCents, now().plus(duration));
        auctions.put(a.id(), a);
        return a;
    }

    public synchronized void bid(long auctionId, String bidder, long amountCents) throws AuctionException {
        closeExpired();
        Auction a = auction(auctionId);
        User u = user(bidder);
        if (a.status() != Auction.Status.OPEN) {
            throw new AuctionException("This auction has ended");
        }
        if (a.seller().equals(bidder)) {
            throw new AuctionException("You cannot bid on your own item");
        }
        if (amountCents < a.minimumNextBidCents()) {
            throw new AuctionException("Bid must be at least " + Money.format(a.minimumNextBidCents()));
        }
        long alreadyHeldHere = a.leadingBid().filter(b -> b.bidder().equals(bidder)).map(Bid::amountCents).orElse(0L);
        if (u.availableCents() + alreadyHeldHere < amountCents) {
            throw new AuctionException("Insufficient available balance (" + Money.format(u.availableCents()) + ")");
        }
        a.leadingBid().ifPresent(previous -> users.get(previous.bidder()).release(previous.amountCents()));
        u.hold(amountCents);
        a.addBid(new Bid(bidder, amountCents, now()));
    }

    /** Settle every auction whose end time has passed. Returns how many were closed. */
    public synchronized int closeExpired() {
        int closed = 0;
        for (Auction a : auctions.values()) {
            if (a.status() == Auction.Status.OPEN && !now().isBefore(a.endsAt())) {
                settle(a);
                closed++;
            }
        }
        return closed;
    }

    private void settle(Auction a) {
        if (a.leadingBid().isEmpty()) {
            a.close(Auction.Status.UNSOLD);
            return;
        }
        Bid win = a.leadingBid().get();
        User buyer = users.get(win.bidder());
        buyer.release(win.amountCents());
        buyer.debit(win.amountCents());
        users.get(a.seller()).credit(win.amountCents());
        a.close(Auction.Status.SOLD);
    }

    public synchronized Auction auction(long id) throws AuctionException {
        Auction a = auctions.get(id);
        if (a == null) {
            throw new AuctionException("Unknown auction #" + id);
        }
        return a;
    }

    public synchronized List<Auction> search(String text, boolean openOnly) {
        closeExpired();
        String q = text == null ? "" : text.trim().toLowerCase();
        List<Auction> out = new ArrayList<>();
        for (Auction a : auctions.values()) {
            boolean matches = q.isEmpty() || a.title().toLowerCase().contains(q) || a.category().toLowerCase().contains(q);
            if (matches && (!openOnly || a.status() == Auction.Status.OPEN)) {
                out.add(a);
            }
        }
        out.sort(Comparator.comparing(Auction::endsAt));
        return out;
    }

    /** Total money in the system (balances only; holds are not money). Constant across trades. */
    public synchronized long totalBalanceCents() {
        return users.values().stream().mapToLong(User::balanceCents).sum();
    }
}
