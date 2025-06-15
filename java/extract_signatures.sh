#!/bin/bash

# Extract method signatures to java/method-signatures.json

# Change to project root directory
cd "$(dirname "$0")/.."

# Ensure everything is compiled
echo "Compiling Java files..."
./java/javac.sh

# Extract signatures
echo "Extracting method signatures..."
./java/java.sh buildtools.MethodSignatureExtractor