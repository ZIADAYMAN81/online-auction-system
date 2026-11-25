package com.ziad.auction;

import com.ziad.auction.model.Auction;
import com.ziad.auction.model.AuctionException;
import com.ziad.auction.model.AuctionHouse;
import com.ziad.auction.model.Money;
import com.ziad.auction.model.Passwords;
import com.ziad.auction.model.User;
import com.ziad.auction.service.Storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** Framework-free test runner: {@code java -cp out com.ziad.auction.AuctionTests}. */
public final class AuctionTests {
    private static int passed, failed;

    private static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAILED: " + what);
        }
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void rejected(Body b, String what) {
        try {
            b.run();
            check(false, what + " should be rejected");
        } catch (AuctionException e) {
            check(true, what);
        } catch (Exception e) {
            check(false, what + " threw " + e);
        }
    }

    /** A clock the tests can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static AuctionHouse house(TestClock clock) throws AuctionException {
        AuctionHouse h = new AuctionHouse();
        h.setClock(clock);
        h.register("seller", "secret1".toCharArray(), 0);
        h.register("ali", "secret2".toCharArray(), 50_000);
        h.register("mona", "secret3".toCharArray(), 80_000);
        return h;
    }

    static void passwords() {
        String stored = Passwords.hash("correct horse".toCharArray());
        check(Passwords.matches("correct horse".toCharArray(), stored), "correct password accepted");
        check(!Passwords.matches("wrong".toCharArray(), stored), "wrong password rejected");
        check(!stored.contains("correct horse"), "password is not stored in plain text");
        check(!stored.equals(Passwords.hash("correct horse".toCharArray())), "salted: same password, different hash");
    }

    static void accounts() throws Exception {
        AuctionHouse h = house(new TestClock());
        check(h.login("ali", "secret2".toCharArray()).username().equals("ali"), "login");
        rejected(() -> h.login("ali", "nope".toCharArray()), "wrong password");
        rejected(() -> h.register("ALI", "another1".toCharArray(), 0), "duplicate username (case-insensitive)");
        rejected(() -> h.register("x", "longenough".toCharArray(), 0), "too-short username");
        rejected(() -> h.register("newuser", "123".toCharArray(), 0), "too-short password");
    }

    static void biddingRules() throws Exception {
        TestClock clock = new TestClock();
        AuctionHouse h = house(clock);
        Auction a = h.list("seller", "Vintage camera", "Electronics", "Used", 10_000, 1_000, Duration.ofHours(2));
        rejected(() -> h.bid(a.id(), "ali", 9_999), "bid below starting price");
        h.bid(a.id(), "ali", 10_000);
        rejected(() -> h.bid(a.id(), "mona", 10_500), "bid below current + increment");
        rejected(() -> h.bid(a.id(), "seller", 20_000), "seller bidding on own item");
        rejected(() -> h.bid(a.id(), "ali", 60_000), "bid above available balance");
        h.bid(a.id(), "mona", 11_000);
        check(h.user("ali").availableCents() == 50_000, "outbid bidder's hold is released");
        check(h.user("mona").availableCents() == 80_000 - 11_000, "leading bidder's amount is held");
        h.bid(a.id(), "mona", 15_000);  // raising your own leading bid only needs the difference
        check(h.user("mona").heldCents() == 15_000, "raising own bid replaces the hold");
        check(a.minimumNextBidCents() == 16_000, "minimum next bid");
    }

    static void settlement() throws Exception {
        TestClock clock = new TestClock();
        AuctionHouse h = house(clock);
        long moneyBefore = h.totalBalanceCents();
        Auction sold = h.list("seller", "Laptop", "Electronics", "Like new", 20_000, 500, Duration.ofHours(1));
        Auction unsold = h.list("seller", "Old chair", "Furniture", "Used", 5_000, 100, Duration.ofHours(1));
        h.bid(sold.id(), "ali", 20_000);
        h.bid(sold.id(), "mona", 25_000);
        check(h.closeExpired() == 0, "nothing closes before the end time");
        clock.now = clock.now.plus(Duration.ofMinutes(61));
        rejected(() -> h.bid(sold.id(), "ali", 30_000), "bid after the end time");
        check(sold.status() == Auction.Status.SOLD && unsold.status() == Auction.Status.UNSOLD, "auctions close with the right status");
        check(h.user("mona").balanceCents() == 55_000 && h.user("mona").heldCents() == 0, "winner pays and the hold is cleared");
        check(h.user("seller").balanceCents() == 25_000, "seller is paid the winning bid");
        check(h.user("ali").balanceCents() == 50_000, "loser keeps their money");
        check(h.totalBalanceCents() == moneyBefore, "no money created or destroyed");
    }

    static void searchAndValidation() throws Exception {
        AuctionHouse h = house(new TestClock());
        h.list("seller", "Gaming laptop", "Electronics", "New", 100, 10, Duration.ofHours(5));
        h.list("seller", "Desk", "Furniture", "Used", 100, 10, Duration.ofHours(1));
        check(h.search("electro", true).size() == 1, "search by category");
        check(h.search("", true).get(0).title().equals("Desk"), "results sorted by end time");
        rejected(() -> h.list("seller", " ", "x", "New", 100, 10, Duration.ofHours(1)), "blank title");
        rejected(() -> h.list("seller", "Item", "x", "New", 0, 10, Duration.ofHours(1)), "zero starting price");
        rejected(() -> Money.parse("12.345"), "three decimals");
        check(Money.parse("12.30") == 1_230 && Money.format(1_230).equals("12.30"), "money parse/format");
    }

    static void persistence() throws Exception {
        Path file = Files.createTempDirectory("auction-test").resolve("data.bin");
        Storage storage = new Storage(file);
        AuctionHouse h = house(new TestClock());
        Auction a = h.list("seller", "Bike", "Sport", "Used", 3_000, 100, Duration.ofDays(3));
        h.bid(a.id(), "ali", 3_000);
        storage.save(h);
        AuctionHouse loaded = storage.loadOrCreate();
        check(loaded.auction(a.id()).leadingBid().orElseThrow().bidder().equals("ali"), "bids survive save and load");
        check(loaded.login("mona", "secret3".toCharArray()) != null, "users and password hashes survive save and load");
        check(loaded.user("ali").heldCents() == 3_000, "holds survive save and load");
    }

    public static void main(String[] args) throws Exception {
        Object[][] tests = {{"password hashing", (Body) AuctionTests::passwords}, {"accounts", (Body) AuctionTests::accounts},
                {"bidding rules", (Body) AuctionTests::biddingRules}, {"settlement", (Body) AuctionTests::settlement},
                {"search and validation", (Body) AuctionTests::searchAndValidation},
                {"persistence", (Body) AuctionTests::persistence}};
        for (Object[] t : tests) {
            System.out.println("- " + t[0]);
            ((Body) t[1]).run();
        }
        System.out.printf("%d/%d checks passed%n", passed, passed + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
