# SAPL Eclipse Plugin - Cache Cleanup Script
#
# Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
# SPDX-License-Identifier: Apache-2.0
#
# Clears Tycho, Maven, and p2 caches that can cause build or reinstallation failures.
# Run this before rebuilding the plugin after changes.

$ErrorActionPreference = "SilentlyContinue"

Write-Host "Cleaning SAPL Eclipse plugin caches..." -ForegroundColor Cyan

# Maven/Tycho caches
$mavenCaches = @(
    "$env:USERPROFILE\.m2\repository\.cache",
    "$env:USERPROFILE\.m2\repository\.meta",
    "$env:USERPROFILE\.m2\repository\p2",
    "$env:USERPROFILE\.m2\repository\io\sapl\sapl-eclipse-thirdparty",
    "$env:USERPROFILE\.m2\repository\org\eclipse\lsp4j"
)

foreach ($path in $mavenCaches) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path
        Write-Host "  Removed: $path" -ForegroundColor Green
    }
}

# p2 caches
$p2Caches = @(
    "$env:USERPROFILE\.p2\pool\plugins\io.sapl*",
    "$env:USERPROFILE\.p2\pool\plugins\sapl*",
    "$env:USERPROFILE\.p2\pool\features\io.sapl*",
    "$env:USERPROFILE\.p2\org.eclipse.equinox.p2.repository",
    "$env:USERPROFILE\.p2\org.eclipse.equinox.p2.core"
)

foreach ($pattern in $p2Caches) {
    $items = Get-Item $pattern 2>$null
    foreach ($item in $items) {
        Remove-Item -Recurse -Force $item.FullName
        Write-Host "  Removed: $($item.FullName)" -ForegroundColor Green
    }
}

# Target directories in this project
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$targetDirs = @(
    "sapl-eclipse-ui\target",
    "sapl-test-eclipse-ui\target",
    "sapl-eclipse-feature\target",
    "sapl-eclipse-repository\target",
    "sapl-eclipse-thirdparty\target"
)

foreach ($dir in $targetDirs) {
    $path = Join-Path $scriptDir $dir
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path
        Write-Host "  Removed: $path" -ForegroundColor Green
    }
}

Write-Host "`nCache cleanup complete." -ForegroundColor Cyan
Write-Host "Remember to start Eclipse with -clean flag after reinstalling." -ForegroundColor Yellow
