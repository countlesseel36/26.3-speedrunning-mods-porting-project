# Smoke test: launches Minecraft 26.3 with Fabric loader 0.19.5 and the ported mods,
# lets it run for a while and reports mixin / startup errors from the log.
[CmdletBinding()]
param(
    [int]$Seconds = 90,
    [string]$ExtraJars = "",
    [string]$GameDir = "",
    [string]$ModsSource = "",
    [string]$ExtraJvm = ""
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$mcDir = Join-Path $env:APPDATA '.minecraft'
$java = 'C:\Program Files\Java\jdk-25\bin\java.exe'
$versionJson = Get-Content (Join-Path $mcDir 'versions\26.3\26.3.json') -Raw | ConvertFrom-Json
$fabricJson = Get-Content (Join-Path $mcDir 'versions\fabric-loader-0.19.5-26.3\fabric-loader-0.19.5-26.3.json') -Raw | ConvertFrom-Json

function Test-Rules($rules) {
    if (-not $rules -or $rules.Count -eq 0) { return $true }
    $allowed = $false
    foreach ($rule in $rules) {
        $match = $true
        if ($rule.os) {
            if ($rule.os.name -and $rule.os.name -ne 'windows') { $match = $false }
            if ($rule.os.arch -and $rule.os.arch -ne 'x86_64') { $match = $false }
        }
        if ($match) { $allowed = ($rule.action -eq 'allow') }
    }
    return $allowed
}

function Resolve-Library($name) {
    $parts = $name.Split(':')
    if ($parts.Count -lt 3) { return $null }
    $group = $parts[0].Replace('.', '\')
    $artifact = $parts[1]
    $version = $parts[2]
    $classifier = if ($parts.Count -ge 4) { "-" + $parts[3] } else { '' }
    $file = "$artifact-$version$classifier.jar"
    return Join-Path $mcDir "libraries\$group\$artifact\$version\$file"
}

$runDir = if ($GameDir) { $GameDir } else { Join-Path $root 'minecraft' }
if (-not (Test-Path $runDir)) { throw "game dir not found: $runDir" }
$modsDir = Join-Path $runDir 'mods'
$nativesDir = Join-Path $root 'build\natives'
foreach ($dir in @($runDir, $modsDir, (Join-Path $nativesDir 'java'), (Join-Path $nativesDir 'lwjgl'), (Join-Path $nativesDir 'jna'), (Join-Path $nativesDir 'netty'))) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$jars = New-Object System.Collections.Generic.List[string]
foreach ($lib in $versionJson.libraries) {
    if (-not (Test-Rules $lib.rules)) { continue }
    $path = Resolve-Library $lib.name
    if ($path -and (Test-Path $path)) { $jars.Add($path) }
}
foreach ($lib in $fabricJson.libraries) {
    $path = Resolve-Library $lib.name
    if ($path -and (Test-Path $path)) { $jars.Add($path) }
}
$jars.Add((Join-Path $mcDir 'versions\26.3\26.3.jar'))

Get-ChildItem $modsDir -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '(?i)(^test_|seedqueue|worldpreview|speedrun-api|atum|fast-reset|standardsettings)' } | Remove-Item -Force
# Hermes locks the instance through the MCSR launcher; it cannot run from a plain launcher, so park it for the test.
$parkedHermes = @()
Get-ChildItem $modsDir -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'hermes-*.jar' } | ForEach-Object {
    $disabled = $_.FullName + '.disabled'
    Move-Item -LiteralPath $_.FullName -Destination $disabled -Force
    $parkedHermes += $disabled
}
if ($ModsSource -and (Test-Path $ModsSource)) { $ExtraJars = (Get-ChildItem $ModsSource -Filter *.jar | ForEach-Object FullName) -join ";" }
foreach ($extra in ($ExtraJars -split ";" | Where-Object { $_ })) {
    if (Test-Path $extra) { Copy-Item $extra $modsDir -Force }
}

