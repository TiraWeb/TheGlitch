# The Glitch -- pull latest worlds+data backups from host to C:\opencode\MCproject\backups
# Host retains every-2-days backups at /opt/theglitch/backups (PC may be offline, so host is source of truth)
# This script pulls via scp (try2.key) - safe to run anytime, skips existing files
param(
  [string]$HostAddr = "217.142.189.253",
  [string]$User = "ubuntu",
  [string]$KeyPath = "C:\opencode\MCproject\try2.key",
  [string]$RemoteDir = "/opt/theglitch/backups",
  [string]$LocalDir = "C:\opencode\MCproject\backups"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $LocalDir)) {
  New-Item -ItemType Directory -Force -Path $LocalDir | Out-Null
}
if (-not (Test-Path -LiteralPath $KeyPath)) {
  Write-Error "Key not found: $KeyPath"
}

# Ensure scp is available (Git for Windows ships it) - try common paths
# Store a plain path string: FileInfo from Get-Item has no .Source (null),
# so use .FullName on Get-Item results.
$scp = (Get-Command scp -ErrorAction SilentlyContinue).Source
if (-not $scp) {
  $candidates = @(
    "C:\Program Files\Git\usr\bin\scp.exe",
    "C:\Program Files (x86)\Git\usr\bin\scp.exe",
    "C:\Windows\System32\OpenSSH\scp.exe"
  )
  foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { $scp = (Get-Item -LiteralPath $c).FullName; break } }
}
if (-not $scp) { Write-Error "scp not found -- install Git for Windows or OpenSSH" }

Write-Host "[glitch-backup] Listing remote backups at ${User}@${HostAddr}:${RemoteDir}/"
$ssh = (Get-Command ssh -ErrorAction SilentlyContinue).Source
if (-not $ssh) {
  $sshCandidates = @("C:\Program Files\Git\usr\bin\ssh.exe","C:\Windows\System32\OpenSSH\ssh.exe")
  foreach ($c in $sshCandidates) { if (Test-Path -LiteralPath $c) { $ssh = (Get-Item -LiteralPath $c).FullName; break } }
}
$remoteList = & $ssh -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo ls -lh ${RemoteDir}/theglitch-worlds+data-*.tar.gz 2>&1 | tail -n 20; echo ---; sudo ls -lh ${RemoteDir}/ 2>&1 | head -n 20"
Write-Host $remoteList

