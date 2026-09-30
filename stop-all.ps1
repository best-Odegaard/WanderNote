# stop-all.ps1 - stop the three local WanderNote services (ports 8080 / 8002 / 5173).
# Thin wrapper around start-all.ps1 -Stop; kept ASCII-only so any PowerShell reads it correctly.
& (Join-Path $PSScriptRoot 'start-all.ps1') -Stop
