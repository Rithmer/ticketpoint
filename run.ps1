$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!(Test-Path '.env')) { throw 'Создайте .env из .env.example и задайте POSTGRES_PASSWORD.' }
foreach ($line in Get-Content '.env' -Encoding UTF8) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Некорректная строка в .env.' }
    $name = $Matches[1]
    $value = $Matches[2].Trim().Trim('"').Trim("'")
    $value = [regex]::Replace($value, '\$\{([A-Z][A-Z0-9_]*)\}', {
        param($match)
        [Environment]::GetEnvironmentVariable($match.Groups[1].Value, 'Process')
    })
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
& .\mvnw.cmd javafx:run @args
exit $LASTEXITCODE
