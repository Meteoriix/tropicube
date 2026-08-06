#!/usr/bin/env pwsh
# deploy.ps1 — Build, distribute and redeploy all Tropicube plugin images.
#
# Usage:
#   .\deploy.ps1                  Full build + image rebuild
#   .\deploy.ps1 -SkipTests       Skip unit tests (faster)
#   .\deploy.ps1 -OnlyImages      Skip Maven, redistribue les artefacts target/ puis reconstruit les images
#   .\deploy.ps1 -SkipRestart     Construit sans recréer le service Velocity
#   .\deploy.ps1 -ValidateOnly    Vérifie et redistribue les artefacts sans construire d'image

param(
    [switch]$SkipTests,
    [switch]$OnlyImages,
    [switch]$SkipRestart,
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

$start = Get-Date

function Step([string]$msg) {
    Write-Host "`n==> $msg" -ForegroundColor Cyan
}
function Ok([string]$msg) {
    Write-Host "    $msg" -ForegroundColor Green
}
function Fail([string]$msg) {
    Write-Host "`n[ERROR] $msg" -ForegroundColor Red
    exit 1
}

# ── Lang-merge helpers ────────────────────────────────────────────────────────

function script:Find-SectionStart([string[]]$lines, [string]$section) {
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $l = $lines[$i]
        if ($l -eq "${section}:" -or $l.StartsWith("${section}: ") -or $l.StartsWith("${section}:`t")) { return $i }
    }
    return -1
}

function script:Find-SectionInsertPoint([string[]]$lines, [string]$section) {
    $start = Find-SectionStart $lines $section
    if ($start -eq -1) { return $lines.Count }
    $lastContent = $start
    for ($i = $start + 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Length -eq 0) { continue }
        $c = $lines[$i][0]
        if ($c -ne ' ' -and $c -ne "`t" -and $c -ne '#') { break }
        if ($c -eq ' ' -or $c -eq "`t") { $lastContent = $i }
    }
    return $lastContent + 1
}

function script:Get-SectionBlock([string[]]$defLines, [string]$section) {
    $sStart = Find-SectionStart $defLines $section
    if ($sStart -eq -1) { return "" }
    $blockStart = $sStart
    for ($i = $sStart - 1; $i -ge 0; $i--) {
        if ($defLines[$i] -match '^#') { $blockStart = $i } else { break }
    }
    $end = $defLines.Count
    for ($i = $sStart + 1; $i -lt $defLines.Count; $i++) {
        $l = $defLines[$i]
        if ($l.Length -gt 0 -and $l[0] -ne ' ' -and $l[0] -ne "`t" -and $l[0] -ne '#') { $end = $i; break }
    }
    return ($defLines[$blockStart..($end - 1)] -join "`n")
}

function script:Get-SubKeyBlock([string[]]$defLines, [string]$section, [string]$subKey) {
    $sStart = Find-SectionStart $defLines $section
    if ($sStart -eq -1) { return "" }
    $sEnd = $defLines.Count
    for ($i = $sStart + 1; $i -lt $defLines.Count; $i++) {
        $l = $defLines[$i]
        if ($l.Length -gt 0 -and $l[0] -ne ' ' -and $l[0] -ne "`t" -and $l[0] -ne '#') { $sEnd = $i; break }
    }
    $keyPrefix = "  ${subKey}:"
    $keyLine = -1
    for ($i = $sStart + 1; $i -lt $sEnd; $i++) {
        $l = $defLines[$i]
        if ($l -eq $keyPrefix -or $l.StartsWith("${keyPrefix} ") -or $l.StartsWith("${keyPrefix}`t")) { $keyLine = $i; break }
    }
    if ($keyLine -eq -1) { return "" }
    $blockStart = $keyLine
    for ($i = $keyLine - 1; $i -gt $sStart; $i--) {
        if ($defLines[$i] -match '^\s*#') { $blockStart = $i } else { break }
    }
    $blockEnd = $keyLine + 1
    for ($i = $keyLine + 1; $i -lt $sEnd; $i++) {
        $l = $defLines[$i]
        if ([string]::IsNullOrWhiteSpace($l)) { break }
        $indent = if ($l -match '^(\s+)') { $Matches[1].Length } else { 0 }
        if ($indent -le 2) { break }
        $blockEnd = $i + 1
    }
    return ($defLines[$blockStart..($blockEnd - 1)] -join "`n")
}

