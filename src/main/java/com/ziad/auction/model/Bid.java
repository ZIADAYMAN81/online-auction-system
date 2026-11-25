package com.ziad.auction.model;

import java.io.Serializable;
import java.time.Instant;

public record Bid(String bidder, long amountCents, Instant at) implements Serializable {
}