$classpath = ($jars | Select-Object -Unique) -join ';'

$jvmArgs = New-Object System.Collections.Generic.List[string]
foreach ($entry in $versionJson.arguments.jvm) {
    $value = if ($entry -is [string]) { $entry } else { $entry.value }
    if ($null -eq $value) { continue }
    if ($value -is [array]) { continue }  # OS specific entries we do not need on Windows
    $arg = [string]$value
    if ($arg -eq '-XstartOnFirstThread') { continue }
    $arg = $arg.Replace('${natives_directory}', $nativesDir)
    $arg = $arg.Replace('${launcher_name}', 'smoke')
    $arg = $arg.Replace('${launcher_version}', '1')
    $arg = $arg.Replace('${classpath}', $classpath)
    $jvmArgs.Add($arg)
}

$gameArgs = @()
foreach ($entry in $versionJson.arguments.game) {
    if ($entry -isnot [string] -and ($entry.PSObject.Properties.Name -contains 'rules')) { continue }
    $value = if ($entry -is [string]) { $entry } else { $entry.value }
    if ($null -eq $value) { continue }
    if ($value -is [array]) { continue }
    $gameArgs += [string]$value
}

$replacements = @{
    '${auth_player_name}' = 'Dev'
    '${version_name}' = '26.3'
    '${game_directory}' = $runDir
    '${assets_root}' = (Join-Path $mcDir 'assets')
    '${assets_index_name}' = '34'
    '${auth_uuid}' = '00000000000000000000000000000000'
    '${auth_access_token}' = '0'
    '${user_type}' = 'legacy'
    '${version_type}' = 'release'
    '${user_properties}' = '{}'
    '${clientid}' = '0'
    '${auth_xuid}' = '0'
}
$resolvedGameArgs = foreach ($arg in $gameArgs) {
    $result = $arg
    foreach ($key in $replacements.Keys) { $result = $result.Replace($key, $replacements[$key]) }
    $result
}

$allArgs = New-Object System.Collections.Generic.List[string]
foreach ($a in $jvmArgs) { $allArgs.Add($a) }
foreach ($a in ($ExtraJvm -split ";" | Where-Object { $_ })) { $allArgs.Add($a) }
$allArgs.Add($fabricJson.mainClass)
foreach ($a in $resolvedGameArgs) { $allArgs.Add($a) }

$argFile = Join-Path $root 'build\launch-args.txt'
$lines = foreach ($a in $allArgs) {
    if ($a -match '\s') { '"' + $a + '"' } else { $a }
}
[System.IO.File]::WriteAllLines($argFile, [string[]]$lines, (New-Object System.Text.UTF8Encoding($false)))

$log = Join-Path $root 'build\smoke.log'
$err = Join-Path $root 'build\smoke.err.log'
if (Test-Path $log) { Remove-Item $log -Force }
if (Test-Path $err) { Remove-Item $err -Force }

Write-Host "launching 26.3 ($($allArgs.Count) args), log: $log"
$process = Start-Process -FilePath $java -ArgumentList "@$argFile" -WorkingDirectory $runDir -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
Start-Sleep -Seconds $Seconds
if (-not $process.HasExited) {
    $process.Kill()
    Start-Sleep -Seconds 2
    Write-Host "killed after $Seconds s (still running)"
} else {
    Write-Host "process exited early with code $($process.ExitCode)"
}

Write-Host '--- notable log lines ---'
if (Test-Path $log) {
    Select-String -Path $log -Pattern 'Mixin apply failed|MixinTransformerError|Exception|ERROR|Atum|Fast Reset|Loading Minecraft|Setting user' |
        Select-Object -First 40 -ExpandProperty Line
}
if (Test-Path $err) {
    Get-Content $err -TotalCount 20
}

foreach ($disabled in $parkedHermes) {
    $restored = $disabled -replace '\.disabled$', ''
    if (Test-Path $disabled) { Move-Item -LiteralPath $disabled -Destination $restored -Force }
}

