param(
    [int]$Port = 8081,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$apiDirectory = Join-Path $PSScriptRoot "..\api"
$apiDirectory = (Resolve-Path $apiDirectory).Path
$healthUrl = "http://localhost:$Port/api/health"
$existingListener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($existingListener) {
    throw "Port $Port is already in use by PID $($existingListener.OwningProcess). Choose a free port; the script will not stop an existing service."
}

$logPath = Join-Path ([IO.Path]::GetTempPath()) ("khabar-api-cold-start-" + [guid]::NewGuid().ToString("N") + ".log")
$previousPort = $env:PORT
$previousLogLevel = $env:LOGGING_LEVEL_ROOT
$job = $null
$apiProcessId = $null
$watch = [Diagnostics.Stopwatch]::StartNew()

try {
    $job = Start-Job -ScriptBlock {
        param($WorkingDirectory, $ServerPort, $OutputLog)
        Set-Location $WorkingDirectory
        $env:PORT = "$ServerPort"
        $env:LOGGING_LEVEL_ROOT = "INFO"
        & .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=local" *>> $OutputLog
    } -ArgumentList @($apiDirectory, $Port, $logPath)

    $healthy = $false
    $health = $null
    while (-not $healthy -and $watch.Elapsed.TotalSeconds -lt $TimeoutSeconds -and $job.State -eq "Running") {
        try {
            $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
            $healthy = $health.status -eq "UP"
        }
        catch {
            Start-Sleep -Milliseconds 250
        }

    }

    $watch.Stop()
    if (-not $healthy) {
        if (Test-Path $logPath) {
            Select-String -Path $logPath -Pattern "Started KhabarApiApplication|APPLICATION FAILED TO START|BUILD FAILURE|Unknown lifecycle phase|not recognized" |
                Select-Object -Last 5 | ForEach-Object { $_.Line }
        }
        throw "The local API did not report UP within $TimeoutSeconds seconds (job state: $($job.State))."
    }

    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listener) {
        throw "The API returned UP but no listener was found on port $Port."
    }

    $apiProcessId = $listener.OwningProcess
    $startedLine = Select-String -Path $logPath -Pattern "Started KhabarApiApplication" | Select-Object -Last 1 | ForEach-Object { $_.Line.Trim() }
    [pscustomobject]@{
        Result = "UP"
        Port = $Port
        ColdStartToFirstHealthyResponseSeconds = [math]::Round($watch.Elapsed.TotalSeconds, 2)
        SpringStartedLine = $startedLine
        ProcessId = $apiProcessId
        Measurement = "Includes Maven wrapper startup and local Spring startup; excludes frontend, agents, external providers, and deployed hosting."
    } | Format-List
}
finally {
    if ($apiProcessId) {
        Stop-Process -Id $apiProcessId -ErrorAction SilentlyContinue
    }
    if ($job) {
        if ($job.State -eq "Running") {
            Stop-Job -Job $job -ErrorAction SilentlyContinue
        }
        Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
    }

    $env:PORT = $previousPort
    $env:LOGGING_LEVEL_ROOT = $previousLogLevel
    Remove-Item -LiteralPath $logPath -ErrorAction SilentlyContinue
}