function script:Get-YamlLeafKeys([string[]]$lines) {
    $keys = [System.Collections.Generic.HashSet[string]]::new()
    $sections = [System.Collections.Generic.HashSet[string]]::new()
    $section = $null
    foreach ($line in $lines) {
        if ($line -match '^ {4,}\S[^:]*:\s*\S') {
            throw "Nested YAML leaf detected; LangMerge refuses to ignore it silently: $line"
        }
        if     ($line -match '^(\w[\w\-]*):\s*$')   {
            $section = $Matches[1]
            if (-not $sections.Add($section)) {
                throw "Duplicate YAML section detected; LangMerge refuses ambiguous translations: $section"
            }
        }
        elseif ($line -match '^(\w[\w\-]*):\s+.')   { $section = $null }
        elseif ($line -match '^  (\w[\w\-]*):\s' -and $null -ne $section) {
            $fullKey = "${section}.$($Matches[1])"
            if (-not $keys.Add($fullKey)) {
                throw "Duplicate YAML key detected; LangMerge refuses ambiguous translations: $fullKey"
            }
        }
    }
    return $keys
}

function script:Get-YamlSections([string[]]$lines) {
    $sections = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($line in $lines) {
        if ($line -match '^(\w[\w\-]*):\s*$') { [void]$sections.Add($Matches[1]) }
    }
    return $sections
}

function script:Merge-LangFile([string]$defaultText, [string]$diskPath) {
    $fname  = [System.IO.Path]::GetFileName($diskPath)
    $defRaw = $defaultText -replace "`r`n", "`n" -replace "`r", "`n"

    if (-not (Test-Path $diskPath)) {
        $null = New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($diskPath))
        [System.IO.File]::WriteAllText($diskPath, $defRaw, [System.Text.Encoding]::UTF8)
        Ok "[LangMerge] $fname seeded from JAR defaults."
        return
    }

    $diskRaw = (Get-Content $diskPath -Raw -Encoding UTF8) -replace "`r`n", "`n" -replace "`r", "`n"
    $defLines  = $defRaw  -split "`n"
    $diskLines = [System.Collections.Generic.List[string]]::new(($diskRaw -split "`n"))

    $defLeafs  = Get-YamlLeafKeys $defLines
    $diskLeafs = Get-YamlLeafKeys $diskLines
    $diskSecs  = Get-YamlSections $diskLines

    $missing = @($defLeafs | Where-Object { -not $diskLeafs.Contains($_) })
    if ($missing.Count -eq 0) { return }
    Write-Host "    [LangMerge] ${fname}: inserting $($missing.Count) missing key(s)." -ForegroundColor Yellow

    $bySection = [System.Collections.Specialized.OrderedDictionary]::new()
    foreach ($key in $missing) {
        $sec = $key.Substring(0, $key.IndexOf('.'))
        if (-not $bySection.Contains($sec)) { $bySection[$sec] = [System.Collections.Generic.List[string]]::new() }
        $bySection[$sec].Add($key)
    }

    $insertPairs = [System.Collections.Generic.List[object]]::new()
    foreach ($sec in $bySection.Keys) {
        if (-not $diskSecs.Contains($sec)) {
            $block = Get-SectionBlock $defLines $sec
            if (-not $block) { continue }
            $bLines = [System.Collections.Generic.List[string]]::new()
            if ($diskLines.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace($diskLines[$diskLines.Count - 1])) {
                $bLines.Add("")
            }
            $bLines.AddRange(($block -split "`n"))
            $insertPairs.Add([PSCustomObject]@{ At = $diskLines.Count; Lines = $bLines })
        } else {
            $insertAt = Find-SectionInsertPoint $diskLines.ToArray() $sec
            $bLines   = [System.Collections.Generic.List[string]]::new()
            foreach ($fullKey in $bySection[$sec]) {
                $subKey = $fullKey.Substring($fullKey.IndexOf('.') + 1)
                $text = Get-SubKeyBlock $defLines $sec $subKey
                if ($text) { $bLines.AddRange(($text -split "`n")) }
            }
            if ($bLines.Count -gt 0) {
                $insertPairs.Add([PSCustomObject]@{ At = $insertAt; Lines = $bLines })
            }
        }
    }

    foreach ($pair in @($insertPairs | Sort-Object At -Descending)) {
        $diskLines.InsertRange([Math]::Min($pair.At, $diskLines.Count), $pair.Lines)
    }

    [System.IO.File]::WriteAllText($diskPath, ($diskLines -join "`n"), [System.Text.Encoding]::UTF8)
    Ok "[LangMerge] ${fname} updated."
}

