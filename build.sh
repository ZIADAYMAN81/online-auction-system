#!/usr/bin/env sh
# Compile everything into out/ and run the test suite (requires JDK 17+).
set -e
rm -rf out
javac -Xlint:all -d out $(find src -name "*.java")
java -cp out com.ziad.auction.AuctionTests
