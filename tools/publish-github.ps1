# Publish this project to GitHub and (optionally) create a Release with the built jars.
# Requires Git for Windows; the release step additionally needs the GitHub CLI (gh).
#
# Usage:
#   # push the source (jars are git-ignored)
#   powershell -ExecutionPolicy Bypass -File .\tools\publish-github.ps1 `
#       -RepoUrl https://github.com/<user>/<repo>.git `
#       -CommitMessage "26.3 port: SeedQueue 0.9.0-beta.52 / WorldPreview 6.3.0-beta.31"
#
#   # tag + release, uploading dist\*.jar and dist\SeedQueue-LockedPool.zip as assets
#   powershell -ExecutionPolicy Bypass -File .\tools\publish-github.ps1 `
#       -RepoUrl https://github.com/<user>/<repo>.git -Release v0.9.0-beta.52
#
# The script only runs git / gh commands. It never deletes files.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepoUrl,
    [string]$CommitMessage = "",
    [string]$Branch = "main",
    [string]$Release = ""
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Get-ReleaseAssets {
    $items = @(Get-ChildItem (Join-Path $root "dist\*.jar") -ErrorAction SilentlyContinue)
    $pack = Join-Path $root "dist\SeedQueue-LockedPool.zip"
    if (Test-Path $pack) { $items += Get-Item $pack }
    return $items
}

if ($Release) {
    # ── release only ────────────────────────────────────────────────
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Host "gh CLI not found. Create the release on github.com instead:"
        Write-Host "  Releases -> Draft a new release -> tag $Release -> drag these files:"
        foreach ($f in (Get-ReleaseAssets)) { Write-Host ("    " + $f.Name) }
        exit 0
    }

    $assets = Get-ReleaseAssets
    if (-not $assets -or $assets.Count -eq 0) { throw "no assets in dist/ - build first (build.ps1 -Mod all)" }

    $notes = Join-Path $root "docs\release-notes.md"
    $noteArgs = @()
    if (Test-Path $notes) { $noteArgs = @("--notes-file", $notes) } else { $noteArgs = @("--notes", "Minecraft 26.3 speedrun mod port (test build).") }

    Write-Host ("creating release " + $Release + " with " + $assets.Count + " assets")
    gh release create $Release @noteArgs @($assets | ForEach-Object { $_.FullName })
    exit $LASTEXITCODE
}

# ── source push ─────────────────────────────────────────────────────
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "git not found. Install Git for Windows first: https://git-scm.com/download/win"
}

if (-not (Test-Path (Join-Path $root ".git"))) {
    Write-Host "git init"
    git init | Out-Null
    git branch -M $Branch
    git remote add origin $RepoUrl
} else {
    $existing = (git remote get-url origin 2>$null)
    if (-not $existing) {
        git remote add origin $RepoUrl
    } elseif ($existing -ne $RepoUrl) {
        Write-Host "origin: $existing -> $RepoUrl"
        git remote set-url origin $RepoUrl
    }
}

git config core.autocrlf false
git config core.longpaths true

git add -A

$status = git status --porcelain
if (-not $status) {
    Write-Host "nothing to commit"
} else {
    if (-not $CommitMessage) { $CommitMessage = "Update 26.3 speedrun mod ports" }
    git commit -m $CommitMessage
}

Write-Host "pushing to $RepoUrl ($Branch)"
git push -u origin $Branch

Write-Host "done: $RepoUrl"
