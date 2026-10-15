@echo off
if not exist out mkdir out
javac -d out Hasher.java Ledger.java Json.java DocVerifyServer.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b
)
echo Starting DocVerify Server on http://localhost:8080 ...
java -cp out DocVerifyServer
pause