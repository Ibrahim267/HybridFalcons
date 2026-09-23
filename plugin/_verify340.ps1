$ErrorActionPreference = 'Continue'
function Check($name, $ok) { Write-Output ("{0} = {1}" -f $name, $(if ($ok) { 'PASS' } else { 'FAIL' })) }

# --- dashboard page (IC relay 8792) ---
$idx = (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8790/').Content
Check 'index: preset buttons REMOVED (no t-preset)'      ($idx -notmatch 't-preset')
Check 'index: time presets REMOVED (no data-min)'        ($idx -notmatch 'data-min')
Check 'index: settings-only note present'                ($idx -match 'Settings \| Tools \| FitDeveloper')
Check 'index: settings-only note div'                    ($idx -match 'cfg-note')
Check 'index: no browser target in POST (triggerBreak)'  ($idx -notmatch 'target:\s*breakTarget')
Check 'index: FD bridge names (__FD_PLUGIN__)'           ($idx -match '__FD_PLUGIN__')
Check 'index: no CG leftovers (__CG_PLUGIN__)'           ($idx -notmatch '__CG_PLUGIN__')
Check 'index: no cgEngine leftovers'                     ($idx -notmatch '__cgEngine')
Check 'index: bright steps counter color eaf0f6'         ($idx -match 'pbar-label b\{color:#eaf0f6')
Check 'index: status bar badge 3.4.0'                    ($idx -match 'v3\.4\.0')

# --- walker page ---
$wk = (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8790/walk').Content
Check 'walk: footer v3.4.0'                              ($wk -match 'v3\.4\.0')
Check 'walk: no Walk Again button'                       ($wk -notmatch 'againBtn')
Check 'walk: no +1 step button'                          ($wk -notmatch 'tapBtn')

# --- ide-activity: settings-only target + breakScreen field ---
$act = (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8790/api/ide-activity').Content
Write-Output ("ide-activity: " + $act)
Check 'activity: breakScreen field present'              ($act -match '"breakScreen":"(both|plugin|browser)"')
Check 'activity: targetSteps present'                    ($act -match '"targetSteps":\d+')

# --- POST /api/session test intentionally SKIPPED here: it would open a real
# --- break and lock the user's IDE; the settings-target path is enforced in
# --- FitDeveloperServer dispatch (target = FitDeveloperSettings.targetSteps())
