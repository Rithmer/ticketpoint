$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot/.."
& docker compose --env-file .env up -d --wait @args
exit $LASTEXITCODE
