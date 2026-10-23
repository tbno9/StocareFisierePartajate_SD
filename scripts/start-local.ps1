# Varianta Windows (PowerShell): deschide cate o fereastra pentru fiecare nod.
# Rulare din radacina proiectului:  powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
Set-Location (Join-Path $PSScriptRoot "..")
mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$cp = "target/classes"
$main = "ro.ubm.sd.node.StorageNode"

Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -cp $cp $main --id n1 --port 7001 --dir data/n1 --coordinator"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -cp $cp $main --id n2 --port 7002 --dir data/n2"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -cp $cp $main --id n3 --port 7003 --dir data/n3"

Write-Host "Client: java -cp $cp ro.ubm.sd.client.Client --coordinator localhost:7001"