#!/bin/bash

# Usage: ./java.sh ClassName
# Example: ./java.sh HelloRoot
# Example: ./java.sh appz.sside.http.HttpMessage

# Change to the project root directory (parent of java directory)
cd "$(dirname "$0")/.."

if [ $# -eq 0 ]; then
    echo "Usage: $0 ClassName"
    echo "Example: $0 HelloRoot"
    echo "Example: $0 appz.sside.http.HttpMessage"
    exit 1
fi

# Run the Java class with classpath including tmp directory and all jars in lib
java.exe -XX:+ShowCodeDetailsInExceptionMessages -cp "./java/tmp;./java/lib/*" "$1"