# ── 0. Prerequisites ──────────────────────────────────────────────────────────
if (-not $OnlyImages) {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Fail "mvn not found on PATH." }
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail "docker not found on PATH." }
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { Fail "Docker daemon is not available." }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { Fail "docker compose is not available." }
& docker compose config --quiet
if ($LASTEXITCODE -ne 0) { Fail "docker-compose.yml or .env is invalid." }

# ── 1. Maven build ────────────────────────────────────────────────────────────
if (-not $OnlyImages) {
    Step "Building plugins (Maven)..."
    $mvnArgs = @("clean", "package", "--batch-mode", "--no-transfer-progress", "-T", "1C")
    if ($SkipTests) { $mvnArgs += "-DskipTests" }
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { Fail "Maven build failed." }
}

# ── 2. Distribute JARs ────────────────────────────────────────────────────────
Step "Distributing verified JARs..."

$null = New-Item -ItemType Directory -Force dockerfiles\plugins\lobby, dockerfiles\plugins\sheepwars, dockerfiles\plugins\velocity

function script:Resolve-BuiltArtifact([string]$module) {
    $matches = @(Get-ChildItem "$module\target\$module-*-all.jar" -File -ErrorAction SilentlyContinue)
    if ($matches.Count -ne 1) {
        Fail "Expected exactly one shaded artifact for ${module}, found $($matches.Count). Run a clean Maven build."
    }
    return $matches[0].FullName
}

$artifacts = @(
    [pscustomobject]@{ Source = Resolve-BuiltArtifact "tropicube-core"; Destinations = @("dockerfiles\plugins\lobby\tropicube-core.jar", "dockerfiles\plugins\sheepwars\tropicube-core.jar"); Module = "tropicube-core"; InputModules = @("tropicube-core", "tropicube-docker-api") }
    [pscustomobject]@{ Source = Resolve-BuiltArtifact "tropicube-lobby"; Destinations = @("dockerfiles\plugins\lobby\tropicube-lobby.jar"); Module = "tropicube-lobby"; InputModules = @("tropicube-lobby", "tropicube-core", "tropicube-docker-api") }
    [pscustomobject]@{ Source = Resolve-BuiltArtifact "tropicube-sheepwars"; Destinations = @("dockerfiles\plugins\sheepwars\tropicube-sheepwars.jar"); Module = "tropicube-sheepwars"; InputModules = @("tropicube-sheepwars", "tropicube-core", "tropicube-docker-api") }
    [pscustomobject]@{ Source = Resolve-BuiltArtifact "tropicube-velocity"; Destinations = @("dockerfiles\plugins\velocity\tropicube-velocity.jar"); Module = "tropicube-velocity"; InputModules = @("tropicube-velocity", "tropicube-docker-api") }
)

foreach ($artifact in $artifacts) {
    if (-not (Test-Path $artifact.Source)) { Fail "Missing Maven artifact: $($artifact.Source)" }
    if ($OnlyImages) {
        $inputs = @()
        foreach ($inputModule in $artifact.InputModules) {
            $inputs += @(Get-ChildItem (Join-Path $inputModule "src") -Recurse -File -ErrorAction SilentlyContinue)
            $inputs += Get-Item (Join-Path $inputModule "pom.xml")
        }
        $inputs += Get-Item "pom.xml"
        $newestInput = $inputs | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($null -ne $newestInput -and $newestInput.LastWriteTimeUtc -gt (Get-Item $artifact.Source).LastWriteTimeUtc) {
            Fail "$($artifact.Source) is older than $($newestInput.FullName); run without -OnlyImages."
        }
    }
    foreach ($destination in $artifact.Destinations) {
        Copy-Item $artifact.Source $destination -Force
        if ((Get-FileHash $artifact.Source).Hash -ne (Get-FileHash $destination).Hash) {
            Fail "JAR verification failed after copy: $destination"
        }
        Ok $destination
    }
}

