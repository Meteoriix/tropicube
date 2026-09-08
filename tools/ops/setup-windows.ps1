#requires -Version 7.0
<#
Installs a private, encrypted local-development backup repository for this checkout.
Re-running preserves the encryption key and existing snapshots. Timers are opt-in
so their first execution can follow a successful manual deployment and backup.
#>
param([switch]$InstallTasks)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$identifier = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($repositoryRoot.ToLowerInvariant()))).Substring(0, 12).ToLowerInvariant()
$privateRoot = Join-Path $env:LOCALAPPDATA "Tropicube/ops/$identifier"
$null = New-Item -ItemType Directory -Force -Path $privateRoot
# umask/chmod do not protect Windows exports: descendants inherit this explicit ACL.
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = [Security.AccessControl.DirectorySecurity]::new($privateRoot, [Security.AccessControl.AccessControlSections]::Access)
$acl.SetAccessRuleProtection($true, $false)
foreach ($rule in @($acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))) {
    $null = $acl.RemoveAccessRuleSpecific($rule)
}
foreach ($identity in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'), [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
[IO.FileSystemAclExtensions]::SetAccessControl([IO.DirectoryInfo]::new($privateRoot), $acl)
$toolsDirectory = Join-Path $privateRoot 'bin'
$stateDirectory = Join-Path $privateRoot 'state'
$null = New-Item -ItemType Directory -Force -Path $toolsDirectory, $stateDirectory
$restic = Join-Path $toolsDirectory 'restic.exe'
$archive = Join-Path $toolsDirectory 'restic_0.19.1_windows_amd64.zip'
# Official release archive, checked against its SHA256SUMS before execution.
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest 'https://github.com/restic/restic/releases/download/v0.19.1/restic_0.19.1_windows_amd64.zip' -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne 'da948ad707ed690426473aaba2046cd61f8f90f6f0e7dab6be0d5796531de67d') {
    throw 'Restic archive checksum mismatch.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $toolsDirectory -Force
Copy-Item -LiteralPath (Join-Path $toolsDirectory 'restic_0.19.1_windows_amd64.exe') -Destination $restic -Force
$passwordFile = Join-Path $privateRoot 'restic-password'
$backupRepository = Join-Path $privateRoot 'repository'
if (-not (Test-Path -LiteralPath $passwordFile)) {
    if (Test-Path -LiteralPath (Join-Path $backupRepository 'config')) { throw 'Existing repository has no password file; recover its original key.' }
    [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)))
}
$settingsDirectory = Join-Path $repositoryRoot '.runtime/windows'
$null = New-Item -ItemType Directory -Force -Path $settingsDirectory
$settingsFile = Join-Path $settingsDirectory 'settings.json'
if (-not (Test-Path -LiteralPath $settingsFile)) {
    @{
        RESTIC_REPOSITORY = "local:$backupRepository"
        RESTIC_PASSWORD_FILE = $passwordFile
        TROPICUBE_OPS_STATE = $stateDirectory
        TROPICUBE_BACKUP_MODE = 'local-development'
        toolsDirectory = $toolsDirectory
        python = (Get-Command python -CommandType Application | Select-Object -First 1).Source
        dockerDirectory = Split-Path (Get-Command docker -CommandType Application | Select-Object -First 1).Source
        gitDirectory = Split-Path (Get-Command git -CommandType Application | Select-Object -First 1).Source
    } | ConvertTo-Json | Set-Content -LiteralPath $settingsFile -Encoding utf8NoBOM
}
. (Join-Path $PSScriptRoot 'windows.ps1') -Command environment
if ($env:TROPICUBE_BACKUP_MODE -ne 'local-development' -or $env:RESTIC_REPOSITORY -ne "local:$backupRepository") {
    throw 'Existing backup settings differ; preserved without initializing a new repository.'
}
if (-not (Test-Path -LiteralPath (Join-Path $backupRepository 'config'))) {
    & $restic init --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Restic repository initialization failed.' }
}
& $restic snapshots --quiet
if ($LASTEXITCODE -ne 0) { throw 'Cannot open existing Restic repository.' }
if ($InstallTasks) {
    $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
    $pwsh = (Get-Command pwsh -CommandType Application | Select-Object -First 1).Source
    foreach ($command in @('backup', 'diagnose')) {
        $arguments = '-NoProfile -NonInteractive -WindowStyle Hidden -File "{0}" -Command {1} -Scheduled' -f (Join-Path $PSScriptRoot 'windows.ps1'), $command
        $action = New-ScheduledTaskAction -Execute $pwsh -Argument $arguments -WorkingDirectory $repositoryRoot
        $trigger = if ($command -eq 'backup') {
            New-ScheduledTaskTrigger -Daily -At '04:00'
        } else {
            New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1)
        }
        $limit = if ($command -eq 'backup') { New-TimeSpan -Hours 3 } else { New-TimeSpan -Minutes 2 }
        $taskSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit $limit
        $null = Register-ScheduledTask -TaskName "Tropicube-$identifier-$command" -Action $action -Trigger $trigger -Principal $principal -Settings $taskSettings -Force
        Write-Host "Scheduled task installed: Tropicube-$identifier-$command"
    }
}
Write-Host "Private backup configuration ready: $settingsFile"
Write-Host 'Local development only: keep an independent copy of the recovery key and repository off this PC.'
