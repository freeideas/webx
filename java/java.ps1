# Usage: ./java.ps1 ClassName
# Example: ./java.ps1 HelloRoot
# Example: ./java.ps1 appz.sside.http.HttpMessage

param(
    [Parameter(Mandatory=$true)]
    [string]$ClassName
)

# Change to the project root directory (parent of java directory)
Set-Location -Path (Join-Path $PSScriptRoot "..")

# Run the Java class with classpath including tmp directory and all jars in lib
& java.exe -XX:+ShowCodeDetailsInExceptionMessages -cp "./java/tmp;./java/lib/*" $ClassName