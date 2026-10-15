#!/bin/bash
mkdir -p out
javac -d out Hasher.java Ledger.java Json.java DocVerifyServer.java
if [ $? -eq 0 ]; then
    echo "Starting DocVerify Server on http://localhost:8080 ..."
    java -cp out DocVerifyServer
else
    echo "Compilation failed."
fi