$ErrorActionPreference = 'Continue'
Copy-Item 'C:/Users/loq/Documents/hackathons/JetBrains/CrunchGuard/plugin/build/distributions/fitdeveloper-plugin-2.4.1.zip' 'C:/Users/loq/Desktop/fitdeveloper-plugin-2.4.1.zip' -Force
Remove-Item 'C:/Users/loq/Desktop/fitdeveloper-plugin-2.3.0.zip' -Force -ErrorAction SilentlyContinue
Write-Output 'desktop now:'
Get-ChildItem 'C:/Users/loq/Desktop' -Filter 'fitdeveloper*' | ForEach-Object { '  ' + $_.Name + '  ' + $_.Length + 'B' }
