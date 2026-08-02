param(
    [string]$ProjectRoot = "D:\GitHub\DSE-ERP"
)
$ErrorActionPreference = "Stop"
Write-Host "DSE ERP 2.0.1 source files must first be copied/extracted over: $ProjectRoot"
Set-Location $ProjectRoot
mvn clean test
mvn clean package
Write-Host "Validation completed. Review git status, then commit and push manually."
git status --short
