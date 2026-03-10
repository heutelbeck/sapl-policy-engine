#!/bin/sh
set -e
if systemctl is-active --quiet sapl-node; then
    systemctl stop sapl-node
fi
systemctl disable sapl-node 2>/dev/null || true
