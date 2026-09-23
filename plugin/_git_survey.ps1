$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\loq\Documents\hackathons\JetBrains\FitDeveloper'
Write-Output '=== branch / remote / last commits ==='
git log --oneline -3
git remote -v
Write-Output '=== status (short) ==='
git status -sb
Write-Output '=== diff stat vs HEAD ==='
git diff --stat HEAD | Select-Object -Last 5
Write-Output '=== untracked ==='
git ls-files --others --exclude-standard
Write-Output '=== survey done ==='
