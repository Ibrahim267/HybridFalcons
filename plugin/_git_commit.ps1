$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\loq\Documents\hackathons\JetBrains\FitDeveloper'
Write-Output '=== add ==='
git add -A 2>&1 | Out-Null
git status -sb | Select-Object -First 3
Write-Output '=== commit ==='
git commit -F ..\_commit_msg_341.txt
Write-Output '=== push ==='
git push origin main 2>&1
Write-Output '=== post-push state ==='
git log --oneline -2
git status -sb
Write-Output '=== git done ==='
