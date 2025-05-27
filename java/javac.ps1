# Usage: ./javac.ps1 [File|Directory]
# Example: ./javac.ps1                      # Compile all files
# Example: ./javac.ps1 src/HelloRoot.java   # Compile a file
# Example: ./javac.ps1 src/appz/sside       # Compile a directory

param(
    [Parameter(Mandatory=$false)]
    [string]$Target
)

# Change to the project root directory (parent of java directory)
Set-Location -Path (Join-Path $PSScriptRoot "..")

try {
    if ([string]::IsNullOrWhiteSpace($Target)) {
        # No arguments - compile all Java files
        Write-Host "Compiling all Java sources..."
        $files = Get-ChildItem -Path java/src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
        & javac.exe -cp "./java/tmp;./java/lib/*" -d ./java/tmp $files
    }
    else {
        # Prepend java/ if not already present
        if (-not $Target.StartsWith("java/")) {
            $Target = "java/$Target"
        }
        
        # Check if argument is a file
        if (Test-Path -Path $Target -PathType Leaf) {
            Write-Host "Compiling file: $Target"
            & javac.exe -cp "./java/tmp;./java/lib/*" -d ./java/tmp $Target
        }
        elseif (Test-Path -Path $Target -PathType Container) {
            Write-Host "Compiling directory: $Target"
            # Find all .java files in the directory and compile them
            $files = Get-ChildItem -Path $Target -Recurse -Filter *.java | ForEach-Object { $_.FullName }
            if ($files.Count -gt 0) {
                & javac.exe -cp "./java/tmp;./java/lib/*" -d ./java/tmp $files
            }
            else {
                Write-Host "No Java files found in $Target"
                exit 1
            }
        }
        else {
            Write-Host "Error: '$Target' is not a valid file or directory"
            exit 1
        }
    }
    
    # Check if compilation was successful
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Compilation successful!"
    }
    else {
        Write-Host "Compilation failed!"
        exit $LASTEXITCODE
    }
}
finally {
}