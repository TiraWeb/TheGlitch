#!/usr/bin/env bash
#
# The Glitch — server launcher.
# Aikar's flags, tuned for Oracle Ampere A1 (2 OCPU / 12GB RAM):
# 8GB heap leaves ~4GB for JVM off-heap memory, Geyser's translation
# buffers, and the OS itself — the margin that prevents OOM kills.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"

HEAP="${HEAP:-8G}"

# Minecraft 26.x requires Java 25; pick it explicitly in case the distro
# 'java' alternative points at an older JDK.
JAVA_BIN="java"
for candidate in /usr/lib/jvm/java-25-openjdk-*/bin/java; do
  if [[ -x "${candidate}" ]]; then
    JAVA_BIN="${candidate}"
    break
  fi
done

exec "${JAVA_BIN}" -Xms"${HEAP}" -Xmx"${HEAP}" \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar purpur.jar --nogui
