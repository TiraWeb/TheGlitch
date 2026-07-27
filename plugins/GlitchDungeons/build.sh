#!/bin/bash
# GlitchDungeons build + deploy script
# Builds locally, deploys to server via SCP + SSH

set -e

SERVER_DIR="/opt/theglitch"
PLUGIN_DIR="$SERVER_DIR/plugins"
REMOTE_HOST="tirob@129.154.195.94"
PLUGIN_NAME="GlitchDungeons-1.0-SNAPSHOT.jar"

echo "=== Building GlitchDungeons ==="
cd "$(dirname "$0")"

if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found. Install Maven first."
    exit 1
fi

mvn clean package -q

if [ $? -ne 0 ]; then
    echo "BUILD FAILED"
    exit 1
fi

echo "Build successful."
echo ""
echo "=== Deploying to $REMOTE_HOST ==="

scp "target/$PLUGIN_NAME" "$REMOTE_HOST:/tmp/"

ssh "$REMOTE_HOST" "
    systemctl stop theglitch
    rm -f $PLUGIN_DIR/GlitchDungeons*.jar
    cp /tmp/$PLUGIN_NAME $PLUGIN_DIR/
    rm /tmp/$PLUGIN_NAME
    systemctl start theglitch
    echo 'Deployed and restarted.'
"

echo "=== Done ==="
