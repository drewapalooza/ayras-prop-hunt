# Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause
# Close the developer RuneLite client before running this installer.
$ErrorActionPreference = 'Stop'
$dogProject = Split-Path -Parent $PSScriptRoot
$dogSource = Join-Path $dogProject 'ayras-doggies-1.2.0.jar'
$dogFolder = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
$dogDestination = Join-Path $dogFolder 'ayras-doggies-1.2.0.jar'
$dogPending = "$dogDestination.pending"

if (-not (Test-Path -LiteralPath $dogSource -PathType Leaf)) {
    throw 'Build the release first: .\gradlew.bat test release'
}
New-Item -ItemType Directory -Force -Path $dogFolder | Out-Null
Copy-Item -LiteralPath $dogSource -Destination $dogPending
if ((Get-FileHash -LiteralPath $dogSource -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath $dogPending -Algorithm SHA256).Hash) {
    throw 'The staged copy does not match the release JAR.'
}

# Stage first, then disable old versions. A locked JAR stops installation before
# the new version becomes active, so the next launch cannot load two versions.
Get-ChildItem -LiteralPath $dogFolder -File |
    Where-Object { $_.Name -match '^(dog-mode|ayras-doggies)-.*\.jar$' } |
    Where-Object { $_.Name -ne 'ayras-doggies-1.2.0.jar' } |
    ForEach-Object {
        $dogDisabledName = $_.Name + '.disabled'
        if (Test-Path -LiteralPath (Join-Path $dogFolder $dogDisabledName)) {
            $dogDisabledName += '.' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
        }
        try { Rename-Item -LiteralPath $_.FullName -NewName $dogDisabledName }
        catch { throw 'Close your developer RuneLite client, then run this installer again. The update remains staged.' }
    }
Move-Item -LiteralPath $dogPending -Destination $dogDestination -Force
Write-Output "Ayra's Doggies 1.2.0 installed. Restart your developer client and open the Ayra's Doggies settings cog."
