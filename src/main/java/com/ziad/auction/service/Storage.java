package com.ziad.auction.service;

import com.ziad.auction.model.AuctionHouse;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saves the whole auction house to one file. Writes go to a temporary file that is then atomically moved
 * into place, so a crash can never leave a half-written save. Loading uses an allow-list filter so only
 * this application's classes (and basic JDK types) can be deserialised.
 */
public final class Storage {
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "com.ziad.auction.model.*;java.lang.*;java.util.*;java.time.*;java.math.*;!*");

    private final Path file;

    public Storage(Path file) {
        this.file = file;
    }

    public AuctionHouse loadOrCreate() throws IOException {
        if (!Files.exists(file)) {
            return new AuctionHouse();
        }
        try (InputStream in = Files.newInputStream(file); ObjectInputStream ois = new ObjectInputStream(in)) {
            ois.setObjectInputFilter(FILTER);
            return (AuctionHouse) ois.readObject();
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IOException("Save file is not a valid auction database: " + file, e);
        }
    }

    public synchronized void save(AuctionHouse house) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "auction", ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp); ObjectOutputStream oos = new ObjectOutputStream(out)) {
            synchronized (house) {
                oos.writeObject(house);
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
