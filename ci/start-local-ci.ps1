$ErrorActionPreference = 'Stop'

$CiRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $CiRoot
Set-Location $ProjectRoot

$EnvFile = Join-Path $CiRoot '.env.ci'
$ExampleFile = Join-Path $CiRoot '.env.ci.example'
if (-not (Test-Path $EnvFile)) {
    Copy-Item $ExampleFile $EnvFile
    throw "Created ci/.env.ci. Set JENKINS_ADMIN_PASSWORD and SONAR_TOKEN, then run this script again."
}

function Invoke-WithRetry([scriptblock] $Action, [string] $Name) {
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            & $Action
            if ($LASTEXITCODE -eq 0) { return }
        } catch {
            if ($attempt -eq 5) { throw }
        }
        Write-Host "$Name failed on attempt $attempt; retrying..."
        Start-Sleep -Seconds ([Math]::Min(30, $attempt * 5))
    }
    throw "$Name failed after retries"
}

Invoke-WithRetry { docker compose --env-file $EnvFile -f ci/docker-compose.ci.yml pull sonarqube } 'SonarQube image pull'
Invoke-WithRetry { docker compose --env-file $EnvFile -f ci/docker-compose.ci.yml build jenkins } 'Jenkins image build'
docker compose --env-file $EnvFile -f ci/docker-compose.ci.yml up -d sonarqube

$deadline = (Get-Date).AddMinutes(10)
do {
    Start-Sleep -Seconds 5
    try {
        $status = (Invoke-RestMethod http://127.0.0.1:9000/api/system/status -TimeoutSec 5).status
        Write-Host "SonarQube status: $status"
        if ($status -eq 'UP') { break }
    } catch {
        Write-Host 'SonarQube status: starting'
    }
} while ((Get-Date) -lt $deadline)

if ($status -ne 'UP') {
    docker logs --tail 100 omobio-sonarqube
    throw 'SonarQube did not become healthy within 10 minutes.'
}

docker compose --env-file $EnvFile -f ci/docker-compose.ci.yml up -d jenkins
Write-Host 'SonarQube: http://localhost:9000'
Write-Host 'Jenkins:   http://localhost:8080'
