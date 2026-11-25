@echo off
rem Compile everything into out\ and run the test suite (requires JDK 17+).
setlocal enabledelayedexpansion
if exist out rmdir /s /q out
if exist sources.txt del sources.txt
rem Quote each path and use forward slashes so folders with spaces work in the javac argument file.
for /r src %%f in (*.java) do (
    set "p=%%~f"
    echo "!p:\=/!">>sources.txt
)
javac -Xlint:all -d out @sources.txt || exit /b 1
del sources.txt
java -cp out com.ziad.auction.AuctionTests
