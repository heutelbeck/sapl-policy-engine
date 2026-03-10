#!/bin/sh
set -e
if ! getent group sapl-node >/dev/null 2>&1; then
    groupadd --system sapl-node
fi
if ! getent passwd sapl-node >/dev/null 2>&1; then
    useradd --system --gid sapl-node --home-dir /var/lib/sapl-node --shell /usr/sbin/nologin --no-create-home sapl-node
fi
chown -R sapl-node:sapl-node /var/lib/sapl-node
systemctl daemon-reload