# ── 2b. Merge missing language keys into config ────────────────────────────
Step "Merging missing language keys..."

    # Remove stale double-nested languages\languages\ dir if present (deploy artifact)
    $staleDir = "dockerfiles\configs\TropicubeCore\languages\languages"
    if (Test-Path $staleDir) {
        Remove-Item $staleDir -Recurse -Force
        Ok "Removed stale $staleDir"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $coreZip = [System.IO.Compression.ZipFile]::OpenRead($artifacts[0].Source)
    try {
        foreach ($lang in @("fr", "en", "es", "de")) {
            $entry = $coreZip.GetEntry("languages/$lang.yml")
            if ($null -eq $entry) { continue }
            $reader      = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
            $defaultText = $reader.ReadToEnd()
            $reader.Dispose()
            Merge-LangFile $defaultText "dockerfiles\configs\TropicubeCore\languages\$lang.yml"
        }
    } finally {
        $coreZip.Dispose()
    }

    $velocityZip = [System.IO.Compression.ZipFile]::OpenRead($artifacts[3].Source)
    try {
        foreach ($lang in @("fr", "en", "es", "de")) {
            $entry = $velocityZip.GetEntry("languages/$lang.yml")
            if ($null -eq $entry) { continue }
            $reader      = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
            $defaultText = $reader.ReadToEnd()
            $reader.Dispose()
            Merge-LangFile $defaultText "dockerfiles\configs\TropicubeVelocity\languages\$lang.yml"
        }
    } finally {
        $velocityZip.Dispose()
    }

if ($ValidateOnly) {
    $elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    Write-Host "`n==> Validation complete in ${elapsed}s; no image or container was changed." -ForegroundColor Green
    exit 0
}

# ── 3. Rebuild Docker images (parallel) ──────────────────────────────────────
Step "Building Docker images in parallel (lobby / sheepwars / velocity)..."

$rootDir = $PWD.Path
$buildTag = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$dockerBuilds = @(
    [pscustomobject]@{ Name = "lobby";     File = "Dockerfile.lobby";     Tag = "tropicube-lobby:latest" }
    [pscustomobject]@{ Name = "sheepwars"; File = "Dockerfile.sheepwars"; Tag = "tropicube-sheepwars:latest" }
    [pscustomobject]@{ Name = "velocity";  File = "Dockerfile.velocity";  Tag = "tropicube-velocity:latest" }
)

$jobs = $dockerBuilds | ForEach-Object {
    $b = $_
    Start-Job -WorkingDirectory $rootDir -ScriptBlock {
        $tag = $using:b.Tag
        $dockerfile = $using:b.File
        $buildName = $using:b.Name
        $tagSuffix = $using:buildTag
        $versionedTag = $tag.Replace(":latest", ":$tagSuffix")
        $output = & docker build --pull -f "dockerfiles/$dockerfile" -t $tag -t $versionedTag . 2>&1
        [pscustomobject]@{
            Name     = $buildName
            Output   = ($output | Out-String)
            ExitCode = $LASTEXITCODE
        }
    }
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

$anyFailed = $false
foreach ($r in $results) {
    Write-Host "`n--- $($r.Name) ---" -ForegroundColor DarkGray
    Write-Host $r.Output
    if ($r.ExitCode -ne 0) {
        Write-Host "[ERROR] Docker build failed: $($r.Name)" -ForegroundColor Red
        $anyFailed = $true
    } else {
        Ok "Built tropicube-$($r.Name):latest"
    }
}
if ($anyFailed) { exit 1 }

if (-not $SkipRestart) {
    Step "Recreating the Velocity stack..."
    & docker compose up -d --force-recreate velocity
    if ($LASTEXITCODE -ne 0) { Fail "Docker Compose deployment failed." }
    Ok "Velocity recreated; new game containers will use the freshly tagged images."
}

# ── Done ──────────────────────────────────────────────────────────────────────
$elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
Write-Host "`n==> Deploy complete in ${elapsed}s." -ForegroundColor Green
Write-Host "    Lobby/SheepWars/Velocity: new containers will use tropicube-lobby:latest / tropicube-sheepwars:latest / tropicube-velocity:latest." -ForegroundColor DarkGray
Write-Host "    Rollback tag: $buildTag" -ForegroundColor DarkGray
