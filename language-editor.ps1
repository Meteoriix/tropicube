param(
    [ValidateSet('start', 'stop', 'status', 'foreground')]
    [string]$Action = 'start',
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontend = Join-Path $repoRoot 'tools/language-editor/frontend'
$backend = Join-Path $repoRoot 'tools/language-editor/backend'
$runtime = Join-Path $repoRoot 'tools/language-editor/.runtime'
$pidFile = Join-Path $runtime 'editor.pid'
$outputLog = Join-Path $runtime 'editor.log'
$errorLog = Join-Path $runtime 'editor-error.log'
$jar = Join-Path $backend 'target/tropicube-language-editor.jar'
$port = if ($env:TROPICUBE_LANGUAGE_EDITOR_PORT) { $env:TROPICUBE_LANGUAGE_EDITOR_PORT } else { '8765' }

if ($port -notmatch '^\d+$' -or [int]$port -lt 1 -or [int]$port -gt 65535) {
    throw "TROPICUBE_LANGUAGE_EDITOR_PORT doit être un port compris entre 1 et 65535."
}
$address = "http://127.0.0.1:$port"

function Get-EditorProcess {
    if (-not (Test-Path -LiteralPath $pidFile)) { return $null }
    $savedPid = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    if ($savedPid -notmatch '^\d+$') { return $null }
    $process = Get-Process -Id ([int]$savedPid) -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $savedPid" -ErrorAction SilentlyContinue
    if (-not $details.CommandLine -or $details.CommandLine -notlike '*tropicube-language-editor.jar*') {
        return $null
    }
    return $process
}

function Remove-StalePidFile {
    if ((Test-Path -LiteralPath $pidFile) -and -not (Get-EditorProcess)) {
        Remove-Item -LiteralPath $pidFile -Force
    }
}

function Build-Editor {
    if (-not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules'))) {
        npm --prefix $frontend ci
        if ($LASTEXITCODE -ne 0) { throw 'Échec de npm ci.' }
    }
    npm --prefix $frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Échec de la compilation du frontend.' }
    & (Join-Path $repoRoot 'mvnw.cmd') -q -f (Join-Path $backend 'pom.xml') package
    if ($LASTEXITCODE -ne 0) { throw 'Échec de la compilation du backend.' }
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null
Remove-StalePidFile

switch ($Action) {
    'status' {
        $process = Get-EditorProcess
        if ($process) {
            Write-Host "Éditeur de langues actif (PID $($process.Id)) : $address"
        } else {
            Write-Host 'Éditeur de langues arrêté.'
        }
        exit 0
    }
    'stop' {
        $process = Get-EditorProcess
        if (-not $process) {
            Write-Host 'Éditeur de langues déjà arrêté.'
            exit 0
        }
        Stop-Process -Id $process.Id
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) { break }
            Start-Sleep -Milliseconds 250
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        Write-Host 'Éditeur de langues arrêté.'
        exit 0
    }
    'foreground' {
        if (Get-EditorProcess) { throw "L'éditeur de langues est déjà actif : $address" }
        Build-Editor
        & java -jar $jar
        exit $LASTEXITCODE
    }
    'start' {
        $existing = Get-EditorProcess
        if ($existing) {
            Write-Host "Éditeur de langues déjà actif (PID $($existing.Id)) : $address"
            if (-not $NoBrowser) { Start-Process $address }
            exit 0
        }

        Build-Editor
        $javaCommand = Get-Command javaw -ErrorAction SilentlyContinue
        if (-not $javaCommand) { $javaCommand = Get-Command java -ErrorAction Stop }
        $process = Start-Process -FilePath $javaCommand.Source `
            -ArgumentList @('-jar', ('"{0}"' -f $jar), '--no-browser') `
            -WorkingDirectory $repoRoot `
            -RedirectStandardOutput $outputLog `
            -RedirectStandardError $errorLog `
            -WindowStyle Hidden `
            -PassThru
        Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii

        $ready = $false
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            if ($process.HasExited) { break }
            try {
                Invoke-WebRequest -Uri "$address/api/state" -UseBasicParsing -TimeoutSec 1 | Out-Null
                $ready = $true
                break
            } catch {
                Start-Sleep -Milliseconds 250
            }
        }
        if (-not $ready) {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue }
            Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
            $details = if (Test-Path -LiteralPath $errorLog) { (Get-Content -LiteralPath $errorLog -Raw).Trim() } else { '' }
            throw "L'éditeur n'a pas démarré sur $address. Consultez $errorLog. $details"
        }

        if (-not $NoBrowser) { Start-Process $address }
        Write-Host "Éditeur de langues démarré en arrière-plan (PID $($process.Id)) : $address"
        Write-Host "Arrêt : .\language-editor.ps1 stop"
    }
}
