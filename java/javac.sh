#!/bin/bash

# Usage: ./javac.sh [File|Directory]
# Example: ./javac.sh                      # Compile all files
# Example: ./javac.sh src/HelloRoot.java   # Compile a file
# Example: ./javac.sh src/appz/sside       # Compile a directory

# Change to the project root directory (parent of java directory)
cd "$(dirname "$0")/.."

if [ $# -eq 0 ]; then
    # No arguments - compile all Java files
    echo "Compiling all Java sources..."
    javac -cp "./java/tmp:./java/lib/*" -d ./java/tmp java/src/**/*.java
else
    ARG="$1"
    
    # Prepend java/ if not already present
    if [[ "$ARG" != java/* ]]; then
        ARG="java/$ARG"
    fi
    
    # Check if argument is a file
    if [ -f "$ARG" ]; then
        echo "Compiling file: $ARG"
        javac -cp "./java/tmp:./java/lib/*" -d ./java/tmp "$ARG"
    elif [ -d "$ARG" ]; then
        echo "Compiling directory: $ARG"
        # Find all .java files in the directory and compile them
        find "$ARG" -name "*.java" -print0 | xargs -0 javac -cp "./java/tmp:./java/lib/*" -d ./java/tmp
    else
        echo "Error: '$ARG' is not a valid file or directory"
        exit 1
    fi
fi

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "Compilation successful!"
else
    echo "Compilation failed!"
fi