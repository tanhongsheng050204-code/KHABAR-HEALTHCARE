# Starts everything needed to use the screens with live data, each in its own window:
#   1. agents service  -> http://localhost:8000
#   2. clinical API    -> http://localhost:8080  (`local` profile: in-memory database, demo people)
#   3. the screens     -> http://localhost:5500
# Then open http://localhost:5500/login.html and press any sign-in button.
# Close the three windows to stop everything.

$root = Split-Path -Parent $PSScriptRoot
$jdk = Join-Path $env:USERPROFILE ".jdks\jdk-21.0.12.1+1"

if (-not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    Write-Warning "Java 21 not found at $jdk. Set `$jdk in this script to your JDK 21 folder."
}

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\services\agents'; .\.venv\Scripts\python.exe -m uvicorn main:app --port 8000"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME = '$jdk'; Set-Location '$root\services\api'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root'; python -m http.server 5500 --directory docs"

Write-Host "Starting... the API takes about 30 seconds the first time."
Write-Host "Then open http://localhost:5500/login.html"
