# Extracts the meaningful failure lines from build_log.txt
$log = "C:\Users\loq\Documents\hackathons\JetBrains\CrunchGuard\plugin\build_log.txt"
$lines = Get-Content $log
$n = $lines.Count
Write-Output "TOTAL_LINES=$n"
$idx = @()
for ($i = 0; $i -lt $n; $i++) {
    if ($lines[$i] -match 'FAILURE: Build failed|What went wrong|Caused by:|^\s*> |error:|\.java:\d+') { $idx += $i }
}
foreach ($i in $idx) {
    Write-Output ("{0}: {1}" -f ($i+1), $lines[$i])
}
