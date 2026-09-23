# Starts everything needed to use the screens with live data, each in its own window:
#   1. agents service  -> http://localhost:8000
#   2. clinical API    -> http://localhost:8080  (`local` profile: in-memory database, demo people)
#   3. the screens     -> http://localhost:5500
# Then open http://localhost:5500/login.html and press any sign-in button.
# Close the windows to stop everything.
#
# -WithGraph also starts a throwaway Neo4j on bolt://localhost:7687 (no Docker needed) and points both
# services at it, so the patient graph is written and read. Everything in it is lost when it stops.
param([switch]$WithGraph)

$root = Split-Path -Parent $PSScriptRoot
$jdk = Join-Path $env:USERPROFILE ".jdks\jdk-21.0.12.1+1"

if (-not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    Write-Warning "Java 21 not found at $jdk. Set `$jdk in this script to your JDK 21 folder."
}

$graphEnv = ""
$waitForGraph = ""
if ($WithGraph) {
    $graphEnv = "`$env:NEO4J_URI = 'bolt://localhost:7687'; `$env:NEO4J_PASSWORD = 'local'; "
    # The API waits for Neo4j, so the demo patients are written to the graph when it seeds them.
    $waitForGraph = "while (-not (Test-NetConnection localhost -Port 7687 -InformationLevel Quiet -WarningAction SilentlyContinue)) { Write-Host 'Waiting for Neo4j...'; Start-Sleep 3 }; "
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME = '$jdk'; Set-Location '$root\services\api'; .\mvnw.cmd -q test-compile exec:java '-Dexec.mainClass=com.khabar.api.graph.LocalNeo4j' '-Dexec.classpathScope=test'"
}

Start-Process powershell -ArgumentList "-NoExit", "-Command", "$graphEnv Set-Location '$root\services\agents'; .\.venv\Scripts\python.exe -m uvicorn main:app --port 8000"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$graphEnv$waitForGraph`$env:JAVA_HOME = '$jdk'; Set-Location '$root\services\api'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root'; python -m http.server 5500 --directory docs"

Write-Host "Starting... the API takes about 30 seconds the first time."
Write-Host "Then open http://localhost:5500/login.html"
