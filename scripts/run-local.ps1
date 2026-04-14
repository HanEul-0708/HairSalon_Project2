param(
    [int]$Port = 8090
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$env:SPRING_PROFILES_ACTIVE = "local"
$env:APP_ASSET_VERSION = "local-$(Get-Date -Format 'yyyyMMddHHmmss')"

Write-Host "Starting HairSalonProject2 with local profile."
Write-Host "URL: http://localhost:${Port}"
Write-Host "Map: http://localhost:${Port}/salons/map"
Write-Host "Asset version: $env:APP_ASSET_VERSION"

if (-not $env:KAKAO_JAVASCRIPT_KEY) {
    Write-Host "KAKAO_JAVASCRIPT_KEY env is empty. application-secret.properties may still provide the key."
}

if (-not $env:KAKAO_REST_API_KEY) {
    Write-Host "KAKAO_REST_API_KEY env is empty. application-secret.properties may still provide the key."
}

& .\gradlew.bat bootRun "--args=--server.port=$Port --spring.profiles.active=local --app.asset-version=$env:APP_ASSET_VERSION"
