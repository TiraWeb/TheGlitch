#!/usr/bin/env bash
#
# Build CoreProtect CE from the official source and install it on the live server.
#
# Why: the free CoreProtect-CE-24.0 release refuses to start on Minecraft 26.2
# ("Minecraft 26.2 is not supported"); PlayPro/CoreProtect master already has
# the Bukkit_v26_2 adapter. Source builds must set project.branch=development
# or the plugin refuses to start. Swap back to an official release jar once
# one lists 26.2.
#
# Usage: sudo ./scripts/build-coreprotect.sh   (then restart theglitch)
set -euo pipefail

WORK=/tmp/CoreProtect
LIVE=/opt/theglitch/server/plugins/CoreProtect.jar

rm -rf "$WORK"
git clone -q --depth 1 https://github.com/PlayPro/CoreProtect.git "$WORK"
echo "Building CoreProtect @ $(git -C "$WORK" rev-parse --short HEAD)"
mvn -q -B -f "$WORK/pom.xml" clean package -DskipTests -Dproject.branch=development
JAR=$(ls "$WORK"/target/CoreProtect-*.jar | grep -v original | head -1)
install -o minecraft -g minecraft -m 644 "$JAR" "$LIVE"
echo "Installed $JAR -> $LIVE (restart the server to load it)"
