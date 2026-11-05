# Build script for the Minecraft 26.3 ports of FastReset and Atum.
# No Gradle / no network: compiles with the local JDK 25 against the locally
# installed 26.3 jar + Fabric loader libraries.

[CmdletBinding()]
param(
    [ValidateSet('all', 'fast-reset', 'atum', 'speedrunapi', 'standardsettings', 'worldpreview', 'seedqueue')]
    [string]$Mod = 'all'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = 'C:\Program Files\Java\jdk-25\bin'
$javac = Join-Path $jdk 'javac.exe'
$jarTool = Join-Path $jdk 'jar.exe'
$mcJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
$libRoot = Join-Path $env:APPDATA '.minecraft\libraries'

if (-not (Test-Path $javac)) { throw "javac not found: $javac" }
if (-not (Test-Path $mcJar)) { throw "Minecraft 26.3 jar not found: $mcJar" }

Add-Type -AssemblyName System.IO.Compression.FileSystem

$extraLibs = @()
if (Test-Path (Join-Path $root 'build\libs')) {
    $extraLibs += Get-ChildItem (Join-Path $root 'build\libs') -Filter *.jar | ForEach-Object FullName
}
$instanceMods = Join-Path $root 'minecraft\mods'
if (Test-Path $instanceMods) {
    $extraLibs += Get-ChildItem $instanceMods -Filter 'sodium*.jar' -ErrorAction SilentlyContinue | ForEach-Object FullName
if (Test-Path (Join-Path $root 'dist')) {
  $extraLibs += Get-ChildItem (Join-Path $root 'dist') -Filter '*.jar' | ForEach-Object FullName
}
}
$moduleClasses = Get-ChildItem (Join-Path $root 'build') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'classes') } | ForEach-Object { Join-Path $_.FullName 'classes' }
$classpath = (@($mcJar) + (Get-ChildItem $libRoot -Recurse -Filter *.jar | ForEach-Object FullName) + $extraLibs + $moduleClasses) -join ';'
Write-Host "classpath entries: $((($classpath -split ';').Count))"

$mods = if ($Mod -eq 'all') { @('fast-reset', 'atum', 'speedrunapi', 'standardsettings', 'worldpreview', 'seedqueue') } else { @($Mod) }

foreach ($name in $mods) {
    $src = Join-Path $root "$name\src\main\java"
    $res = Join-Path $root "$name\src\main\resources"
    $out = Join-Path $root "build\$name\classes"
    $dist = Join-Path $root "dist"

    if (-not (Test-Path $src)) { Write-Host "== skipping $name (no sources yet) =="; continue }
    if (Test-Path $out) { Remove-Item $out -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $out, $dist | Out-Null

    $sources = Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object FullName
    $argFile = Join-Path $root "build\$name-sources.txt"
    [System.IO.File]::WriteAllLines($argFile, [string[]]$sources, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host "== compiling $name ($($sources.Count) files) =="
    $ErrorActionPreference = 'Continue'
    & $javac --release 21 -proc:none -nowarn -encoding UTF-8 -cp $classpath -d $out "@$(Join-Path $root "build\$name-sources.txt")" 2>&1 | ForEach-Object { Write-Host $_ }
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $name (exit $LASTEXITCODE)" }

    if (Test-Path $res) {
        Copy-Item (Join-Path $res '*') $out -Recurse -Force
    }

    $meta = Get-Content (Join-Path $res 'fabric.mod.json') -Raw | ConvertFrom-Json
    $version = $meta.version
    $displayName = $meta.name
    $target = Join-Path $dist ($version + "-" + ($displayName -replace '\s+', '-') + ".jar")
    if (Test-Path $target) { Remove-Item $target -Force }
    Push-Location $out
    try {
        & $jarTool --create --file $target .
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) { throw "jar failed for $name (exit $LASTEXITCODE)" }
    Write-Host "== built $target ($([math]::Round((Get-Item $target).Length / 1024, 1)) KB) =="
}
