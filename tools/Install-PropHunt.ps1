# Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause
# Close the developer RuneLite client before running this installer.
$ErrorActionPreference = 'Stop'
$propProject = Split-Path -Parent $PSScriptRoot
$propSource = Join-Path $propProject 'ayras-prop-hunt-2.0.0.jar'
$propFolder = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
$propDestination = Join-Path $propFolder 'ayras-prop-hunt-2.0.0.jar'
$propPending = "$propDestination.pending"

if (-not (Test-Path -LiteralPath $propSource -PathType Leaf)) {
    throw 'Build the release first: .\gradlew.bat test release'
}
New-Item -ItemType Directory -Force -Path $propFolder | Out-Null
Copy-Item -LiteralPath $propSource -Destination $propPending
if ((Get-FileHash -LiteralPath $propSource -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath $propPending -Algorithm SHA256).Hash) {
    throw 'The staged copy does not match the release JAR.'
}

# Stage first, then disable old versions. A locked JAR stops installation before
# the new version becomes active, so the next launch cannot load two versions.
Get-ChildItem -LiteralPath $propFolder -File |
    Where-Object { $_.Name -match '^(dog-mode|ayras-doggies|ayras-prop-hunt)-.*\.jar$' } |
    Where-Object { $_.Name -ne 'ayras-prop-hunt-2.0.0.jar' } |
    ForEach-Object {
        $propDisabledName = $_.Name + '.disabled'
        if (Test-Path -LiteralPath (Join-Path $propFolder $propDisabledName)) {
            $propDisabledName += '.' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
        }
        try { Rename-Item -LiteralPath $_.FullName -NewName $propDisabledName }
        catch { throw 'Close your developer RuneLite client, then run this installer again. The update remains staged.' }
    }
Move-Item -LiteralPath $propPending -Destination $propDestination -Force
Write-Output "Ayra's Prop Hunt 2.0.0 installed. Restart your developer client and open the box icon in the sidebar."
