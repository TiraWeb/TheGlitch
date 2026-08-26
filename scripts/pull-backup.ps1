# The Glitch — pull latest worlds+data backups from host to C:\opencode\MCproject\backups
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
$scp = Get-Command scp -ErrorAction SilentlyContinue
if (-not $scp) {
  $candidates = @(
    "C:\Program Files\Git\usr\bin\scp.exe",
    "C:\Program Files (x86)\Git\usr\bin\scp.exe",
    "C:\Windows\System32\OpenSSH\scp.exe"
  )
  foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { $scp = Get-Item -LiteralPath $c; break } }
}
if (-not $scp) { Write-Error "scp not found — install Git for Windows or OpenSSH"; }

Write-Host "[glitch-backup] Listing remote backups at ${User}@${HostAddr}:${RemoteDir}/"
$ssh = (Get-Command ssh -ErrorAction SilentlyContinue)
if (-not $ssh) {
  $sshCandidates = @("C:\Program Files\Git\usr\bin\ssh.exe","C:\Windows\System32\OpenSSH\ssh.exe")
  foreach ($c in $sshCandidates) { if (Test-Path -LiteralPath $c) { $ssh = Get-Item -LiteralPath $c; break } }
}
$remoteList = & $ssh.Source -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo ls -lh ${RemoteDir}/theglitch-worlds+data-*.tar.gz 2>&1 | tail -n 20; echo '---'; sudo ls -lh ${RemoteDir}/ 2>&1 | head -n 20"
Write-Host $remoteList

Write-Host "[glitch-backup] Pulling any missing archives to $LocalDir ..."
# Use scp with wildcard - need to expand remotely via ssh + tar? Simplest: list files then scp each
$files = & $ssh.Source -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo bash -c 'ls -1 ${RemoteDir}/theglitch-worlds+data-*.tar.gz 2>/dev/null | head -n 20'"
if (-not $files) {
  Write-Host "[glitch-backup] No remote backups found yet — run backup-now.sh on host first."
  exit 0
}
$files = $files -split "`n" | Where-Object { $_.Trim() -ne "" }
foreach ($remote in $files) {
  $remote = $remote.Trim()
  $name = Split-Path -Leaf $remote
  $local = Join-Path $LocalDir $name
  $localSha = "$local.sha256"
  $remoteSha = "${remote}.sha256"
  if (Test-Path -LiteralPath $local) {
    Write-Host "[glitch-backup] Skip existing $name"
  } else {
    Write-Host "[glitch-backup] Fetching $name ..."
    # scp needs sudo-readable file — copy to /tmp with correct perms first, then scp from /tmp
    $tmpRemote = "/tmp/$name"
    & $ssh.Source -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo cp `"$remote`" `"$tmpRemote`" && sudo chmod 644 `"$tmpRemote`" && sudo cp `"${remote}.sha256`" `"$tmpRemote.sha256`" 2>/dev/null; sudo chmod 644 `"$tmpRemote.sha256`" 2>/dev/null; echo ok"
    $scpArgs = @("-i", $KeyPath, "-o", "StrictHostKeyChecking=no", "${User}@${HostAddr}:`"$tmpRemote`"", "`"$local`"")
    & $scp.Source @scpArgs
    $scpShaArgs = @("-i", $KeyPath, "-o", "StrictHostKeyChecking=no", "${User}@${HostAddr}:`"$tmpRemote.sha256`"", "`"$localSha`"")
    & $scp.Source @scpShaArgs 2>$null
    # cleanup tmp on host
    & $ssh.Source -i $KeyPath -o StrictHostKeyChecking=no "${User}@${HostAddr}" "sudo rm -f `"$tmpRemote`" `"$tmpRemote.sha256`""
    if (Test-Path -LiteralPath $local) {
      $size = (Get-Item -LiteralPath $local).Length / 1MB
      Write-Host "[glitch-backup] Saved $name ($([math]::Round($size,1)) MB)"
      if (Test-Path -LiteralPath $localSha) {
        $expected = (Get-Content -LiteralPath $localSha -Raw).Split()[0]
        $actual = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLower()
        if ($expected -and $expected.ToLower() -eq $actual) {
          Write-Host "[glitch-backup] SHA256 OK $name"
        } else {
          Write-Host "[glitch-backup] SHA256 mismatch for $name (expected $expected got $actual) — still saved, verify manually"
        }
      }
    }
  }
}

Write-Host "[glitch-backup] Local backups:"
Get-ChildItem -LiteralPath $LocalDir -Filter "theglitch-worlds+data-*.tar.gz" | Sort-Object LastWriteTime -Descending | Select-Object Name, @{N="MB";E={[math]::Round($_.Length/1MB,1)}}, LastWriteTime | Format-Table | Out-String | Write-Host
Write-Host "[glitch-backup] Done. To restore on a fresh box: tar -xzpf <archive> -C /opt/theglitch && chown -R minecraft:minecraft /opt/theglitch/server && systemctl restart theglitch"
