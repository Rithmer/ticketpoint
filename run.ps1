$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& .\mvnw.cmd -pl javafx-client -am javafx:run @args
exit $LASTEXITCODE
