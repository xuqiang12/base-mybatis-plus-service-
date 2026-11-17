param(
    [string]$JavaExecutable = "java",
    [string]$ConfigFile = "system-service/src/main/resources/application-local.yml"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot "database-seed/target/database-seed-0.0.1-SNAPSHOT-exec.jar"
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "请先使用 JDK 21 运行 mvn -s .mvn/settings.xml verify 生成运行包"
}
if (-not [IO.Path]::IsPathRooted($ConfigFile)) { $ConfigFile = Join-Path $projectRoot $ConfigFile }
$configPath = (Resolve-Path -LiteralPath $ConfigFile).Path.Replace('\', '/')
Push-Location $projectRoot
try {
    & $JavaExecutable -jar $jarPath "--spring.config.additional-location=file:$configPath"
    if ($LASTEXITCODE -ne 0) { throw "Seed 执行失败，未完成的版本会回滚，请检查错误日志" }
} finally {
    Pop-Location
}
