# One-time: use repo-managed git hooks (version bump on commit).
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

git config core.hooksPath .githooks
Write-Host "Git hooks path set to .githooks (pre-commit will bump version.json)."
Write-Host "Run: git config --get core.hooksPath"