Write-Host "[glitch-backup] Pulling any missing archives to $LocalDir ..."
$files = & $ssh -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo bash -c 'ls -1 ${RemoteDir}/theglitch-worlds+data-*.tar.gz 2>/dev/null | head -n 20'"
if ($LASTEXITCODE -ne 0) {
  Write-Error "ssh listing failed (exit $LASTEXITCODE) -- transport error (network down / host unreachable), NOT an empty backup dir."
  exit 1
}
if (-not $files) {
  Write-Host "[glitch-backup] No remote backups found yet -- run backup-now.sh on host first."
  exit 0
}
$files = $files -split "`n" | Where-Object { $_.Trim() -ne "" }
$verified = @{}
$failures = $false
foreach ($remote in $files) {
  $remote = $remote.Trim()
  $name = Split-Path -Leaf $remote
  $local = Join-Path $LocalDir $name
  $localSha = "$local.sha256"

  # Skip-fast path: already verified OK earlier in this run
  if ($verified.ContainsKey($name)) {
    Write-Host "[glitch-backup] Skip (verified OK this run) $name"
    continue
  }

  # Re-verify pre-existing local archives every run: a truncated interrupted
  # download must never be accepted forever.
  if (Test-Path -LiteralPath $local) {
    if (Test-Path -LiteralPath $localSha) {
      $expected = (Get-Content -LiteralPath $localSha -Raw).Split()[0]
      $actual = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLower()
      if ($expected -and $expected.ToLower() -eq $actual) {
        Write-Host "[glitch-backup] SHA256 OK (existing) $name"
        $verified[$name] = $true
        continue
      }
      Write-Warning "[glitch-backup] SHA256 mismatch for existing $name (expected $expected got $actual) -- deleting both copies so the next run re-fetches"
      Remove-Item -LiteralPath $local -Force
      Remove-Item -LiteralPath $localSha -Force -ErrorAction SilentlyContinue
    } else {
      Write-Warning "[glitch-backup] Existing $name has no .sha256 sidecar -- UNVERIFIED, deleting so the next run re-fetches"
      Remove-Item -LiteralPath $local -Force
    }
  }

  Write-Host "[glitch-backup] Fetching $name ..."
  $tmpRemote = "/tmp/$name"
  & $ssh -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo cp `"$remote`" `"$tmpRemote`" && sudo chmod 644 `"$tmpRemote`" && sudo cp `"${remote}.sha256`" `"$tmpRemote.sha256`" 2>/dev/null; sudo chmod 644 `"$tmpRemote.sha256`" 2>/dev/null; echo ok"
  $scpArgs = @("-i", $KeyPath, "-o", "StrictHostKeyChecking=no", "${User}@${HostAddr}:`"$tmpRemote`"", "`"$local`"")
  & $scp @scpArgs
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "[glitch-backup] scp FAILED for $name (exit $LASTEXITCODE) -- deleting partial local file"
    if (Test-Path -LiteralPath $local) { Remove-Item -LiteralPath $local -Force }
    & $ssh -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo rm -f `"$tmpRemote`" `"$tmpRemote.sha256`""
    $failures = $true
    continue
  }
  $scpShaArgs = @("-i", $KeyPath, "-o", "StrictHostKeyChecking=no", "${User}@${HostAddr}:`"$tmpRemote.sha256`"", "`"$localSha`"")
  & $scp @scpShaArgs 2>$null
  $shaCopied = ($LASTEXITCODE -eq 0) -and (Test-Path -LiteralPath $localSha)
  & $ssh -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo rm -f `"$tmpRemote`" `"$tmpRemote.sha256`""
  if (Test-Path -LiteralPath $local) {
    $size = (Get-Item -LiteralPath $local).Length / 1MB
    Write-Host "[glitch-backup] Saved $name ($([math]::Round($size,1)) MB)"
  }
  if (-not $shaCopied) {
    Write-Warning "[glitch-backup] No .sha256 sidecar fetched for $name -- archive is UNVERIFIED, deleting it so the next run re-fetches"
    if (Test-Path -LiteralPath $local) { Remove-Item -LiteralPath $local -Force }
    if (Test-Path -LiteralPath $localSha) { Remove-Item -LiteralPath $localSha -Force }
    $failures = $true
    continue
  }
  $expected = (Get-Content -LiteralPath $localSha -Raw).Split()[0]
  $actual = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLower()
  if ($expected -and $expected.ToLower() -eq $actual) {
    Write-Host "[glitch-backup] SHA256 OK $name"
    $verified[$name] = $true
  } else {
    Write-Warning "[glitch-backup] SHA256 mismatch for $name (expected $expected got $actual) -- deleting both local copies so the next run re-fetches"
    Remove-Item -LiteralPath $local -Force
    Remove-Item -LiteralPath $localSha -Force
    $failures = $true
  }
}
if ($failures) { Write-Error "One or more archives failed to fetch/verify cleanly."; exit 1 }

Write-Host "[glitch-backup] Local backups:"
Get-ChildItem -LiteralPath $LocalDir -Filter "theglitch-worlds+data-*.tar.gz" | Sort-Object LastWriteTime -Descending | Select-Object Name, @{N="MB";E={[math]::Round($_.Length/1MB,1)}}, LastWriteTime | Format-Table | Out-String | Write-Host
Write-Host '[glitch-backup] Done. To restore on a fresh box: tar -xzpf <archive> -C /opt/theglitch && chown -R minecraft:minecraft /opt/theglitch/server && systemctl restart theglitch'
